/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.{Cause, FiberRef, LogLevel, Supervisor}

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.{Collections, Map => JMap, Set => JSet, WeakHashMap}
import scala.concurrent.ExecutionContext

private[internal] trait PlatformSpecific {

  /**
   * Adds a shutdown hook that executes the specified action on shutdown.
   */
  def addShutdownHook(action: () => Unit): Unit =
    java.lang.Runtime.getRuntime.addShutdownHook {
      new Thread {
        override def run() = action()
      }
    }

  /**
   * A Runtime with settings suitable for benchmarks, specifically with Tracing
   * and auto-yielding disabled.
   *
   * Tracing adds a constant ~2x overhead on FlatMaps, however, it's an
   * optional feature and it's not valid to compare the performance of ZIO with
   * enabled Tracing with effect types _without_ a comparable feature.
   */
  lazy val benchmark: Platform = makeDefault(Int.MaxValue).withReportFailure(_ => ()).withTracing(Tracing.disabled)

  /**
   * The default platform, configured with settings designed to work well for
   * mainstream usage. Advanced users should consider making their own platform
   * customized for specific application requirements.
   */
  lazy val default: Platform = makeDefault()

  /**
   * The default number of operations the ZIO runtime should execute before
   * yielding to other fibers.
   */
  final val defaultYieldOpCount = 2048

  /**
   * Returns the name of the thread group to which this thread belongs. This
   * is a side-effecting method.
   */
  final def getCurrentThreadGroup: String =
    Thread.currentThread.getThreadGroup.getName

  /**
   * A `Platform` created from Scala's global execution context.
   */
  lazy val global: Platform = fromExecutionContext(ExecutionContext.global)

  /**
   * Creates a platform from an `Executor`.
   */
  final def fromExecutor(executor0: Executor): Platform =
    new Platform {
      val blockingExecutor = Blocking.blockingExecutor

      val executor = executor0

      def fatal(t: Throwable): Boolean =
        t.isInstanceOf[VirtualMachineError]

      // FIXME: Make this nice
      def log(level: LogLevel, message: () => String, context: Map[FiberRef.Runtime[_], AnyRef], regions: List[String]): Unit = 
        println(message())

      def reportFailure(cause: Cause[Any]): Unit =
        if (cause.died)
          System.err.println(cause.prettyPrint)

      def reportFatal(t: Throwable): Nothing = {
        t.printStackTrace()
        try {
          System.exit(-1)
          throw t
        } catch { case _: Throwable => throw t }
      }

      val supervisor = Supervisor.none

      val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

    }

  /**
   * Creates a Platform from an execution context.
   */
  final def fromExecutionContext(ec: ExecutionContext): Platform =
    fromExecutor(Executor.fromExecutionContext(defaultYieldOpCount)(ec))

  /**
   * Returns whether the current platform is ScalaJS.
   */
  val isJS = false

  /**
   * Returns whether the currently platform is the JVM.
   */
  val isJVM = true

  /**
   * Returns whether the currently platform is Scala Native.
   */
  val isNative = false

  /**
   * Makes a new default platform. This is a side-effecting method.
   */
  def makeDefault(yieldOpCount: Int = defaultYieldOpCount): Platform =
    fromExecutor(Executor.makeDefault(yieldOpCount))

  final def newWeakHashMap[A, B](): JMap[A, B] =
    Collections.synchronizedMap(new WeakHashMap[A, B]())

  final def newConcurrentWeakSet[A](): JSet[A] =
    Collections.synchronizedSet(newWeakSet[A]())

  final def newWeakSet[A](): JSet[A] =
    Collections.newSetFromMap(new WeakHashMap[A, java.lang.Boolean]())

  final def newConcurrentSet[A](): JSet[A] = ConcurrentHashMap.newKeySet[A]()

  final def newWeakReference[A](value: A): () => A = {
    val ref = new WeakReference[A](value)

    () => ref.get()
  }

  /**
   * calling `initCause()` on [[java.lang.Throwable]] may fail on the JVM if `newCause != this`,
   * which may happen if the cause is set to null.
   * This works around this with reflection.
   */
  def forceThrowableCause(throwable: Throwable, newCause: Throwable): Unit = {
    import scala.util.control.Exception._
    ignoring(classOf[Throwable]) {
      val causeField = classOf[Throwable].getDeclaredField("cause")
      causeField.setAccessible(true)
      causeField.set(throwable, newCause)
    }
  }
}
