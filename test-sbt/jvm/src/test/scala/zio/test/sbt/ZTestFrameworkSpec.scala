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

package zio.test.sbt

import sbt.testing._
import zio.FunctionIO
import zio.test.sbt.TestingSupport._
import zio.test.{ Assertion, DefaultRunnableSpec, TestArgs, TestAspect }

import scala.collection.mutable.ArrayBuffer

object ZTestFrameworkSpec {

  def main(args: Array[String]): Unit =
    run(tests: _*)

  def tests = Seq(
    test("should return correct fingerprints")(testFingerprints()),
    test("should report events")(testReportEvents()),
    test("should log messages")(testLogMessages()),
    test("should test only selected test")(testTestSelection()),
    test("should return summary when done")(testSummary())
  )

  def testFingerprints() = {
    val fingerprints = new ZTestFramework().fingerprints.toSeq
    assertEquals("fingerprints", fingerprints, Seq(RunnableSpecFingerprint))
  }

  def testReportEvents() = {
    val reported = ArrayBuffer[Event]()
    loadAndExecute(failingSpecFQN, reported.append(_))

    val expected = Set(
      sbtEvent(failingSpecFQN, "failing test", Status.Failure),
      sbtEvent(failingSpecFQN, "passing test", Status.Success),
      sbtEvent(failingSpecFQN, "ignored test", Status.Ignored)
    )

    assertEquals("reported events", reported.toSet, expected)
  }

  private def sbtEvent(fqn: String, label: String, status: Status) =
    ZTestEvent(fqn, new TestSelector(label), status, None, 0, RunnableSpecFingerprint)

  def testLogMessages() = {
    val loggers = Seq.fill(3)(new MockLogger)

    loadAndExecute(failingSpecFQN, loggers = loggers)

    loggers.map(_.messages) foreach (
      messages =>
        assertEquals(
          "logged messages",
          messages.toList.dropRight(1),
          List(
            s"info: ${red("- some suite")}",
            s"info:   ${red("- failing test")}",
            s"info:     ${blue("1")} did not satisfy ${cyan("equalTo(2)")}",
            s"info:   ${green("+")} passing test"
          )
        )
      )
  }

  def testTestSelection() = {
    val loggers = Seq(new MockLogger)

    loadAndExecute(failingSpecFQN, loggers = loggers, testArgs = Array("-t", "passing test"))

    loggers.map(_.messages) foreach (
      messages =>
        assertEquals(
          "logged messages",
          messages.toList.dropRight(1),
          List(
            s"info: ${green("+")} some suite",
            s"info:   ${green("+")} passing test"
          )
        )
      )
  }

  def testSummary() = {
    val taskDef = new TaskDef(failingSpecFQN, RunnableSpecFingerprint, false, Array())
    val runner  = new ZTestFramework().runner(Array(), Array(), getClass.getClassLoader)
    val task = runner
      .tasks(Array(taskDef))
      .map(task => {
        val zTestTask = task.asInstanceOf[BaseTestTask]
        new ZTestTask(
          zTestTask.taskDef,
          zTestTask.testClassLoader,
          FunctionIO.succeed("foo") >>> zTestTask.sendSummary,
          TestArgs.empty
        )
      })
      .head

    task.execute(_ => (), Array.empty)

    assertEquals("done contains summary", runner.done(), "foo\nDone")
  }

  private def loadAndExecute(
    fqn: String,
    eventHandler: EventHandler = _ => (),
    loggers: Seq[Logger] = Nil,
    testArgs: Array[String] = Array.empty
  ) = {
    val taskDef = new TaskDef(fqn, RunnableSpecFingerprint, false, Array())
    val task = new ZTestFramework()
      .runner(testArgs, Array(), getClass.getClassLoader)
      .tasks(Array(taskDef))
      .head

    task.execute(eventHandler, loggers.toArray)
  }

  lazy val failingSpecFQN = SimpleFailingSpec.getClass.getName
  object SimpleFailingSpec
      extends DefaultRunnableSpec(
        zio.test.suite("some suite")(
          zio.test.test("failing test") {
            zio.test.assert(1, Assertion.equalTo(2))
          },
          zio.test.test("passing test") {
            zio.test.assert(1, Assertion.equalTo(1))
          },
          zio.test.test("ignored test") {
            zio.test.assert(1, Assertion.equalTo(2))
          } @@ TestAspect.ignore
        )
      )
}
