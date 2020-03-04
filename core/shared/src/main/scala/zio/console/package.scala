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

import java.io.IOException

package object console {
  type Console = Has[Service]

  /**
   * Prints text to the console.
   */
  def putStr(line: => String): ZIO[Console, Nothing, Unit] =
    ZIO.accessM(_.get putStr line)

  /**
   * Prints a line of text to the console, including a newline character.
   */
  def putStrLn(line: => String): ZIO[Console, Nothing, Unit] =
    ZIO.accessM(_.get putStrLn line)

  /**
   * Retrieves a line of input from the console.
   */
  val getStrLn: ZIO[Console, IOException, String] =
    ZIO.accessM(_.get.getStrLn)
}
