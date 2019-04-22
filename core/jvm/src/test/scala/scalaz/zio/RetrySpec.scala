package scalaz.zio

import org.specs2.ScalaCheck
import scalaz.zio.clock.Clock
import scalaz.zio.delay.Delay
import scalaz.zio.duration._
import scalaz.zio.random._

class RetrySpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  def is = "RetrySpec".title ^ s2"""
   Retry on failure according to a provided strategy
      retry 0 time for `once` when first time succeeds $notRetryOnSuccess
      retry 0 time for `recurs(0)` $retryRecurs0
      retry exactly one time for `once` when second time succeeds $retryOnceSuccess
      retry exactly one time for `once` even if still in error $retryOnceFail
      for a given number of times $retryN
      for a given number of times with random jitter in (0, 1) $retryNUnitIntervalJittered
      for a given number of times with random jitter in custom interval $retryNCustomIntervalJittered
      fixed delay with error predicate $fixedWithErrorPredicate
      fibonacci delay $fibonacci
      linear delay $linear
      exponential delay with default factor $exponential
      exponential delay with other factor $exponentialWithFactor
  Retry according to a provided strategy
    for up to 10 times $recurs10Retry
  Return the result of the fallback after failing and no more retries left
    if fallback succeed - retryOrElse $retryOrElseFallbackSucceed
    if fallback failed - retryOrElse $retryOrElseFallbackFailed
    if fallback succeed - retryOrElseEither $retryOrElseEitherFallbackSucceed
    if fallback failed - retryOrElseEither $retryOrElseEitherFallbackFailed
  Return the result after successful retry
     retry exactly one time for `once` when second time succeeds - retryOrElse $retryOrElseSucceed
     retry exactly one time for `once` when second time succeeds - retryOrElse0 $retryOrElseEitherSucceed
  Retry a failed action 2 times and call `ensuring` should
     run the specified finalizer as soon as the schedule is complete $ensuring
  """

  def retryCollect[R, E, A, E1 >: E, S](
    io: IO[E, A],
    retry: ZSchedule[R, E1, S]
  ): ZIO[R, Nothing, (Either[E1, A], List[(Delay, S)])] = {

    type State = retry.State

    def loop(
      state: State,
      ss: List[(Delay, S)]
    ): ZIO[R, Nothing, (Either[E1, A], List[(Delay, S)])] =
      io.foldM(
        err =>
          retry
            .update(err, state)
            .flatMap(
              step =>
                if (!step.cont) IO.succeed((Left(err), (step.delay, step.finish()) :: ss))
                else loop(step.state, (step.delay, step.finish()) :: ss)
            ),
        suc => IO.succeed((Right(suc), ss))
      )

    retry.initial.flatMap(s => loop(s, Nil)).map(x => (x._1, x._2.reverse))
  }

  /*
   * Retry `once` means that we try to exec `io`, get and error,
   * try again to exec `io`, and whatever the output is, we return that
   * second result.
   * The three following tests test retry when:
   * - the first time succeeds (no retry)
   * - the first time fails and the second succeeds (one retry, result success)
   * - both first time and retry fail (one retry, result failure)
   */

  // no retry on success
  def notRetryOnSuccess = {
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      _   <- ref.update(_ + 1).retry(Schedule.once)
      i   <- ref.get
    } yield i)

    retried must_=== 1
  }

  // one retry on failure
  def retryOnceSuccess = {
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      _   <- failOn0(ref).retry(Schedule.once)
      r   <- ref.get
    } yield r)

    retried must_=== 2
  }

  // no more than one retry on retry `once`
  def retryOnceFail = {
    val retried = unsafeRun(
      (for {
        ref <- Ref.make(0)
        _   <- alwaysFail(ref).retry(Schedule.once)
      } yield ()).foldM(
        err => IO.succeed(err),
        _ => IO.succeed("A failure was expected")
      )
    )

    retried must_=== "Error: 2"
  }

  // 0 retry means "one execution in all, no retry, whatever the output"
  def retryRecurs0 = {
    val retried = unsafeRun(
      (for {
        ref <- Ref.make(0)
        i   <- alwaysFail(ref).retry(Schedule.recurs(0))
      } yield i).foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success")
      )
    )

    retried must_=== "Error: 1"
  }

  def retryN = {
    val retried = unsafeRun(retryCollect(IO.fail("Error"), Schedule.recurs(5)))
    val expected =
      (Left("Error"), List(1, 2, 3, 4, 5, 6).map((Delay.none, _)))
    retried must_=== expected
  }

  def retryNUnitIntervalJittered = {
    val schedule: ZSchedule[Random, Int, Int] =
      Schedule.recurs(5).delayed(_ => 500.millis.relative).jittered

    val expected = List(1, 2, 3, 4, 5).map((250.millis.relative, _))

    val expectedDelays = accumulateDelays(expected.map(_._1.run))

    val expectedScheduled = schedule
      .run(List(1, 2, 3, 4, 5))
      .provide(TestRandom)
      .map(_.map(_._1.run))
      .flatMap(accumulateDelays)

    val results = unsafeRun(
      for {
        d1 <- expectedDelays
        d2 <- expectedScheduled
      } yield d1.zip(d2)
    )

    results.map { case (d1, d2) => d1 must_=== d2 }
  }

  def retryNCustomIntervalJittered = {
    val schedule: ZSchedule[Random, Int, Int] =
      Schedule.recurs(5).delayed(_ => 500.millis.relative).jittered(2, 4)

    val expected = List(1, 2, 3, 4, 5).map((1500.millis.relative, _))

    val expectedDelays = accumulateDelays(expected.map(_._1.run))

    val expectedScheduled = schedule
      .run(List(1, 2, 3, 4, 5))
      .provide(TestRandom)
      .map(_.map(_._1.run))
      .flatMap(accumulateDelays)

    val results = unsafeRun(
      for {
        d1 <- expectedDelays
        d2 <- expectedScheduled
      } yield d1.zip(d2)
    )

    results.map { case (d1, d2) => d1 must_=== d2 }
  }

  def accumulateDelays[T](lst: List[ZIO[scalaz.zio.clock.Clock, Nothing, T]]): ZIO[Clock, Nothing, List[T]] =
    lst.foldLeft(clock.nanoTime.map(_ => List.empty[T]))((acc, b) => acc.flatMap(d => b.map(d2 => d :+ d2)))

  def fixedWithErrorPredicate = {
    var i = 0
    val io = IO.effectTotal[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy = Schedule.spaced(200.millis).whileInput[String](_ == "KeepTryingError")
    val retried  = retryCollect(io, strategy)
    val expected =
      (Left("GiveUpError"), List(1, 2, 3, 4, 5).map((200.millis.relative, _)))

    val retriedResults = retried.flatMap(
      tup =>
        accumulateDelays(tup._2.map(res => res._1.run.map(d => (d, res._2))))
          .map(lst => (tup._1, lst))
    )

    val expectedResult = accumulateDelays(expected._2.map { case (dur, ct) => dur.run.map(d1 => (d1, ct)) })
      .map(lst => (expected._1, lst))

    val (results1, results2) = unsafeRun(
      for {
        r1 <- retriedResults
        r2 <- expectedResult
      } yield (r1, r2)
    )

    results1 must_=== results2
  }

  def recurs10Retry = {
    var i                            = 0
    val strategy: Schedule[Any, Int] = Schedule.recurs(10)
    val io = IO.effectTotal[Unit](i += 1).flatMap { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.succeedLazy(i)
    }
    val result   = unsafeRun(io.retry(strategy))
    val expected = 5
    result must_=== expected
  }

  def fibonacci =
    checkErrorWithPredicate(Schedule.fibonacci(100.millis), List(1, 1, 2, 3, 5))

  def linear =
    checkErrorWithPredicate(Schedule.linear(100.millis), List(1, 2, 3, 4, 5))

  def exponential =
    checkErrorWithPredicate(Schedule.exponential(100.millis), List(2, 4, 8, 16, 32))

  def exponentialWithFactor =
    checkErrorWithPredicate(Schedule.exponential(100.millis, 3.0), List(3, 9, 27, 81, 243))

  def checkErrorWithPredicate(schedule: Schedule[Any, Duration], expectedSteps: List[Int]) = {
    var i = 0
    val io = IO.effectTotal[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy = schedule.whileInput[String](_ == "KeepTryingError")
    val retried  = retryCollect(io, strategy)
    val expected = (
      Left("GiveUpError"),
      expectedSteps.map(
        i =>
          (
            (i * 100).millis,
            (i * 100).millis
          )
      )
    )

    val retriedResults = retried
      .flatMap(
        tup =>
          accumulateDelays(tup._2.map { case (dl1, dl2) => dl1.run.map(d1 => (d1, dl2)) })
            .map(lst => (tup._1, lst))
      )

    val results1 = unsafeRun(
      for {
        r1 <- retriedResults
      } yield r1
    )

    results1 must_=== expected
  }

  val ioSucceed = (_: String, _: Unit) => IO.succeed("OrElse")

  val ioFail = (_: String, _: Unit) => IO.fail("OrElseFailed")

  def retryOrElseSucceed = {
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      o   <- failOn0(ref).retryOrElse(Schedule.once, ioFail)
    } yield o)

    retried must_=== 2
  }

  def retryOrElseFallbackSucceed = {
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      o   <- alwaysFail(ref).retryOrElse(Schedule.once, ioSucceed)
    } yield o)

    retried must_=== "OrElse"
  }

  def retryOrElseFallbackFailed = {
    val retried = unsafeRun(
      (for {
        ref <- Ref.make(0)
        i   <- alwaysFail(ref).retryOrElse(Schedule.once, ioFail)
      } yield i).foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success")
      )
    )

    retried must_=== "OrElseFailed"
  }

  def retryOrElseEitherSucceed = {
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      o   <- failOn0(ref).retryOrElseEither(Schedule.once, ioFail)
    } yield o)

    retried must beRight(2)
  }

  def retryOrElseEitherFallbackSucceed = {
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      o   <- alwaysFail(ref).retryOrElseEither(Schedule.once, ioSucceed)
    } yield o)

    retried must beLeft("OrElse")
  }

  def retryOrElseEitherFallbackFailed = {
    val retried = unsafeRun(
      (for {
        ref <- Ref.make(0)
        i   <- alwaysFail(ref).retryOrElseEither(Schedule.once, ioFail)
      } yield i).foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success")
      )
    )

    retried must_=== "OrElseFailed"
  }

  /*
   * A function that increments ref each time it is called.
   * It returns either a failure if ref value is 0 or less
   * before increment, and the value in other cases.
   */
  def failOn0(ref: Ref[Int]): IO[String, Int] =
    for {
      i <- ref.update(_ + 1)
      x <- if (i <= 1) IO.fail(s"Error: $i") else IO.succeed(i)
    } yield x

  /*
   * A function that increments ref each time it is called.
   * It always fails, with the incremented value in error
   */
  def alwaysFail(ref: Ref[Int]): IO[String, Int] =
    for {
      i <- ref.update(_ + 1)
      x <- IO.fail(s"Error: $i")
    } yield x

  def ensuring =
    unsafeRun(for {
      p          <- Promise.make[Nothing, Unit]
      v          <- IO.fail("oh no").retry(Schedule.recurs(2)).ensuring(p.succeed(())).option
      finalizerV <- p.poll
    } yield (v must beNone) and (finalizerV.isDefined must beTrue))

  object TestRandom extends Random {
    object random extends Random.Service[Any] {
      val nextBoolean: UIO[Boolean] = UIO.succeed(false)
      def nextBytes(length: Int): UIO[Chunk[Byte]] =
        UIO.succeed(Chunk.empty)
      val nextDouble: UIO[Double] =
        UIO.succeed(0.5)
      val nextFloat: UIO[Float] =
        UIO.succeed(0.5f)
      val nextGaussian: UIO[Double] =
        UIO.succeed(0.5)
      def nextInt(n: Int): UIO[Int] =
        UIO.succeed(n - 1)
      val nextInt: UIO[Int] =
        UIO.succeed(0)
      val nextLong: UIO[Long] =
        UIO.succeed(0L)
      val nextPrintableChar: UIO[Char] =
        UIO.succeed('A')
      def nextString(length: Int): UIO[String] =
        UIO.succeed("")
    }
  }
}
