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

package zio.clock

import java.util.concurrent.TimeUnit

import zio.duration.Duration
import zio.scheduler.SchedulerLive
import zio.{ IO, UIO, ZIO }
import java.time.{ Instant, OffsetDateTime, ZoneId }

trait Clock extends Serializable {
  val clock: Clock.Service
}

object Clock extends Serializable {
  trait Service extends Serializable {
    def currentTime(unit: TimeUnit): UIO[Long]
    def currentDateTime: UIO[OffsetDateTime]
    val nanoTime: UIO[Long]
    def sleep(duration: Duration): UIO[Unit]
  }

  trait Live extends SchedulerLive with Clock {
    val clock: Service = new Service {
      def currentTime(unit: TimeUnit): UIO[Long] =
        IO.effectTotal(System.currentTimeMillis).map(l => unit.convert(l, TimeUnit.MILLISECONDS))

      val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime)

      def sleep(duration: Duration): UIO[Unit] =
        scheduler.scheduler.flatMap(
          scheduler =>
            UIO.effectAsyncInterrupt[Unit] { k =>
              val canceler = scheduler
                .schedule(() => k(UIO.unit), duration)

              Left(UIO.effectTotal(canceler()))
            }
        )

      def currentDateTime: UIO[OffsetDateTime] =
        for {
          millis <- currentTime(TimeUnit.MILLISECONDS)
          zone   <- ZIO.effectTotal(ZoneId.systemDefault)
        } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    }
  }
  object Live extends Live
}
