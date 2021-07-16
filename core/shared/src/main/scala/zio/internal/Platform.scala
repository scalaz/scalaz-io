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

import zio.internal.tracing.TracingConfig
import zio.{Cause, FiberRef, LogLevel, Supervisor}

/**
 * A `Platform` provides the minimum capabilities necessary to bootstrap
 * execution of `ZIO` tasks.
 */
abstract class Platform { self =>

  /**
   * Retrieves the default executor for all blocking tasks.
   */
  def blockingExecutor: Executor

  /**
   * Retrieves the default executor.
   */
  def executor: Executor

  /**
   * Determines if a throwable is fatal or not. It is important to identify
   * these as it is not recommended to catch, and try to recover from, any
   * fatal error.
   */
  def fatal(t: Throwable): Boolean

  /**
    * Logs the specified message at the specified log level, using the provided context and region 
    * stack.
    */
  def log(level: LogLevel, message: () => String, context: Map[FiberRef.Runtime[_], AnyRef], regions: List[String]): Unit

  /**
   * Reports the specified failure.
   */
  def reportFailure(cause: Cause[Any]): Unit

  /**
   * Reports a fatal error.
   */
  def reportFatal(t: Throwable): Nothing

  /**
    * Retrieves the supervisor associated with the platform.
    */
  def supervisor: Supervisor[Any]

  /**
   * ZIO Tracing configuration.
   */
  def tracing: Tracing

  def withBlockingExecutor(e: Executor): Platform =
    new Platform.Proxy(self) {
      override def blockingExecutor: Executor = e
    }

  def withExecutor(e: Executor): Platform =
    new Platform.Proxy(self) {
      override def executor: Executor = e
    }

  def withFatal(f: Throwable => Boolean): Platform =
    new Platform.Proxy(self) {
      override def fatal(t: Throwable): Boolean = f(t)
    }

  def withLogger(logger: (LogLevel, () => String, Map[FiberRef.Runtime[_], AnyRef], List[String]) => Unit): Platform = 
    new Platform.Proxy(self) {
      override def log(level: LogLevel, message: () => String, context: Map[FiberRef.Runtime[_], AnyRef], regions: List[String]): Unit = 
        logger(level, message, context, regions)
    }

  def withReportFatal(f: Throwable => Nothing): Platform =
    new Platform.Proxy(self) {
      override def reportFatal(t: Throwable): Nothing = f(t)
    }

  def withReportFailure(f: Cause[Any] => Unit): Platform =
    new Platform.Proxy(self) {
      override def reportFailure(cause: Cause[Any]): Unit = f(cause)
    }

  def withSupervisor(s0: Supervisor[Any]): Platform =
    new Platform.Proxy(self) {
      override def supervisor: Supervisor[Any] = s0
    }

  def withTracing(t: Tracing): Platform =
    new Platform.Proxy(self) {
      override def tracing: Tracing = t
    }

  def withTracingConfig(config: TracingConfig): Platform =
    new Platform.Proxy(self) {
      override val tracing: Tracing = self.tracing.copy(tracingConfig = config)
    }
}
object Platform extends PlatformSpecific {
  abstract class Proxy(self: Platform) extends Platform {
    def executor: Executor                     = self.executor
    def blockingExecutor: Executor             = self.blockingExecutor
    def fatal(t: Throwable): Boolean           = self.fatal(t)
    def log(level: LogLevel, message: () => String, context: Map[FiberRef.Runtime[_], AnyRef], regions: List[String]): Unit = self.log(level, message, context, regions)
    def reportFailure(cause: Cause[Any]): Unit = self.reportFailure(cause)
    def reportFatal(t: Throwable): Nothing     = self.reportFatal(t)
    def supervisor: Supervisor[Any]            = self.supervisor
    def tracing: Tracing                       = self.tracing
  }
}
