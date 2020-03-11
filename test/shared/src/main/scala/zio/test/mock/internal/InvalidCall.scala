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

package zio.test.mock.internal

import zio.Has
import zio.test.Assertion
import zio.test.mock.Method

/**
 * An `InvalidCall` describes failed expectation.
 */
sealed trait InvalidCall

object InvalidCall {

  final case class InvalidArguments[R <: Has[_], I, A](
    method: Method[R, I, A],
    args: Any,
    assertion: Assertion[Any]
  ) extends InvalidCall

  final case class InvalidMethod[R0 <: Has[_], R1 <: Has[_], In0, In1, A0, A1](
    method: Method[R0, In0, A0],
    expectedMethod: Method[R1, In1, A1],
    assertion: Assertion[In1]
  ) extends InvalidCall
}
