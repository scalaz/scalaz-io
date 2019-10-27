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

import zio.{ Cause, Managed, ZIO }

import Spec._

/**
 * A `Spec[R, E, L, T]` is the backbone of _ZIO Test_. Every spec is either a
 * suite, which contains other specs, or a test of type `T`. All specs are
 * annotated with labels of type `L`, require an environment of type `R` and
 * may potentially fail with an error of type `E`.
 */
final case class Spec[-R, +E, +L, +T](caseValue: SpecCase[R, E, L, T, Spec[R, E, L, T]]) { self =>

  /**
   * Syntax for adding aspects.
   * {{{
   * test("foo") { assert(42, equalTo(42)) } @@ ignore
   * }}}
   */
  final def @@[R0 <: R1, R1 <: R, E0 >: E, E1 >: E0, S0 <: S, S, S1 >: S](aspect: TestAspect[R0, R1, E0, E1, S0, S1])(
    implicit ev: T <:< Either[TestFailure[Nothing], TestSuccess[S]]
  ): Spec[R1, E0, L, Either[TestFailure[Nothing], TestSuccess[S]]] =
    aspect(mapTest(ev))

  /**
   * Returns a new spec with the suite labels distinguished by `Left`, and the
   * test labels distinguished by `Right`.
   */
  final def distinguish: Spec[R, E, Either[L, L], T] =
    transform[R, E, Either[L, L], T] {
      case SuiteCase(label, specs, exec) => SuiteCase(Left(label), specs, exec)
      case TestCase(label, test)         => TestCase(Right(label), test)
    }

  /**
   * Determines if any node in the spec is satisfied by the given predicate.
   */
  final def exists[R1 <: R, E1 >: E](f: SpecCase[R, E, L, T, Any] => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Boolean] =
    fold[ZIO[R1, E1, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.exists(identity))).zipWith(f(c))(_ || _)
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Returns a new Spec containing only tests with labels satisfying the specified predicate.
   */
  final def filterTestLabels(f: L => Boolean): Option[Spec[R, E, L, T]] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        val filtered = SuiteCase(label, specs.map(_.flatMap(_.filterTestLabels(f))), exec)
        Some(Spec(filtered))

      case t @ TestCase(label, _) =>
        if (f(label)) Some(Spec(t)) else None
    }

  /**
   * Returns a new Spec containing only tests/suites with labels satisfying the specified predicate.
   */
  final def filterLabels(f: L => Boolean): Option[Spec[R, E, L, T]] =
    caseValue match {
      case s @ SuiteCase(label, specs, exec) =>
        // If the suite matched the label, no need to filter anything underneath it.
        if (f(label)) {
          Some(Spec(s))
        } else {
          val filtered = SuiteCase(label, specs.map(_.flatMap(_.filterLabels(f))), exec)
          Some(Spec(filtered))
        }

      case t @ TestCase(label, _) =>
        if (f(label)) Some(Spec(t)) else None
    }

  /**
   * Folds over all nodes to produce a final result.
   */
  final def fold[Z](f: SpecCase[R, E, L, T, Z] => Z): Z =
    caseValue match {
      case SuiteCase(label, specs, exec) => f(SuiteCase(label, specs.map(_.map(_.fold(f)).toVector), exec))
      case t @ TestCase(_, _)            => f(t)
    }

  /**
   * Effectfully folds over all nodes according to the execution strategy of
   * suites, utilizing the specified default for other cases.
   */
  final def foldM[R1 <: R, E1, Z](
    defExec: ExecutionStrategy
  )(f: SpecCase[R, E, L, T, Z] => ZIO[R1, E1, Z]): ZIO[R1, E1, Z] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        exec.getOrElse(defExec) match {
          case ExecutionStrategy.Parallel =>
            specs.foldM(
              e => f(SuiteCase(label, ZIO.fail(e), exec)),
              ZIO
                .foreachPar(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
          case ExecutionStrategy.ParallelN(n) =>
            specs.foldM(
              e => f(SuiteCase(label, ZIO.fail(e), exec)),
              ZIO
                .foreachParN(n)(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
          case ExecutionStrategy.Sequential =>
            specs.foldM(
              e => f(SuiteCase(label, ZIO.fail(e), exec)),
              ZIO
                .foreach(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
        }

      case t @ TestCase(_, _) => f(t)
    }

  /**
   * Determines if all node in the spec are satisfied by the given predicate.
   */
  final def forall[R1 <: R, E1 >: E](f: SpecCase[R, E, L, T, Any] => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Boolean] =
    fold[ZIO[R1, E1, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.forall(identity))).zipWith(f(c))(_ && _)
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Iterates over the spec with the specified default execution strategy, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachExec[R1 <: R, E1, A](
    defExec: ExecutionStrategy
  )(failure: Cause[E] => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foldM[R1, Nothing, Spec[R1, E1, L, A]](defExec) {
      case SuiteCase(label, specs, exec) =>
        specs.foldCause(e => Spec.test(label, failure(e)), t => Spec.suite(label, ZIO.succeed(t), exec))
      case TestCase(label, test) =>
        test.foldCause(e => Spec.test(label, failure(e)), t => Spec.test(label, success(t)))
    }

  /**
   * Iterates over the spec with the sequential strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreach[R1 <: R, E1, A](
    failure: Cause[E] => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.Sequential)(failure, success)

  /**
   * Iterates over the spec with the parallel strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachPar[R1 <: R, E1, A](
    failure: Cause[E] => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.Parallel)(failure, success)

  /**
   * Iterates over the spec with the parallel (`n`) strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachParN[R1 <: R, E1, A](
    n: Int
  )(failure: Cause[E] => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.ParallelN(n))(failure, success)

  /**
   * Returns a new spec with remapped labels.
   */
  final def mapLabel[L1](f: L => L1): Spec[R, E, L1, T] =
    transform[R, E, L1, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(f(label), specs, exec)
      case TestCase(label, test)         => TestCase(f(label), test)
    }

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTest[T1](f: T => T1): Spec[R, E, L, T1] =
    transform[R, E, L, T1] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs, exec)
      case TestCase(label, test)         => TestCase(label, test.map(f))
    }

  /**
   * Uses the specified `Managed` to provide each test in this spec with its
   * required environment.
   */
  final def provideManaged[E1 >: E](managed: Managed[E1, R]): Spec[Any, E1, L, T] =
    transform[Any, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provideManaged(managed), exec)
      case TestCase(label, test)         => TestCase(label, test.provideManaged(managed))
    }

  /**
   * Uses the specified `Managed` once to provide all tests in this spec with
   * a shared version of their required environment. This is useful when the
   * act of creating the environment is expensive and should only be performed
   * once.
   */
  final def provideManagedShared[E1 >: E](managed: Managed[E1, R]): Spec[Any, E1, L, T] = {
    def loop(r: R)(spec: Spec[R, E, L, T]): ZIO[Any, E, Spec[Any, E, L, T]] =
      spec.caseValue match {
        case SuiteCase(label, specs, exec) =>
          specs.flatMap(ZIO.foreach(_)(loop(r))).map(z => Spec.suite(label, ZIO.succeed(z.toVector), exec)).provide(r)
        case TestCase(label, test) =>
          test.map(t => Spec.test(label, ZIO.succeed(t))).provide(r)
      }
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec.suite(label, managed.use(r => specs.flatMap(ZIO.foreach(_)(loop(r))).map(_.toVector).provide(r)), exec)
      case TestCase(label, test) =>
        Spec.test(label, test.provideManaged(managed))
    }
  }

  /**
   * Computes the size of the spec, i.e. the number of tests in the spec.
   */
  final def size: ZIO[R, E, Int] =
    fold[ZIO[R, E, Int]] {
      case SuiteCase(_, counts, _) => counts.flatMap(ZIO.collectAll(_).map(_.sum))
      case TestCase(_, _)          => ZIO.succeed(1)
    }

  /**
   * Transforms the spec one layer at a time.
   */
  final def transform[R1, E1, L1, T1](
    f: SpecCase[R, E, L, T, Spec[R1, E1, L1, T1]] => SpecCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]]
  ): Spec[R1, E1, L1, T1] =
    caseValue match {
      case SuiteCase(label, specs, exec) => Spec(f(SuiteCase(label, specs.map(_.map(_.transform(f))), exec)))
      case t @ TestCase(_, _)            => Spec(f(t))
    }

  final def mapTests[R1, E1, L1 >: L, T1](
    suiteCase: ZIO[R, E, Vector[Spec[R1, E1, L1, T1]]] => ZIO[R1, E1, Vector[Spec[R1, E1, L1, T1]]],
    testCase: ZIO[R, E, T] => ZIO[R1, E1, T1]
  ): Spec[R1, E1, L1, T1] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec.suite(label, suiteCase(specs.map(_.map(_.mapTests(suiteCase, testCase)))), exec)
      case TestCase(label, test) => Spec.test(label, testCase(test))
    }

  /**
   * Transforms the spec statefully, one layer at a time.
   */
  final def transformAccum[R1, E1, L1, T1, Z](
    z0: Z
  )(
    f: (Z, SpecCase[R, E, L, T, Spec[R1, E1, L1, T1]]) => (Z, SpecCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]])
  ): ZIO[R, E, (Z, Spec[R1, E1, L1, T1])] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        for {
          specs <- specs
          result <- ZIO.foldLeft(specs)(z0 -> Vector.empty[Spec[R1, E1, L1, T1]]) {
                     case ((z, vector), spec) =>
                       spec.transformAccum(z)(f).map { case (z1, spec1) => z1 -> (vector :+ spec1) }
                   }
          (z, specs1)     = result
          res             = f(z, SuiteCase(label, ZIO.succeed(specs1), exec))
          (z1, caseValue) = res
        } yield z1 -> Spec(caseValue)
      case t @ TestCase(_, _) =>
        val (z, caseValue) = f(z0, t)
        ZIO.succeed(z -> Spec(caseValue))
    }
}

object Spec {
  sealed trait SpecCase[-R, +E, +L, +T, +A] { self =>
    final def map[B](f: A => B): SpecCase[R, E, L, T, B] = self match {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.map(_.map(f)), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }
  }
  final case class SuiteCase[-R, +E, +L, +A](label: L, specs: ZIO[R, E, Vector[A]], exec: Option[ExecutionStrategy])
      extends SpecCase[R, E, L, Nothing, A]
  final case class TestCase[-R, +E, +L, +T](label: L, test: ZIO[R, E, T]) extends SpecCase[R, E, L, T, Nothing]

  final def suite[R, E, L, T](
    label: L,
    specs: ZIO[R, E, Vector[Spec[R, E, L, T]]],
    exec: Option[ExecutionStrategy]
  ): Spec[R, E, L, T] =
    Spec(SuiteCase(label, specs, exec))

  final def test[R, E, L, T](label: L, test: ZIO[R, E, T]): Spec[R, E, L, T] =
    Spec(TestCase(label, test))
}
