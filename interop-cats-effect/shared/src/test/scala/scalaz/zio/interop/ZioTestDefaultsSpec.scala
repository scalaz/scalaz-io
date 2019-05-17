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
package interop

import com.github.ghik.silencer.silent
import scalaz.zio.interop.runtime.TestRuntime

final class ZioTestDefaultsSpec extends TestRuntime {

  def is = "ZioTestDefaultsSpec".title ^ s2"""
    The default test type-class instances for Zio:
      Don't give conflict when unified all together. $unifyAll
      Can summon Monad[F[E, ?]] when hierarchy instances are in scope. $summonSyntax
  """

  private[this] def unifyAll = {

    import default.testZioInstances._
    import scalaz.zio.interop.bio.{ Async2, Concurrent2, Errorful2, Guaranteed2, RunAsync2, RunSync2, Sync2, Temporal2 }

    @silent def f[F[+ _, + _]](
      implicit
      A: Guaranteed2[F],
      B: Errorful2[F],
      C: Sync2[F],
      D: Temporal2[F],
      E: Concurrent2[F],
      F: RunSync2[F],
      G: Async2[F],
      H: RunAsync2[F]
    ): Unit = ()

    val _ = f[IO]

    success
  }

  private[this] def summonSyntax = {

    import scalaz.zio.interop.bio.{ Concurrent2, RunAsync2, RunSync2, _ }

    @silent def f1[F[+ _, + _], E, A](fa: F[E, A])(
      implicit
      A: Concurrent2[F],
      B: RunSync2[F],
      C: RunAsync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    @silent def f2[F[+ _, + _], E, A](fa: F[E, A])(
      implicit
      A: Concurrent2[F],
      B: Sync2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    @silent def f3[F[+ _, + _], E, A](fa: F[E, A])(
      implicit
      A: Concurrent2[F],
      B: Async2[F]
    ): Unit =
      fa >>= { _ =>
        A.monad.unit
      }

    success
  }
}
