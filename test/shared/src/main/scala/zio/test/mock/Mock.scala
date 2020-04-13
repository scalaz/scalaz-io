/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import zio.internal.Executor
import zio.stream.{ ZSink, ZStream }
import zio.test.TestPlatform
import zio.{ Has, Runtime, Tagged, URIO, URLayer, ZIO }

/**
 * A `Mock[R]` represents a mockable environenment `R`.
 */
abstract class Mock[R <: Has[_]: Tagged] { self =>

  protected[test] val compose: URLayer[Has[Proxy], R]

  /**
   * Replaces Runtime on JS platform to one with unyielding executor.
   */
  protected def withRuntime[R]: URIO[R, Runtime[R]] =
    ZIO.runtime[R].map { runtime =>
      if (!TestPlatform.isJS) runtime
      else
        runtime.withExecutor {
          val ec = runtime.platform.executor.asEC
          Executor.fromExecutionContext(Int.MaxValue)(ec)
        }
    }

  abstract class Effect[I: Tagged, E: Tagged, A: Tagged]              extends Capability[R, I, E, A](self)
  abstract class Method[I: Tagged, E <: Throwable: Tagged, A: Tagged] extends Capability[R, I, E, A](self)
  abstract class Sink[I: Tagged, E: Tagged, A0: Tagged, A: Tagged, B: Tagged]
      extends Capability[R, I, E, ZSink[Any, E, A0, A, B]](self)
  abstract class Stream[I: Tagged, E: Tagged, A: Tagged] extends Capability[R, I, Nothing, ZStream[Any, E, A]](self)

  object Poly {

    object Effect {
      abstract class Input[E: Tagged, A: Tagged]  extends Capability.Poly.Input[R, E, A](self)
      abstract class Error[I: Tagged, A: Tagged]  extends Capability.Poly.Error[R, I, A, Any](self)
      abstract class Output[I: Tagged, E: Tagged] extends Capability.Poly.Output[R, I, E, Any](self)
      abstract class InputError[A: Tagged]        extends Capability.Poly.InputError[R, A, Any](self)
      abstract class InputOutput[E: Tagged]       extends Capability.Poly.InputOutput[R, E, Any](self)
      abstract class ErrorOutput[I: Tagged]       extends Capability.Poly.ErrorOutput[R, I, Any, Any](self)
      abstract class InputErrorOutput             extends Capability.Poly.InputErrorOutput[R, Any, Any](self)
    }

    object Method {
      abstract class Input[E <: Throwable: Tagged, A: Tagged]  extends Capability.Poly.Input[R, E, A](self)
      abstract class Error[I: Tagged, A: Tagged]               extends Capability.Poly.Error[R, I, A, Throwable](self)
      abstract class Output[I: Tagged, E <: Throwable: Tagged] extends Capability.Poly.Output[R, I, E, Any](self)
      abstract class InputError[A: Tagged]                     extends Capability.Poly.InputError[R, A, Throwable](self)
      abstract class InputOutput[E <: Throwable: Tagged]       extends Capability.Poly.InputOutput[R, E, Any](self)
      abstract class ErrorOutput[I: Tagged]                    extends Capability.Poly.ErrorOutput[R, I, Throwable, Any](self)
      abstract class InputErrorOutput                          extends Capability.Poly.InputErrorOutput[R, Throwable, Any](self)
    }
  }
}

object Mock {

  private[mock] case class Composed[R <: Has[_]: Tagged](compose: URLayer[Has[Proxy], R]) extends Mock[R]
}
