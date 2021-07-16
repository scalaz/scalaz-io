/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

sealed trait LogLevel extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] { self =>
  def ordinal: Int
  def label: String
  def syslog: Int

  def apply[R >: Nothing <: Any, E >: Nothing <: Any, A >: Nothing <: Any](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    FiberRef.currentLogLevel.locally(self)(zio)
}
object LogLevel {
  final case class Value(ordinal: Int, label: String, syslog: Int) extends LogLevel

  val All     = Value(Int.MinValue, "ALL", -1)
  val Fatal   = Value(50000, "FATAL", 2)
  val Error   = Value(40000, "ERROR", 3)
  val Warning = Value(30000, "WARN", 4)
  val Info    = Value(20000, "INFO", 6)
  val Debug   = Value(10000, "DEBUG", 7)
  val None    = Value(Int.MaxValue, "OFF", 8)

  implicit val orderingLogLevel: Ordering[LogLevel] = Ordering.by(_.ordinal)
}
