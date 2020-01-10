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

package zio.scheduler

import scala.scalajs.js

import zio.duration.Duration
import zio.internal.{ Scheduler => IScheduler }

private[scheduler] trait PlatformSpecific {
  private[scheduler] val globalScheduler = new IScheduler {
    import IScheduler.CancelToken

    private[this] val ConstFalse = () => false

    private[this] var _size = 0

    override def schedule(task: Runnable, duration: Duration): CancelToken = duration match {
      case Duration.Infinity => ConstFalse
      case Duration.Zero =>
        task.run()

        ConstFalse
      case duration: Duration.Finite =>
        _size += 1
        var completed = false

        val handle = js.timers.setTimeout(duration.toMillis.toDouble) {
          completed = true

          try task.run()
          finally {
            _size -= 1
          }
        }
        () => {
          js.timers.clearTimeout(handle)
          if (!completed) _size -= 1
          !completed
        }
    }

    /**
     * The number of tasks scheduled.
     */
    override def size: Int = _size

    /**
     * Initiates shutdown of the scheduler.
     */
    override def shutdown(): Unit = ()
  }
}
