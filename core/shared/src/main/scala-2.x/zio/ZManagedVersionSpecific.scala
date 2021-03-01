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

package zio

import zio.internal.macros.ProvideLayerAutoMacros

private[zio] trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>

  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to zio
   * val zio2 : ZIO[ZEnv, Nothing, Unit] = zio.provideCustomLayerAuto(oldLadyLayer, flyLayer)
   * }}}
   */
  def provideCustomLayerAuto[E1 >: E](layers: ZLayer[_, E1, _]*): ZIO[ZEnv, E1, A] =
    macro ProvideLayerAutoMacros.provideCustomLayerAutoImpl[ZIO, R, E1, A]

  /**
   * Automatically assembles a layer for the ZIO effect.
   */
  def provideLayerAuto[E1 >: E](layers: ZLayer[_, E1, _]*): ZIO[Any, E1, A] =
    macro ProvideLayerAutoMacros.provideLayerAutoImpl[ZIO, R, E1, A]

}