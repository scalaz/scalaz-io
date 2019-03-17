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

package scalaz.zio

import scalaz.zio.internal.Executor

package object blocking {
  final def blockingExecutor: ZIO[Blocking, Nothing, Executor]                = blockingPackage.blockingExecutor
  final def blocking[R1 <: Blocking, E, A](zio: ZIO[R1, E, A]): ZIO[R1, E, A] = blockingPackage.blocking(zio)
  final def interruptible[A](effect: => A): ZIO[Blocking, Throwable, A]       = blockingPackage.interruptible(effect)
}

import blocking._
private object blockingPackage extends Blocking.Service[Blocking] {
  def blockingExecutor: ZIO[Blocking, Nothing, Executor] =
    ZIO.accessM(_.blocking.blockingExecutor)
}
