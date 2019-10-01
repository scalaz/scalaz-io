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

package zio

sealed trait Cause[+E] extends Product with Serializable { self =>
  import Cause._

  final def &&[E1 >: E](that: Cause[E1]): Cause[E1] = Both(self, that)

  final def ++[E1 >: E](that: Cause[E1]): Cause[E1] = Then(self, that)

  final def defects: List[Throwable] =
    self
      .foldLeft(List.empty[Throwable]) {
        case (z, Die(v)) => v :: z
      }
      .reverse

  final def died: Boolean =
    self match {
      case Die(_)            => true
      case Then(left, right) => left.died || right.died
      case Both(left, right) => left.died || right.died
      case Traced(cause, _)  => cause.died
      case Meta(cause, _)    => cause.died
      case _                 => false
    }

  /**
   * Returns the `Throwable` associated with the first `Die` in this `Cause` if
   * one exists.
   */
  final def dieOption: Option[Throwable] =
    fold(failCase = _ => None, dieCase = t => Some(t), interruptCase = None)(
      thenCase = _ orElse _,
      bothCase = _ orElse _,
      tracedCase = (z, _) => z
    )

  final def failed: Boolean =
    self match {
      case Fail(_)           => true
      case Then(left, right) => left.failed || right.failed
      case Both(left, right) => left.failed || right.failed
      case Traced(cause, _)  => cause.failed
      case Meta(cause, _)    => cause.failed
      case _                 => false
    }

  /**
   * Retrieve the first checked error on the `Left` if available,
   * if there are no checked errors return the rest of the `Cause`
   * that is known to contain only `Die` or `Interrupt` causes.
   * */
  final def failureOrCause: Either[E, Cause[Nothing]] = self.failures.headOption match {
    case Some(error) => Left(error)
    case None        => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
  }

  final def failures[E1 >: E]: List[E1] =
    self
      .foldLeft(List.empty[E1]) {
        case (z, Fail(v)) => v :: z
      }
      .reverse

  final def flatMap[E1](f: E => Cause[E1]): Cause[E1] = self match {
    case Fail(value)          => f(value)
    case c @ Die(_)           => c
    case Interrupt            => Interrupt
    case Then(left, right)    => Then(left.flatMap(f), right.flatMap(f))
    case Both(left, right)    => Both(left.flatMap(f), right.flatMap(f))
    case Traced(cause, trace) => Traced(cause.flatMap(f), trace)
    case Meta(cause, data)    => Meta(cause.flatMap(f), data)
  }

  final def flatten[E1](implicit ev: E <:< Cause[E1]): Cause[E1] =
    flatMap(e => e)

  final def fold[Z](
    failCase: E => Z,
    dieCase: Throwable => Z,
    interruptCase: => Z
  )(thenCase: (Z, Z) => Z, bothCase: (Z, Z) => Z, tracedCase: (Z, ZTrace) => Z): Z =
    self match {
      case Fail(value) =>
        failCase(value)
      case Die(value) =>
        dieCase(value)
      case Interrupt =>
        interruptCase
      case Then(left, right) =>
        thenCase(
          left.fold(failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase),
          right.fold(failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase)
        )
      case Both(left, right) =>
        bothCase(
          left.fold(failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase),
          right.fold(failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase)
        )
      case Traced(cause, trace) =>
        tracedCase(
          cause.fold(failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase),
          trace
        )
      case Meta(cause, _) =>
        cause.fold(failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase)
    }

  final def interrupted: Boolean =
    self match {
      case Interrupt         => true
      case Then(left, right) => left.interrupted || right.interrupted
      case Both(left, right) => left.interrupted || right.interrupted
      case Traced(cause, _)  => cause.interrupted
      case Meta(cause, _)    => cause.interrupted
      case _                 => false
    }

  final def map[E1](f: E => E1): Cause[E1] =
    flatMap(f andThen fail)

  final def untraced: Cause[E] =
    self match {
      case Traced(cause, _)  => cause.untraced
      case Meta(cause, data) => Meta(cause.untraced, data)

      case c @ Fail(_) => c
      case c @ Die(_)  => c
      case Interrupt   => Interrupt

      case Then(left, right) => Then(left.untraced, right.untraced)
      case Both(left, right) => Both(left.untraced, right.untraced)
    }

  final def prettyPrint: String = {
    sealed trait Segment
    sealed trait Step extends Segment

    final case class Sequential(all: List[Step])     extends Segment
    final case class Parallel(all: List[Sequential]) extends Step
    final case class Failure(lines: List[String])    extends Step

    def prefixBlock[A](values: List[String], p1: String, p2: String): List[String] =
      values match {
        case Nil => Nil
        case head :: tail =>
          (p1 + head) :: tail.map(p2 + _)
      }

    def parallelSegments(cause: Cause[Any], maybeData: Option[Data]): List[Sequential] =
      cause match {
        case Cause.Both(left, right) => parallelSegments(left, maybeData) ++ parallelSegments(right, maybeData)
        case _                       => List(causeToSequential(cause, maybeData))
      }

    def linearSegments(cause: Cause[Any], maybeData: Option[Data]): List[Step] =
      cause match {
        case Cause.Then(first, second) => linearSegments(first, maybeData) ++ linearSegments(second, maybeData)
        case _                         => causeToSequential(cause, maybeData).all
      }

    // Inline definition of `StringOps.lines` to avoid calling either of `.linesIterator` or `.lines`
    // since both are deprecated in either 2.11 or 2.13 respectively.
    def lines(str: String): List[String] = augmentString(str).linesWithSeparators.map(_.stripLineEnd).toList

    def renderThrowable(e: Throwable, maybeData: Option[Data]): List[String] = {
      val stackless = maybeData.fold(false)(_.stackless)
      if (stackless) List(e.toString)
      else {
        import java.io.{ PrintWriter, StringWriter }
        val sw = new StringWriter()
        val pw = new PrintWriter(sw)
        e.printStackTrace(pw)
        lines(sw.toString)
      }
    }

    def renderTrace(maybeTrace: Option[ZTrace]): List[String] =
      maybeTrace.fold("No ZIO Trace available." :: Nil) { trace =>
        "" :: lines(trace.prettyPrint)
      }

    def renderFail(error: List[String], maybeTrace: Option[ZTrace]): Sequential =
      Sequential(
        List(Failure("A checked error was not handled." :: error ++ renderTrace(maybeTrace)))
      )

    def renderFailThrowable(t: Throwable, maybeTrace: Option[ZTrace], maybeData: Option[Data]): Sequential =
      renderFail(renderThrowable(t, maybeData), maybeTrace)

    def renderDie(t: Throwable, maybeTrace: Option[ZTrace], maybeData: Option[Data]): Sequential =
      Sequential(
        List(Failure("An unchecked error was produced." :: renderThrowable(t, maybeData) ++ renderTrace(maybeTrace)))
      )

    def renderInterrupt(maybeTrace: Option[ZTrace]): Sequential =
      Sequential(
        List(Failure("An unchecked error was produced." :: renderTrace(maybeTrace)))
      )

    def causeToSequential(cause: Cause[Any], maybeData: Option[Data]): Sequential =
      cause match {
        case Cause.Fail(t: Throwable) =>
          renderFailThrowable(t, None, maybeData)
        case Cause.Fail(error) =>
          renderFail(lines(error.toString), None)
        case Cause.Die(t) =>
          renderDie(t, None, maybeData)
        case Cause.Interrupt =>
          renderInterrupt(None)

        case t: Cause.Then[Any] => Sequential(linearSegments(t, maybeData))
        case b: Cause.Both[Any] => Sequential(List(Parallel(parallelSegments(b, maybeData))))
        case Traced(c, trace) =>
          c match {
            case Cause.Fail(t: Throwable) =>
              renderFailThrowable(t, Some(trace), maybeData)
            case Cause.Fail(error) =>
              renderFail(lines(error.toString), Some(trace))
            case Cause.Die(t) =>
              renderDie(t, Some(trace), maybeData)
            case Cause.Interrupt =>
              renderInterrupt(Some(trace))
            case _ =>
              Sequential(
                Failure("An error was rethrown with a new trace." :: renderTrace(Some(trace))) ::
                  causeToSequential(c, maybeData).all
              )
          }
        case Meta(cause, data) =>
          causeToSequential(cause, Some(data))

      }

    def format(segment: Segment): List[String] =
      segment match {
        case Failure(lines) =>
          prefixBlock(lines, "─", " ")
        case Parallel(all) =>
          List(("══╦" * (all.size - 1)) + "══╗") ++
            all.foldRight[List[String]](Nil) {
              case (current, acc) =>
                prefixBlock(acc, "  ║", "  ║") ++
                  prefixBlock(format(current), "  ", "  ")
            }
        case Sequential(all) =>
          all.flatMap { segment =>
            List("║") ++
              prefixBlock(format(segment), "╠", "║")
          } ++ List("▼")
      }

    val sequence = causeToSequential(this, None)

    ("Fiber failed." :: {
      sequence match {
        // use simple report for single failures
        case Sequential(List(Failure(cause))) => cause

        case _ => format(sequence).updated(0, "╥")
      }
    }).mkString("\n")
  }

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the
   * "most important" `Throwable`.
   */
  final def squash(implicit ev: E <:< Throwable): Throwable =
    squashWith(ev)

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the
   * "most important" `Throwable`.
   */
  final def squashWith(f: E => Throwable): Throwable =
    failures.headOption.map(f) orElse
      (if (interrupted) Some(new InterruptedException) else None) orElse
      defects.headOption getOrElse (new InterruptedException)

  /**
   * Remove all `Fail` and `Interrupt` nodes from this `Cause`,
   * return only `Die` cause/finalizer defects.
   */
  final def stripFailures: Option[Cause[Nothing]] =
    self match {
      case Interrupt => None
      case Fail(_)   => None

      case d @ Die(_) => Some(d)

      case Both(l, r) =>
        (l.stripFailures, r.stripFailures) match {
          case (Some(l), Some(r)) => Some(Both(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }

      case Then(l, r) =>
        (l.stripFailures, r.stripFailures) match {
          case (Some(l), Some(r)) => Some(Then(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }

      case Traced(c, trace) => c.stripFailures.map(Traced(_, trace))
      case Meta(c, data)    => c.stripFailures.map(Meta(_, data))
    }

  final def succeeded: Boolean = !failed

  final def traces: List[ZTrace] =
    self
      .foldLeft(List.empty[ZTrace]) {
        case (z, Traced(_, trace)) => trace :: z
      }
      .reverse

  private def foldLeft[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z =
    (f.lift(z -> self).getOrElse(z), self) match {
      case (z, Then(left, right)) => right.foldLeft(left.foldLeft(z)(f))(f)
      case (z, Both(left, right)) => right.foldLeft(left.foldLeft(z)(f))(f)
      case (z, Traced(cause, _))  => cause.foldLeft(z)(f)
      case (z, Meta(cause, _))    => cause.foldLeft(z)(f)

      case (z, _) => z
    }
}

object Cause extends Serializable {

  final def die(defect: Throwable): Cause[Nothing]              = Die(defect)
  final def fail[E](error: E): Cause[E]                         = Fail(error)
  final val interrupt: Cause[Nothing]                           = Interrupt
  final def stack[E](cause: Cause[E]): Cause[E]                 = Meta(cause, Data(false))
  final def stackless[E](cause: Cause[E]): Cause[E]             = Meta(cause, Data(true))
  final def traced[E](cause: Cause[E], trace: ZTrace): Cause[E] = Traced(cause, trace)

  /**
   * Converts the specified `Cause[Option[E]]` to an `Option[Cause[E]]` by
   * recursively stripping out any failures with the error `None`.
   */
  final def sequenceCauseOption[E](c: Cause[Option[E]]): Option[Cause[E]] =
    c match {
      case Cause.Traced(cause, trace) => sequenceCauseOption(cause).map(Cause.Traced(_, trace))
      case Cause.Meta(cause, data)    => sequenceCauseOption(cause).map(Cause.Meta(_, data))
      case Cause.Interrupt            => Some(Cause.Interrupt)
      case d @ Cause.Die(_)           => Some(d)
      case Cause.Fail(Some(e))        => Some(Cause.Fail(e))
      case Cause.Fail(None)           => None
      case Cause.Then(left, right) =>
        (sequenceCauseOption(left), sequenceCauseOption(right)) match {
          case (Some(cl), Some(cr)) => Some(Cause.Then(cl, cr))
          case (None, Some(cr))     => Some(cr)
          case (Some(cl), None)     => Some(cl)
          case (None, None)         => None
        }

      case Cause.Both(left, right) =>
        (sequenceCauseOption(left), sequenceCauseOption(right)) match {
          case (Some(cl), Some(cr)) => Some(Cause.Both(cl, cr))
          case (None, Some(cr))     => Some(cr)
          case (Some(cl), None)     => Some(cl)
          case (None, None)         => None
        }
    }

  private final case class Fail[E](value: E) extends Cause[E] {
    override final def equals(that: Any): Boolean = that match {
      case fail: Fail[_]     => value == fail.value
      case traced: Traced[_] => this == traced.cause
      case meta: Meta[_]     => this == meta.cause
      case _                 => false
    }
  }

  object Fail {
    def apply[E](value: E): Cause[E] =
      new Fail(value)
  }

  private final case class Die(value: Throwable) extends Cause[Nothing] {
    override final def equals(that: Any): Boolean = that match {
      case die: Die          => value == die.value
      case traced: Traced[_] => this == traced.cause
      case meta: Meta[_]     => this == meta.cause
      case _                 => false
    }
  }

  object Die {
    final def apply(value: Throwable): Cause[Nothing] =
      new Die(value)
  }

  case object Interrupt extends Cause[Nothing] {
    override final def equals(that: Any): Boolean =
      (this eq that.asInstanceOf[AnyRef]) || (that match {
        case traced: Traced[_] => this == traced.cause
        case meta: Meta[_]     => this == meta.cause
        case _                 => false
      })
  }

  // Traced is excluded completely from equals & hashCode
  private final case class Traced[E](cause: Cause[E], trace: ZTrace) extends Cause[E] {
    override final def hashCode: Int = cause.hashCode()
    override final def equals(obj: Any): Boolean = obj match {
      case traced: Traced[_] => cause == traced.cause
      case meta: Meta[_]     => cause == meta.cause
      case _                 => cause == obj
    }
  }

  object Traced {
    def apply[E](cause: Cause[E], trace: ZTrace): Cause[E] =
      new Traced(cause, trace)
  }

  // Meta is excluded completely from equals & hashCode
  private final case class Meta[E](cause: Cause[E], data: Data) extends Cause[E] {
    override final def hashCode: Int = cause.hashCode
    override final def equals(obj: Any): Boolean = obj match {
      case traced: Traced[_] => cause == traced.cause
      case meta: Meta[_]     => cause == meta.cause
      case _                 => cause == obj
    }
  }

  private final case class Then[E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
    override final def equals(that: Any): Boolean = that match {
      case traced: Traced[_] => self.equals(traced.cause)
      case meta: Meta[_]     => self.equals(meta.cause)
      case other: Cause[_]   => eq(other) || sym(assoc)(other, self) || sym(dist)(self, other)
      case _                 => false
    }
    override final def hashCode: Int = Cause.flatten(self).hashCode

    private def eq(that: Cause[_]): Boolean = (self, that) match {
      case (tl: Then[_], tr: Then[_]) => tl.left == tr.left && tl.right == tr.right
      case _                          => false
    }

    private def assoc(l: Cause[_], r: Cause[_]): Boolean = (l, r) match {
      case (Then(Then(al, bl), cl), Then(ar, Then(br, cr))) => al == ar && bl == br && cl == cr
      case _                                                => false
    }

    private def dist(l: Cause[_], r: Cause[_]): Boolean = (l, r) match {
      case (Then(al, Both(bl, cl)), Both(Then(ar1, br), Then(ar2, cr)))
          if ar1 == ar2 && al == ar1 && bl == br && cl == cr =>
        true
      case (Then(Both(al, bl), cl), Both(Then(ar, cr1), Then(br, cr2)))
          if cr1 == cr2 && al == ar && bl == br && cl == cr1 =>
        true
      case _ => false
    }
  }

  object Then {
    def apply[E](left: Cause[E], right: Cause[E]): Cause[E] =
      new Then(left, right)
  }

  private final case class Both[E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
    override final def equals(that: Any): Boolean = that match {
      case traced: Traced[_] => self.equals(traced.cause)
      case meta: Meta[_]     => self.equals(meta.cause)
      case other: Cause[_]   => eq(other) || sym(assoc)(self, other) || sym(dist)(self, other) || comm(other)
      case _                 => false
    }
    override final def hashCode: Int = Cause.flatten(self).hashCode

    private def eq(that: Cause[_]) = (self, that) match {
      case (bl: Both[_], br: Both[_]) => bl.left == br.left && bl.right == br.right
      case _                          => false
    }

    private def assoc(l: Cause[_], r: Cause[_]): Boolean = (l, r) match {
      case (Both(Both(al, bl), cl), Both(ar, Both(br, cr))) => al == ar && bl == br && cl == cr
      case _                                                => false
    }

    private def dist(l: Cause[_], r: Cause[_]): Boolean = (l, r) match {
      case (Both(Then(al1, bl), Then(al2, cl)), Then(ar, Both(br, cr)))
          if al1 == al2 && al1 == ar && bl == br && cl == cr =>
        true
      case (Both(Then(al, cl1), Then(bl, cl2)), Then(Both(ar, br), cr))
          if cl1 == cl2 && al == ar && bl == br && cl1 == cr =>
        true
      case _ => false
    }

    private def comm(that: Cause[_]): Boolean = (self, that) match {
      case (Both(al, bl), Both(ar, br)) => al == br && bl == ar
      case _                            => false
    }
  }

  object Both {
    def apply[E](left: Cause[E], right: Cause[E]): Cause[E] =
      new Both(left, right)
  }

  private final case class Data(stackless: Boolean)

  private[Cause] def sym(f: (Cause[_], Cause[_]) => Boolean): (Cause[_], Cause[_]) => Boolean =
    (l, r) => f(l, r) || f(r, l)

  private[Cause] def flatten(c: Cause[_]): Set[Cause[_]] = c match {
    case Then(left, right) => flatten(left) ++ flatten(right)
    case Both(left, right) => flatten(left) ++ flatten(right)
    case Traced(cause, _)  => flatten(cause)
    case o                 => Set(o)
  }
}
