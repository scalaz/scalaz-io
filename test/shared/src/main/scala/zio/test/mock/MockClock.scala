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

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import zio.clock.Clock
import zio.duration.Duration
import zio.{ Has, UIO, URLayer, ZLayer }

object MockClock {

  sealed trait Tag[I, A] extends Method[Clock, I, A] {
    def envBuilder = MockClock.envBuilder
  }

  object CurrentTime     extends Tag[TimeUnit, Long]
  object CurrentDateTime extends Tag[Unit, OffsetDateTime]
  object NanoTime        extends Tag[Unit, Long]
  object Sleep           extends Tag[Duration, Unit]

  private lazy val envBuilder: URLayer[Has[Proxy], Clock] =
    ZLayer.fromService(invoke =>
      new Clock.Service {
        def currentTime(unit: TimeUnit): UIO[Long] = invoke(CurrentTime, unit)
        def currentDateTime: UIO[OffsetDateTime]   = invoke(CurrentDateTime)
        val nanoTime: UIO[Long]                    = invoke(NanoTime)
        def sleep(duration: Duration): UIO[Unit]   = invoke(Sleep, duration)
      }
    )
}
