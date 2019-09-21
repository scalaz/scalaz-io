package zio.internal
import java.util.concurrent.RejectedExecutionException

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

import scala.concurrent.ExecutionContext

final class TestExecutor(val submitResult: Boolean) extends Executor {
  val here: Boolean                       = true
  def shutdown(): Unit                    = ()
  def submit(runnable: Runnable): Boolean = submitResult
  def yieldOpCount: Int                   = 1
  def metrics: None.type                  = None
}

final class CheckPrintThrowable extends Throwable {
  var printed = false

  override def printStackTrace(): Unit = printed = true
}

object TestExecutor {
  val failing = new TestExecutor(false)
  val y       = new TestExecutor(true)
  val u       = new TestExecutor(true)

  val badEC = new ExecutionContext {
    override def execute(r: Runnable): Unit            = throw new RejectedExecutionException("Rejected: " + r.toString)
    override def reportFailure(cause: Throwable): Unit = ()
  }

  val ec = new ExecutionContext {
    override def execute(r: Runnable): Unit            = ()
    override def reportFailure(cause: Throwable): Unit = ()
  }

  // backward compatibility for scala 2.11.12
  val runnable = new Runnable {
    override def run(): Unit = ()
  }
}

object ExecutorSpec
    extends ZIOBaseSpec(
      suite("ExecutorSpec")(
        suite("Create the default unyielding executor and check that:")(
          test("When converted to an EC, it reports Throwables to stdout") {
            val t = new CheckPrintThrowable
            TestExecutor.failing.asEC.reportFailure(t)
            assert(t.printed, isTrue)
          }
        ),
        suite("Create an executor that cannot have tasks submitted to and check that:")(
          test("It throws an exception upon submission") {
            assert(
              TestExecutor.failing.submitOrThrow(TestExecutor.runnable),
              throwsA[RejectedExecutionException]
            )
          }
        ),
        suite("Create a yielding executor and check that:")(
          test("Runnables can be submitted ") {
            assert(TestExecutor.y.submitOrThrow(TestExecutor.runnable), not(throwsA[RejectedExecutionException]))
          },
          test("When converted to an ExecutionContext, it accepts Runnables") {
            assert(TestExecutor.y.asEC.execute(TestExecutor.runnable), not(throwsA[RejectedExecutionException]))
          },
          test("When created from an EC, must not throw when fed an effect ") {
            assert(
              Executor.fromExecutionContext(1)(TestExecutor.ec).submit(TestExecutor.runnable),
              not(throwsA[RejectedExecutionException])
            )
          }
        ),
        suite("Create an unyielding executor and check that:")(
          test("Runnables can be submitted") {
            assert(TestExecutor.u.submitOrThrow(TestExecutor.runnable), not(throwsA[RejectedExecutionException]))
          },
          test("When converted to an ExecutionContext, it accepts Runnables") {
            assert(TestExecutor.u.asEC.execute(TestExecutor.runnable), not(throwsA[RejectedExecutionException]))
          }
        )
      )
    )
