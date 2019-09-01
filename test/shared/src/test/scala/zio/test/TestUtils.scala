package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.{ Schedule, ZIO }
import zio.clock.Clock

object TestUtils {

  final def label(test: => Future[Boolean], label: String): Async[(Boolean, String)] =
    Async
      .fromFuture(test)
      .map(passed => if (passed) (passed, succeed(label)) else (passed, fail(label)))
      .handle { case _ => (false, fail(label)) }

  final def nonFlaky[R, E](test: ZIO[R, E, Boolean]): ZIO[R with Clock, E, Boolean] =
    test.repeat(Schedule.recurs(100) *> Schedule.identity[Boolean])

  final def report(suites: List[Async[List[(Boolean, String)]]])(implicit ec: ExecutionContext): Unit = {
    val async = Async
      .sequence(suites)
      .map(_.flatten)
      .flatMap { results =>
        val passed = results.forall(_._1)
        results.foreach(result => println(result._2))
        if (passed) Async.succeed(true)
        else Async(TestPlatform.fail()).map(_ => false)
      }
    TestPlatform.await(async.run(ec))
  }

  final def scope(tests: List[Async[(Boolean, String)]], label: String): Async[List[(Boolean, String)]] =
    Async.sequence(tests).map { tests =>
      val offset = tests.map { case (passed, label) => (passed, "  " + label) }
      val passed = tests.forall(_._1)
      if (passed) (passed, succeed(label)) :: offset else (passed, fail(label)) :: offset
    }

  final def timeit[A](label: String)(async: Async[A]): Async[A] =
    for {
      start  <- Async(System.currentTimeMillis)
      result <- async
      stop   <- Async(System.currentTimeMillis)
      _      <- Async(println(s"$label took ${(stop - start) / 1000.0} seconds"))
    } yield result

  private def succeed(s: String): String =
    green("+") + " " + s

  private def fail(s: String): String =
    red("-" + " " + s)

  private def green(s: String): String =
    Console.GREEN + s + Console.RESET

  private def red(s: String): String =
    Console.RED + s + Console.RESET
}
