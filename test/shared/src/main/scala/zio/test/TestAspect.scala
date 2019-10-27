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

import zio.{ clock, Cause, ZIO, ZManaged, ZSchedule }
import zio.duration._
import zio.clock.Clock
import zio.system
import zio.system.System
import zio.test.environment.Live

/**
 * A `TestAspect` is an aspect that can be weaved into specs. You can think of
 * an aspect as a polymorphic function, capable of transforming one test into
 * another, possibly enlarging the environment, error, or success type.
 */
trait TestAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerS, -UpperS] { self =>

  /**
   * Applies the aspect to some tests in the spec, chosen by the provided
   * predicate.
   */
  def some[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
    predicate: L => Boolean,
    spec: ZSpec[R, E, L, S]
  ): ZSpec[R, E, L, S]

  /**
   * An alias for [[all]].
   */
  final def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
    spec: ZSpec[R, E, L, S]
  ): ZSpec[R, E, L, S] =
    all(spec)

  /**
   * Applies the aspect to every test in the spec.
   */
  final def all[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
    spec: ZSpec[R, E, L, S]
  ): ZSpec[R, E, L, S] =
    some[R, E, S, L](_ => true, spec)

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one.
   */
  final def >>>[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerS1 >: LowerS,
    UpperS1 <: UpperS
  ](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1] =
    new TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1] {
      def some[R >: LowerR1 <: UpperR1, E >: LowerE1 <: UpperE1, S >: LowerS1 <: UpperS1, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] =
        that.some(predicate, self.some(predicate, spec))
    }

  final def andThen[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerS1 >: LowerS,
    UpperS1 <: UpperS
  ](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1] =
    self >>> that
}
object TestAspect extends TimeoutVariants {

  /**
   * Constructs an aspect that runs the specified effect after every test.
   */
  def after[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect[Nothing, R0, E0, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any, Nothing, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        test <* effect
    }

  /**
   * Constructs an aspect that evaluates every test inside the context of a `Managed`.
   */
  def around[R0, E0](before: ZIO[R0, E0, Any], after: ZIO[R0, Nothing, Any]) =
    new TestAspect.PerTest[Nothing, R0, E0, Any, Nothing, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: Nothing <: Any](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        ZManaged.make(before)(_ => after).use(_ => test)
    }

  /**
   * Constructs an aspect that evaluates every test inside the context of the managed function.
   */
  def aroundTest[R0, E0, S0](
    managed: ZManaged[
      R0,
      E0,
      Either[TestFailure[Nothing], TestSuccess[S0]] => ZIO[R0, E0, Either[TestFailure[Nothing], TestSuccess[S0]]]
    ]
  ) =
    new TestAspect.PerTest[Nothing, R0, E0, Any, S0, S0] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: S0 <: S0](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        managed.use(f => test.flatMap(f))
    }

  /**
   * Constucts a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R0, E0, S0](
    f: ZIO[R0, E0, Either[TestFailure[Nothing], TestSuccess[S0]]] => ZIO[
      R0,
      E0,
      Either[TestFailure[Nothing], TestSuccess[S0]]
    ]
  ): TestAspect[R0, R0, E0, E0, S0, S0] =
    new TestAspect.PerTest[R0, R0, E0, E0, S0, S0] {
      def perTest[R >: R0 <: R0, E >: E0 <: E0, S >: S0 <: S0](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        f(test)
    }

  /**
   * Constructs an aspect that runs the specified effect before every test.
   */
  def before[R0, E0, S0](effect: ZIO[R0, Nothing, Any]): TestAspect[Nothing, R0, E0, Any, S0, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any, S0, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: S0 <: Any](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        effect *> test
    }

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] = {
        lazy val untilSuccess: ZIO[R, Nothing, Either[TestFailure[Nothing], TestSuccess[S]]] =
          test.foldM(_ => untilSuccess, _.fold(_ => untilSuccess, s => ZIO.succeed(Right(s))))

        untilSuccess
      }
    }

  /**
   * An aspect that sets suites to the specified execution strategy, but only
   * if their current strategy is inherited (undefined).
   */
  def executionStrategy(exec: ExecutionStrategy): TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def some[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] = spec.transform[R, E, L, Either[TestFailure[Nothing], TestSuccess[S]]] {
        case Spec.SuiteCase(label, specs, None) if (predicate(label)) => Spec.SuiteCase(label, specs, Some(exec))
        case c                                                        => c
      }
    }

  /**
   * An aspect that makes a test that failed for any reason pass. Note that if the test
   * passes this aspect will make it fail.
   */
  val failure: PerTest[Nothing, Any, Nothing, Any, Unit, Unit] = failure(Assertion.anything)

  /**
   * An aspect that makes a test that failed for the specified failure pass.  Note that the
   * test will fail for other failures and also if it passes correctly.
   */
  def failure[E0](p: Assertion[TestFailure[E0]]): PerTest[Nothing, Any, Nothing, E0, Unit, Unit] =
    new TestAspect.PerTest[Nothing, Any, Nothing, E0, Unit, Unit] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: E0, S >: Unit <: Unit](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] = {
        lazy val succeed = Right(TestSuccess.Succeeded(BoolAlgebra.unit))
        test.foldCause(
          {
            case c if p.run(TestFailure.Runtime(c)).isSuccess => succeed
            case other                                        => Left(TestFailure.Assertion(assert(TestFailure.Runtime(other), p)))
          }, {
            case Left(e) => if (p.run(e).isSuccess) succeed else Left(TestFailure.Assertion(assert(e, p)))
            case _       => Left(TestFailure.Runtime(Cause.die(new RuntimeException("did not fail as expected"))))
          }
        )
      }
    }

  /**
   * An aspect that retries a test until success, without limit, for use with
   * flaky tests.
   */
  val flaky: TestAspectPoly = eventually

  /**
   * An aspect that returns the tests unchanged
   */
  val identity: TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def some[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] = spec
    }

  /**
   * An aspect that only runs a test if the specified environment variable
   * satisfies the specified assertion.
   */
  def ifEnv(env: String, assertion: Assertion[String]): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[System], Nothing, Any, Nothing, Any] {
      def perTest[R <: Live[System], E, S](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        Live.live(system.env(env)).orDie.flatMap { value =>
          value
            .filter(assertion.test)
            .fold[ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]](ZIO.succeed(Right(TestSuccess.Ignored)))(
              _ => test
            )
        }
    }

  /**
   * As aspect that only runs a test if the specified environment variable is
   * set.
   */
  def ifEnvSet(env: String): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    ifEnv(env, Assertion.anything)

  /**
   * An aspect that only runs a test if the specified Java property satisfies
   * the specified assertion.
   */
  def ifProp(
    prop: String,
    assertion: Assertion[String]
  ): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[System], Nothing, Any, Nothing, Any] {
      def perTest[R <: Live[System], E, S](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        Live.live(system.property(prop)).orDie.flatMap { value =>
          value
            .filter(assertion.test)
            .fold[ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]](ZIO.succeed(Right(TestSuccess.Ignored)))(
              _ => test
            )
        }
    }

  /**
   * As aspect that only runs a test if the specified Java property is set.
   */
  def ifPropSet(prop: String): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    ifProp(prop, Assertion.anything)

  /**
   * An aspect that marks tests as ignored.
   */
  val ignore: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        ZIO.succeed(Right(TestSuccess.Ignored))
    }

  def js[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] =
    if (TestPlatform.isJS) that else identity

  /**
   * An aspect that only runs tests on ScalaJS.
   */
  val jsOnly: TestAspectPoly =
    if (TestPlatform.isJS) identity else ignore

  def jvm[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] =
    if (TestPlatform.isJVM) that else identity

  /**
   * An aspect that only runs tests on the JVM.
   */
  val jvmOnly: TestAspectPoly =
    if (TestPlatform.isJVM) identity else ignore

  /**
   * An aspect that repeats the test a specified number of times, ensuring it
   * is stable ("non-flaky"). Stops at the first failure.
   */
  def nonFlaky(n0: Int): TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] = {
        def repeat(n: Int): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
          if (n <= 1) test
          else test.flatMap(_.fold(e => ZIO.succeed(Left(e)), _ => repeat(n - 1)))

        repeat(n0)
      }
    }

  /**
   * An aspect that executes the members of a suite in parallel.
   */
  val parallel: TestAspectPoly = executionStrategy(ExecutionStrategy.Parallel)

  /**
   * An aspect that executes the members of a suite in parallel, up to the
   * specified number of concurent fibers.
   */
  def parallelN(n: Int): TestAspectPoly = executionStrategy(ExecutionStrategy.ParallelN(n))

  /**
   * An aspect that retries failed tests according to a schedule.
   */
  def retry[R0, E0, S0](
    schedule: ZSchedule[R0, TestFailure[E0], S0]
  ): TestAspect[Nothing, R0 with Live[Clock], Nothing, E0, Nothing, S0] =
    new TestAspect.PerTest[Nothing, R0 with Live[Clock], Nothing, E0, Nothing, S0] {
      def perTest[R >: Nothing <: R0 with Live[Clock], E >: Nothing <: E0, S >: Nothing <: S0](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] = {
        def loop(state: schedule.State): ZIO[R with Live[Clock], E, Either[TestFailure[Nothing], TestSuccess[S]]] =
          test.foldCauseM(
            err =>
              schedule
                .update(TestFailure.Runtime(err), state)
                .flatMap(
                  decision =>
                    if (decision.cont) Live.live(clock.sleep(decision.delay)) *> loop(decision.state)
                    else ZIO.halt(err)
                ), {
              case Left(e) =>
                schedule
                  .update(e, state)
                  .flatMap(
                    decision =>
                      if (decision.cont) Live.live(clock.sleep(decision.delay)) *> loop(decision.state)
                      else ZIO.succeed(Left(e))
                  )
              case Right(s) => ZIO.succeed(Right(s))
            }
          )

        schedule.initial.flatMap(loop)
      }
    }

  /**
   * An aspect that executes the members of a suite sequentially.
   */
  val sequential: TestAspectPoly = executionStrategy(ExecutionStrategy.Sequential)

  /**
   * An aspect that converts ignored tests into test failures.
   */
  val success: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] =
        test.map {
          case Right(TestSuccess.Ignored) =>
            Left(TestFailure.Runtime(Cause.die(new RuntimeException("Test was ignored."))))
          case x => x
        }
    }

  /**
   * An aspect that times out tests using the specified duration.
   * @param duration maximum test duration
   * @param interruptDuration after test timeout will wait given duration for successful interruption
   */
  def timeout(
    duration: Duration,
    interruptDuration: Duration = 1.second
  ): TestAspect[Nothing, Live[Clock], Nothing, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[Clock], Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Live[Clock], E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
      ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]] = {
        def timeoutFailure =
          TestTimeoutException(s"Timeout of ${duration.render} exceeded.")
        def interruptionTimeoutFailure = {
          val msg =
            s"Timeout of ${duration.render} exceeded. Couldn't interrupt test within ${interruptDuration.render}, possible resource leak!"
          TestTimeoutException(msg)
        }
        Live
          .withLive(test)(_.either.timeoutFork(duration).flatMap {
            case Left(fiber) =>
              fiber.join.raceWith(ZIO.sleep(interruptDuration))(
                (_, fiber) => fiber.interrupt *> ZIO.die(timeoutFailure),
                (_, _) => ZIO.die(interruptionTimeoutFailure)
              )
            case Right(result) => result.fold(ZIO.fail, ZIO.succeed)
          })
      }
    }

  trait PerTest[+LowerR, -UpperR, +LowerE, -UpperE, +LowerS, -UpperS]
      extends TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] {
    def perTest[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS](
      test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]
    ): ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]

    final def some[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
      predicate: L => Boolean,
      spec: ZSpec[R, E, L, S]
    ): ZSpec[R, E, L, S] =
      spec.transform[R, E, L, Either[TestFailure[Nothing], TestSuccess[S]]] {
        case c @ Spec.SuiteCase(_, _, _) => c
        case Spec.TestCase(label, test) =>
          Spec.TestCase(label, if (predicate(label)) perTest(test) else test)
      }
  }
}
