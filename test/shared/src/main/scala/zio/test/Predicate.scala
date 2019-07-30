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

import zio.Exit

/**
 * A `Predicate[A]` is capable of producing assertion results on an `A`. As a
 * proposition, predicates compose using logical conjuction and disjunction,
 * and can be negated.
 */
class Predicate[-A] private (render: String, val run: A => PredicateResult) extends (A => PredicateResult) { self =>
  import AssertResult._

  /**
   * Returns a new predicate that succeeds only if both predicates succeed.
   */
  final def &&[A1 <: A](that: => Predicate[A1]): Predicate[A1] =
    Predicate.predicateDirect(s"(${self} && ${that})") { actual =>
      self.run(actual) match {
        case Failure(l) => Failure(l)
        case Success(_) => that.run(actual)
        case Ignore     => that.run(actual)
      }
    }

  /**
   * Returns a new predicate that succeeds if either predicates succeed.
   */
  final def ||[A1 <: A](that: => Predicate[A1]): Predicate[A1] =
    Predicate.predicateDirect(s"(${self} || ${that})") { actual =>
      self.run(actual) match {
        case Failure(_) => that.run(actual)
        case Success(l) => Success(l)
        case Ignore     => that.run(actual)
      }
    }

  /**
   * Evaluates the predicate with the specified value.
   */
  final def apply(a: A): PredicateResult = run(a)

  override final def equals(that: Any): Boolean = that match {
    case that: Predicate[_] => this.toString == that.toString
  }

  override final def hashCode: Int = toString.hashCode

  /**
   * Returns the negation of this predicate.
   */
  final def negate: Predicate[A] = Predicate.not(self)

  /**
   * Tests the predicate to see if it would succeed on the given element.
   */
  final def test(a: A): Boolean = run(a) match {
    case Success(_) => true
    case _          => false
  }

  /**
   * Provides a meaningful string rendering of the predicate.
   */
  override final def toString: String = render
}

object Predicate {

  /**
   * Makes a new predicate that always succeeds.
   */
  final val anything: Predicate[Any] = Predicate.predicateRec[Any]("anything") { (self, actual) =>
    AssertResult.success(PredicateValue(self, actual))
  }

  /**
   * Makes a new predicate that requires an iterable contain the specified
   * element.
   */
  final def contains[A](element: A): Predicate[Iterable[A]] =
    Predicate.predicate(s"contains(${element})") { actual =>
      if (!actual.exists(_ == element)) AssertResult.failureUnit
      else AssertResult.successUnit
    }

  /**
   * Makes a new predicate that requires a value equal the specified value.
   */
  final def equals[A](expected: A): Predicate[A] =
    Predicate.predicate(s"equals(${expected})") { actual =>
      if (actual == expected) AssertResult.successUnit
      else AssertResult.failureUnit
    }

  /**
   * Makes a new predicate that requires an iterable contain one element
   * satisfying the given predicate.
   */
  final def exists[A](predicate: Predicate[A]): Predicate[Iterable[A]] =
    Predicate.predicate(s"exists(${predicate})") { actual =>
      if (!actual.exists(predicate.test(_))) AssertResult.failureUnit
      else AssertResult.successUnit
    }

  /**
   * Makes a new predicate that requires an exit value to fail.
   */
  final def fails[E](predicate: Predicate[E]): Predicate[Exit[E, Any]] =
    Predicate.predicateRec[Exit[E, Any]](s"fails(${predicate})") { (self, actual) =>
      actual match {
        case Exit.Failure(cause) if cause.failures.length > 0 => predicate.run(cause.failures.head)

        case _ => AssertResult.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that focuses in on a field in a case class.
   *
   * {{{
   * field("age", _.age, within(0, 10))
   * }}}
   */
  final def field[A, B](name: String, proj: A => B, predicate: Predicate[B]): Predicate[A] =
    Predicate.predicateDirect[A]("field(\"" + name + "\"" + s", _.${name}, ${predicate})") { actual =>
      predicate(proj(actual))
    }

  /**
   * Makes a new predicate that requires an iterable contain only elements
   * satisfying the given predicate.
   */
  final def forall[A](predicate: Predicate[A]): Predicate[Iterable[A]] =
    Predicate.predicateRec[Iterable[A]](s"forall(${predicate})") { (self, actual) =>
      actual.map(predicate(_)).toList match {
        case head :: tail =>
          tail.foldLeft(head) {
            case (AssertResult.Success(_), next) => next
            case (acc, _)                        => acc
          }
        case Nil => AssertResult.success(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def gt[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"gt(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) > 0) AssertResult.successUnit
      else AssertResult.failureUnit
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * or equal to the specified reference value.
   */
  final def gte[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"gte(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) >= 0) AssertResult.successUnit
      else AssertResult.failureUnit
    }

  /**
   * Makes a new predicate that requires the sum type be a specified term.
   *
   * {{{
   * isCase("Some", Some.unapply, anything)
   * }}}
   */
  final def isCase[Sum, Proj](
    termName: String,
    term: Sum => Option[Proj],
    predicate: Predicate[Proj]
  ): Predicate[Sum] =
    Predicate.predicateRec[Sum]("isCase(\"" + termName + "\", " + s"${termName}.unapply, ${predicate})") {
      (self, actual) =>
        term(actual).fold(AssertResult.failure(PredicateValue(self, actual)))(predicate)
    }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isTrue: Predicate[Boolean] = Predicate.predicate(s"isTrue") { actual =>
    if (actual) AssertResult.successUnit else AssertResult.failureUnit
  }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isFalse: Predicate[Boolean] = Predicate.predicate(s"isFalse") { actual =>
    if (!actual) AssertResult.successUnit else AssertResult.failureUnit
  }

  /**
   * Makes a new predicate that requires a Left value satisfying a specified
   * predicate.
   */
  final def left[A](predicate: Predicate[A]): Predicate[Either[A, Nothing]] =
    Predicate.predicateRec[Either[A, Nothing]](s"left(${predicate})") { (self, actual) =>
      actual match {
        case Left(a)  => predicate.run(a)
        case Right(_) => AssertResult.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def lt[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"lt(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) < 0) AssertResult.successUnit
      else AssertResult.failureUnit
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def lte[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"lte(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) <= 0) AssertResult.successUnit
      else AssertResult.failureUnit
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final val none: Predicate[Option[Any]] = Predicate.predicate(s"none") { actual =>
    actual match {
      case None    => AssertResult.successUnit
      case Some(_) => AssertResult.failureUnit
    }
  }

  /**
   * Makes a new predicate that negates the specified predicate.
   */
  final def not[A](predicate: Predicate[A]): Predicate[A] =
    Predicate.predicate(s"not(${predicate})")(actual => predicate.run(actual).negate(_ => ()))

  /**
   * Makes a new predicate that always fails.
   */
  final val nothing: Predicate[Any] = Predicate.predicateRec[Any]("nothing") { (self, actual) =>
    AssertResult.failure(PredicateValue(self, actual))
  }

  /**
   * Makes a new `Predicate` from a pretty-printing and a function.
   */
  final def predicate[A](render: String)(run: A => AssertResult[Unit]): Predicate[A] =
    predicateRec[A](render)((predicate, a) => run(a).map(_ => PredicateValue(predicate, a)))

  /**
   * Makes a new `Predicate` from a pretty-printing and a function.
   */
  final def predicateDirect[A](render: String)(run: A => PredicateResult): Predicate[A] =
    new Predicate(render, run)

  /**
   * Makes a new `Predicate` from a pretty-printing and a function, passing
   * the predicate itself to the specified function, so it can embed a
   * recursive reference into the assert result.
   */
  final def predicateRec[A](render: String)(run: (Predicate[A], A) => PredicateResult): Predicate[A] = {
    lazy val predicate: Predicate[A] = predicateDirect[A](render)((a: A) => run(predicate, a))

    predicate
  }

  /**
   * Makes a new predicate that requires a Right value satisfying a specified
   * predicate.
   */
  final def right[A](predicate: Predicate[A]): Predicate[Either[Nothing, A]] =
    Predicate.predicateRec[Either[Nothing, A]](s"right(${predicate})") { (self, actual) =>
      actual match {
        case Right(a) => predicate.run(a)
        case Left(_)  => AssertResult.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final def some[A](predicate: Predicate[A]): Predicate[Option[A]] =
    Predicate.predicateRec[Option[A]](s"some(${predicate}") { (self, actual) =>
      actual match {
        case Some(a) => predicate.run(a)
        case None    => AssertResult.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that requires an exit value to succeed.
   */
  final def succeeds[A](predicate: Predicate[A]): Predicate[Exit[Any, A]] =
    Predicate.predicateRec[Exit[Any, A]](s"succeeds(${predicate})") { (self, actual) =>
      actual match {
        case Exit.Success(a) => predicate.run(a)

        case exit => AssertResult.failure(PredicateValue(self, exit))
      }
    }

  /**
   * Returns a new predicate that requires a numeric value to fall within a
   * specified min and max (inclusive).
   */
  final def within[A: Numeric](min: A, max: A): Predicate[A] =
    Predicate.predicate(s"within(${min}, ${max})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, min) < 0) AssertResult.failureUnit
      else if (implicitly[Numeric[A]].compare(actual, max) > 0) AssertResult.failureUnit
      else AssertResult.successUnit
    }
}
