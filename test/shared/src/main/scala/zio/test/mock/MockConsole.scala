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

import java.io.IOException
import java.io.EOFException

import zio.console._
import zio._

trait MockConsole extends Console {
  val console: MockConsole.Service[Any]
}

object MockConsole {

  trait Service[R] extends Console.Service[R] {
    def feedLines(lines: String*): UIO[Unit]
    def output: UIO[Vector[String]]
    def clearInput: UIO[Unit]
    def clearOutput: UIO[Unit]
  }

  case class Mock(consoleState: Ref[MockConsole.Data]) extends MockConsole.Service[Any] {

    override def putStr(line: String): UIO[Unit] =
      consoleState.update { data =>
        Data(data.input, data.output :+ line)
      }.unit

    override def putStrLn(line: String): ZIO[Any, Nothing, Unit] =
      consoleState.update { data =>
        Data(data.input, data.output :+ s"$line\n")
      }.unit

    val getStrLn: ZIO[Any, IOException, String] = {
      for {
        input <- consoleState.get.flatMap(
                  d =>
                    IO.fromOption(d.input.headOption)
                      .mapError(_ => new EOFException("There is no more input left to read"))
                )
        _ <- consoleState.update { data =>
              Data(data.input.tail, data.output)
            }
      } yield input
    }

    def feedLines(lines: String*): UIO[Unit] =
      consoleState.update(data => data.copy(input = lines.toList ::: data.input)).unit

    val output: UIO[Vector[String]] =
      consoleState.get.map(_.output)

    val clearInput: UIO[Unit] =
      consoleState.update(data => data.copy(input = List.empty)).unit

    val clearOutput: UIO[Unit] =
      consoleState.update(data => data.copy(output = Vector.empty)).unit
  }

  def make(data: Data): UIO[MockConsole] =
    makeMock(data).map { mock =>
      new MockConsole {
        val console = mock
      }
    }

  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data).map(Mock(_))

  def feedLines(lines: String*): ZIO[MockConsole, Nothing, Unit] =
    ZIO.accessM(_.console.feedLines(lines: _*))

  val output: ZIO[MockConsole, Nothing, Vector[String]] =
    ZIO.accessM(_.console.output)

  val clearInput: ZIO[MockConsole, Nothing, Unit] =
    ZIO.accessM(_.console.clearInput)

  val clearOutput: ZIO[MockConsole, Nothing, Unit] =
    ZIO.accessM(_.console.clearOutput)

  val DefaultData: Data = Data(Nil, Vector())

  case class Data(input: List[String] = List.empty, output: Vector[String] = Vector.empty)
}
