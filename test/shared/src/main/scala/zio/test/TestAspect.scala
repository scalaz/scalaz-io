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

import java.util.concurrent.TimeoutException

import zio.{ clock, Cause, ZIO, ZManaged, ZSchedule }
import zio.duration.Duration
import zio.clock.Clock
import zio.test.mock.Live

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
object TestAspect {

  /**
   * Constructs an aspect that runs the specified effect after every test.
   */
  def after[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect[Nothing, R0, E0, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any, Nothing, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        test <* effect.catchAllCause(cause => ZIO.fail(TestFailure.Runtime(cause)))
    }

  /**
   * Constructs an aspect that evaluates every test inside the context of the managed function.
   */
  def around[R0, E0, S0](
    managed: ZManaged[R0, TestFailure[E0], TestSuccess[S0] => ZIO[R0, TestFailure[E0], TestSuccess[S0]]]
  ) =
    new TestAspect.PerTest[Nothing, R0, E0, Any, S0, S0] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: S0 <: S0](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        managed.use(f => test.flatMap(f))
    }

  /**
   * Constucts a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R0, E0, S0](
    f: ZIO[R0, TestFailure[E0], TestSuccess[S0]] => ZIO[R0, TestFailure[E0], TestSuccess[S0]]
  ): TestAspect[R0, R0, E0, E0, S0, S0] =
    new TestAspect.PerTest[R0, R0, E0, E0, S0, S0] {
      def perTest[R >: R0 <: R0, E >: E0 <: E0, S >: S0 <: S0](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        f(test)
    }

  /**
   * Constructs an aspect that runs the specified effect before every test.
   */
  def before[R0, E0, S0](effect: ZIO[R0, Nothing, Any]): TestAspect[Nothing, R0, E0, Any, S0, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any, S0, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: S0 <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        effect *> test
    }

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, Nothing, TestSuccess[S]] = {
        lazy val untilSuccess: ZIO[R, Nothing, TestSuccess[S]] =
          test.foldM(_ => untilSuccess, ZIO.succeed(_))

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
      ): ZSpec[R, E, L, S] = spec.transform[L, ZIO[R, TestFailure[E], TestSuccess[S]]] {
        case Spec.SuiteCase(label, specs, None) if (predicate(label)) => Spec.SuiteCase(label, specs, Some(exec))
        case c                                                        => c
      }
    }

  /**
   * An aspect that retries a test until success, without limit, for use with
   * flaky tests.
   */
  val flaky: TestAspectPoly = eventually

  /**
   * An aspect that repeats the test a specified number of times, ensuring it
   * is stable ("non-flaky"). Stops at the first failure.
   */
  def nonFlaky(n0: Int): TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] = {
        def repeat(n: Int): ZIO[R, TestFailure[E], TestSuccess[S]] =
          if (n <= 1) test
          else test.flatMap(_ => repeat(n - 1))

        repeat(n0)
      }
    }

  /**
   * An aspect that marks tests as ignored.
   */
  val ignore: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        ZIO.succeed(TestSuccess.Ignored)
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
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] = {
        def loop(state: schedule.State): ZIO[R with Live[Clock], TestFailure[E], TestSuccess[S]] =
          test.foldM(
            err =>
              schedule
                .update(err, state)
                .flatMap(
                  decision =>
                    if (decision.cont) Live.live(clock.sleep(decision.delay)) *> loop(decision.state)
                    else ZIO.fail(err)
                ),
            succ => ZIO.succeed(succ)
          )

        schedule.initial.flatMap(loop)
      }
    }

  /**
   * An aspect that executes the members of a suite sequentially.
   */
  val sequential: TestAspectPoly = executionStrategy(ExecutionStrategy.Sequential)

  /**
   * An aspect that times out tests using the specified duration.
   */
  def timeout(duration: Duration): TestAspect[Nothing, Live[Clock], Nothing, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[Clock], Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Live[Clock], E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        Live.withLive(test)(_.timeout(duration)).flatMap {
          case None =>
            ZIO.fail(TestFailure.Runtime(Cause.die(new TimeoutException(s"Timeout of ${duration} exceeded"))))
          case Some(v) => ZIO.succeed(v)
        }
    }

  trait PerTest[+LowerR, -UpperR, +LowerE, -UpperE, +LowerS, -UpperS]
      extends TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] {
    def perTest[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS](
      test: ZIO[R, TestFailure[E], TestSuccess[S]]
    ): ZIO[R, TestFailure[E], TestSuccess[S]]

    final def some[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
      predicate: L => Boolean,
      spec: ZSpec[R, E, L, S]
    ): ZSpec[R, E, L, S] =
      spec.transform[L, ZIO[R, TestFailure[E], TestSuccess[S]]] {
        case c @ Spec.SuiteCase(_, _, _) => c
        case Spec.TestCase(label, test)  => Spec.TestCase(label, if (predicate(label)) perTest(test) else test)
      }
  }
}
