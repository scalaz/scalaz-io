/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.UIO
import scala.util.control.NonFatal

/**
 * A `RunnableSpec` has a main function and can be run by the JVM / Scala.js.
 */
abstract class RunnableSpec[+L, +T](runner: TestRunner[L, T])(spec: => Spec[L, T]) {

  /**
   * A simple main function that can be used to run the spec.
   *
   * TODO: Parse command line options.
   */
  final def main(args: Array[String]): Unit =
    runner.unsafeRunAsync(spec) { results =>
      try {
        if (results.exists { case Spec.TestCase(_, test) => test.failure; case _ => false }) System.exit(1)
        else System.exit(0)
      } catch { case NonFatal(_) => }
    }

  /**
   * Returns an effect that executes the spec, producing the results of the execution.
   */
  final val run: UIO[ExecutedSpec[L]] =
    runner.run(spec)

  /**
   * Side-effectfully executes the spec, asynchronously passing results to the
   * specified callback.
   */
  final def unsafeRunAsync(k: ExecutedSpec[L] => Unit): Unit =
    runner.unsafeRunAsync(spec)(k)
}
