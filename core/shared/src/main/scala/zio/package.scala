/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package object zio extends EitherCompat {
  private[zio] type Callback[E, A] = Exit[E, A] => Unit

  type Canceler[R] = URIO[R, Any]
  type FiberId     = Long

  type RIO[-R, +A]  = ZIO[R, Throwable, A]
  type URIO[-R, +A] = ZIO[R, Nothing, A]
  type IO[+E, +A]   = ZIO[Any, E, A]
  type UIO[+A]      = ZIO[Any, Nothing, A]
  type Task[+A]     = ZIO[Any, Throwable, A]
  @deprecated("use RIO", "1.0.0")
  type TaskR[-R, +A] = RIO[R, A]

  @deprecated("use RIO", "1.0.0")
  val TaskR = RIO

  type Managed[+E, +A] = ZManaged[Any, E, A]

  type Schedule[-A, +B] = ZSchedule[Any, A, B]

  type Queue[A] = ZQueue[Any, Nothing, Any, Nothing, A, A]

  object <*> {
    def unapply[A, B](ab: (A, B)): Some[(A, B)] =
      Some((ab._1, ab._2))
  }
}
