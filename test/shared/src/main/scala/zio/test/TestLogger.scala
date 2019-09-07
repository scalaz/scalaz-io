/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.{ UIO, URIO, ZIO }
import zio.console.Console

trait TestLogger {
  def testLogger: TestLogger.Service
}

object TestLogger {
  trait Service {
    def logLine(line: String): UIO[Unit]
  }

  def fromConsole(console: Console): TestLogger = new TestLogger {
    override def testLogger: Service =
      (line: String) => console.console.putStrLn(line)
  }

  def fromConsoleM: URIO[Console, TestLogger] = ZIO.access[Console](fromConsole)

  def logLine(line: String): URIO[TestLogger, Unit] =
    ZIO.accessM(_.testLogger.logLine(line))
}
