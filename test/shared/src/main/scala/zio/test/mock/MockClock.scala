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

package zio.test.mock

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import zio.UIO
import zio.clock.Clock
import zio.duration.Duration

object MockClock {

  trait Service extends Clock.Service

  object currentTime     extends Method[MockClock.Service, TimeUnit, Long]
  object currentDateTime extends Method[MockClock.Service, Unit, OffsetDateTime]
  object nanoTime        extends Method[MockClock.Service, Unit, Long]
  object sleep           extends Method[MockClock.Service, Duration, Unit]

  implicit val mockable: Mockable[MockClock.Service] = (mock: Mock) =>
    new Service {
      def currentTime(unit: TimeUnit): UIO[Long] = mock(MockClock.currentTime, unit)
      def currentDateTime: UIO[OffsetDateTime]   = mock(MockClock.currentDateTime)
      val nanoTime: UIO[Long]                    = mock(MockClock.nanoTime)
      def sleep(duration: Duration): UIO[Unit]   = mock(MockClock.sleep, duration)
    }
}
