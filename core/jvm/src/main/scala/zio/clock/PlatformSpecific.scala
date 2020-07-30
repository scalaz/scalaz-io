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

package zio.clock

import java.util.concurrent._

import java.time.Duration
import zio.internal.{ NamedThreadFactory, Scheduler }

private[clock] trait PlatformSpecific {
  import Scheduler.CancelToken

  private[clock] val globalScheduler = new Scheduler {

    private[this] val service = Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

    private[this] val ConstFalse = () => false

    override def schedule(task: Runnable, duration: Duration): CancelToken = {
      val nanos = duration.toNanos
      if (nanos <= 0) {
        task.run()

        ConstFalse
      } else {
        nanos match {
          case zio.duration.infiniteNano => ConstFalse
          case nanos =>
            val future = service.schedule(new Runnable {
              def run: Unit =
                task.run()
            }, nanos.toLong, TimeUnit.NANOSECONDS)

            () => {
              val canceled = future.cancel(true)

              canceled
            }
        }
      }

    }

  }
}
