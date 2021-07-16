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

/**
 * A `FiberRef` is ZIO's equivalent of Java's `ThreadLocal`. The value of a
 * `FiberRef` is automatically propagated to child fibers when they are forked
 * and merged back in to the value of the parent fiber after they are joined.
 *
 * {{{
 * for {
 *   fiberRef <- FiberRef.make("Hello world!")
 *   child    <- fiberRef.set("Hi!).fork
 *   result   <- child.join
 * } yield result
 * }}}
 *
 * Here `result` will be equal to "Hi!" since changed made by a child fiber are
 * merged back in to the value of the parent fiber on join.
 *
 * By default the value of the child fiber will replace the value of the parent
 * fiber on join but you can specify your own logic for how values should be
 * merged.
 *
 * {{{
 * for {
 *   fiberRef <- FiberRef.make(0, math.max)
 *   child    <- fiberRef.update(_ + 1).fork
 *   _        <- fiberRef.update(_ + 2)
 *   _        <- child.join
 *   value    <- fiberRef.get
 * } yield value
 * }}}
 *
 * Here `value` will be 2 as the value in the joined fiber is lower and we
 * specified `max` as our combining function.
 */
sealed abstract class ZFiberRef[+EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * Folds over the error and value types of the `ZFiberRef`. This is a highly
   * polymorphic method that is capable of arbitrarily transforming the error
   * and value types of the `ZFiberRef`. For most use cases one of the more
   * specific combinators implemented in terms of `fold` will be more ergonomic
   * but this method is extremely useful for implementing new combinators.
   */
  def fold[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ca: C => Either[EC, A],
    bd: B => Either[ED, D]
  ): ZFiberRef[EC, ED, C, D]

  /**
   * Folds over the error and value types of the `ZFiberRef`, allowing access
   * to the state in transforming the `set` value. This is a more powerful
   * version of `fold` but requires unifying the error types.
   */
  def foldAll[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ec: EB => EC,
    ca: C => B => Either[EC, A],
    bd: B => Either[ED, D]
  ): ZFiberRef[EC, ED, C, D]

  /**
   * Reads the value associated with the current fiber. Returns initial value if
   * no value was `set` or inherited from parent.
   */
  def get: IO[EB, B]

  /**
   * Returns the initial value or error.
   */
  def initialValue: Either[EB, B]

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `acquireRelease`.
   */
  def locally[R, EC >: EA, C](value: A)(use: ZIO[R, EC, C]): ZIO[R, EC, C]

  /**
   * Sets the value associated with the current fiber.
   */
  def set(value: A): IO[EA, Unit]

  /**
   * Maps and filters the `get` value of the `ZFiberRef` with the specified
   * partial function, returning a `ZFiberRef` with a `get` value that succeeds
   * with the result of the partial function if it is defined or else fails
   * with `None`.
   */
  def collect[C](pf: PartialFunction[B, C]): ZFiberRef[EA, Option[EB], A, C] =
    fold(identity, Some(_), Right(_), pf.lift(_).toRight(None))

  /**
   * Transforms the `set` value of the `ZFiberRef` with the specified function.
   */
  def contramap[C](f: C => A): ZFiberRef[EA, EB, C, B] =
    contramapEither(c => Right(f(c)))

  /**
   * Transforms the `set` value of the `ZFiberRef` with the specified fallible
   * function.
   */
  def contramapEither[EC >: EA, C](f: C => Either[EC, A]): ZFiberRef[EC, EB, C, B] =
    dimapEither(f, Right(_))

  /**
   * Transforms both the `set` and `get` values of the `ZFiberRef` with the
   * specified functions.
   */
  def dimap[C, D](f: C => A, g: B => D): ZFiberRef[EA, EB, C, D] =
    dimapEither(c => Right(f(c)), b => Right(g(b)))

  /**
   * Transforms both the `set` and `get` values of the `ZFiberRef` with the
   * specified fallible functions.
   */
  def dimapEither[EC >: EA, ED >: EB, C, D](
    f: C => Either[EC, A],
    g: B => Either[ED, D]
  ): ZFiberRef[EC, ED, C, D] =
    fold(identity, identity, f, g)

  /**
   * Transforms both the `set` and `get` errors of the `ZFiberRef` with the
   * specified functions.
   */
  def dimapError[EC, ED](f: EA => EC, g: EB => ED): ZFiberRef[EC, ED, A, B] =
    fold(f, g, Right(_), Right(_))

  /**
   * Filters the `set` value of the `ZFiberRef` with the specified predicate,
   * returning a `ZFiberRef` with a `set` value that succeeds if the predicate
   * is satisfied or else fails with `None`.
   */
  def filterInput[A1 <: A](f: A1 => Boolean): ZFiberRef[Option[EA], EB, A1, B] =
    fold(Some(_), identity, a => if (f(a)) Right(a) else Left(None), Right(_))

  /**
   * Filters the `get` value of the `ZFiberRef` with the specified predicate,
   * returning a `ZFiberRef` with a `get` value that succeeds if the predicate
   * is satisfied or else fails with `None`.
   */
  def filterOutput(f: B => Boolean): ZFiberRef[EA, Option[EB], A, B] =
    fold(identity, Some(_), Right(_), b => if (f(b)) Right(b) else Left(None))

  /**
   * Transforms the `get` value of the `ZFiberRef` with the specified function.
   */
  def map[C](f: B => C): ZFiberRef[EA, EB, A, C] =
    mapEither(b => Right(f(b)))

  /**
   * Transforms the `get` value of the `ZFiberRef` with the specified fallible
   * function.
   */
  def mapEither[EC >: EB, C](f: B => Either[EC, C]): ZFiberRef[EA, EC, A, C] =
    dimapEither(Right(_), f)

  /**
   * Returns a read only view of the `ZFiberRef`.
   */
  def readOnly: ZFiberRef[EA, EB, Nothing, B] =
    self

  /**
   * Returns a write only view of the `ZFiberRef`.
   */
  def writeOnly: ZFiberRef[EA, Unit, A, Nothing] =
    fold(identity, _ => (), Right(_), _ => Left(()))
}

object ZFiberRef {

  val currentLogLevel: FiberRef.Runtime[LogLevel] =
    FiberRef.unsafeMake(LogLevel.Info)

  val currentLogSpan: FiberRef.Runtime[List[String]] =
    FiberRef.unsafeMake(Nil)

  /**
   * Creates a new `FiberRef` with given initial value.
   */
  def make[A](
    initial: A,
    fork: A => A = (a: A) => a,
    join: (A, A) => A = ((_: A, a: A) => a)
  ): UIO[FiberRef.Runtime[A]] =
    ZIO.succeed(unsafeMake(initial, fork, join))

  private[zio] def unsafeMake[A](
    initial: A,
    fork: A => A = (a: A) => a,
    join: (A, A) => A = ((_: A, a: A) => a)
  ): FiberRef.Runtime[A] =
    new ZFiberRef.Runtime[A](initial, fork, join)

  final class Runtime[A] private[zio] (
    private[zio] val initial: A,
    private[zio] val fork: A => A,
    private[zio] val join: (A, A) => A
  ) extends ZFiberRef[Nothing, Nothing, A, A] { self =>
    def fold[EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ca: C => Either[EC, A],
      bd: A => Either[ED, D]
    ): ZFiberRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = A
        def getEither(s: S): Either[ED, D] =
          bd(s)
        def setEither(c: C): Either[EC, S] =
          ca(c)
        val value: Runtime[S] =
          self
      }

    def foldAll[EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ec: Nothing => EC,
      ca: C => (A => Either[EC, A]),
      bd: A => Either[ED, D]
    ): ZFiberRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = A
        def getEither(s: S): Either[ED, D] =
          bd(s)
        def initialValue: Either[ED, D] = self.initialValue.flatMap(bd)
        def setEither(c: C)(s: S): Either[EC, S] =
          ca(c)(s)
        val value: Runtime[S] =
          self

      }

    def get: IO[Nothing, A] =
      modify(v => (v, v))

    def getAndSet(a: A): UIO[A] =
      modify(v => (v, a))

    def getAndUpdate(f: A => A): UIO[A] =
      modify { v =>
        val result = f(v)
        (v, result)
      }

    def getAndUpdateSome(pf: PartialFunction[A, A]): UIO[A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (v, result)
      }

    def initialValue: Either[Nothing, A] = Right(initial)

    def locally[R, EC, C](value: A)(use: ZIO[R, EC, C]): ZIO[R, EC, C] =
      for {
        oldValue <- get
        b        <- set(value).acquireRelease(set(oldValue))(use)
      } yield b

    def modify[B](f: A => (B, A)): UIO[B] =
      new ZIO.FiberRefModify(this, f)

    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): UIO[B] =
      modify { v =>
        pf.applyOrElse[A, (B, A)](v, _ => (default, v))
      }

    def set(value: A): IO[Nothing, Unit] =
      modify(_ => ((), value))

    def update(f: A => A): UIO[Unit] =
      modify { v =>
        val result = f(v)
        ((), result)
      }

    def updateAndGet(f: A => A): UIO[A] =
      modify { v =>
        val result = f(v)
        (result, result)
      }

    def updateSome(pf: PartialFunction[A, A]): UIO[Unit] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        ((), result)
      }

    def updateSomeAndGet(pf: PartialFunction[A, A]): UIO[A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (result, result)
      }

    /**
     * Returns a `ThreadLocal` that can be used to interact with this `FiberRef` from side effecting code.
     *
     * This feature is meant to be used for integration with side effecting code, that needs to access fiber specific data,
     * like MDC contexts and the like. The returned `ThreadLocal` will be backed by this `FiberRef` on all threads that are
     * currently managed by ZIO, and behave like an ordinary `ThreadLocal` on all other threads.
     */
    def unsafeAsThreadLocal: UIO[ThreadLocal[A]] =
      ZIO.succeed {
        new ThreadLocal[A] {
          override def get(): A = {
            val fiberContext = Fiber._currentFiber.get()

            fiberContext.fiberRefLocals.get.getOrElse(self, super.get()).asInstanceOf[A]
          }

          override def set(a: A): Unit = {
            val fiberContext = Fiber._currentFiber.get()
            val fiberRef     = self.asInstanceOf[FiberRef.Runtime[Any]]

            if (fiberContext eq null) super.set(a)
            else fiberContext.setFiberRefValue(fiberRef, a)
          }

          override def remove(): Unit = {
            val fiberContext = Fiber._currentFiber.get()
            val fiberRef     = self

            if (fiberContext eq null) super.remove()
            else fiberContext.removeFiberRef(fiberRef)
          }

          override def initialValue(): A = initial
        }
      }
  }

  private abstract class Derived[+EA, +EB, -A, +B] extends ZFiberRef[EA, EB, A, B] { self =>
    type S

    def getEither(s: S): Either[EB, B]

    def setEither(a: A): Either[EA, S]

    val value: Runtime[S]

    def fold[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => Either[EC, A],
      bd: B => Either[ED, D]
    ): ZFiberRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def setEither(c: C): Either[EC, S] =
          ca(c).flatMap(a => self.setEither(a).fold(e => Left(ea(e)), Right(_)))
        val value: Runtime[S] =
          self.value
      }

    def foldAll[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ec: EB => EC,
      ca: C => (B => Either[EC, A]),
      bd: B => Either[ED, D]
    ): ZFiberRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def initialValue: Either[ED, D] = self.initialValue.left.map(eb).flatMap(bd)
        def setEither(c: C)(s: S): Either[EC, S] =
          self
            .getEither(s)
            .fold(e => Left(ec(e)), ca(c))
            .flatMap(a => self.setEither(a).fold(e => Left(ea(e)), Right(_)))
        val value: Runtime[S] =
          self.value
      }

    def get: IO[EB, B] =
      value.get.flatMap(getEither(_).fold(ZIO.fail(_), ZIO.succeedNow))

    def initialValue: Either[EB, B] = value.initialValue.flatMap(getEither(_))

    def locally[R, EC >: EA, C](a: A)(use: ZIO[R, EC, C]): ZIO[R, EC, C] =
      value.get.flatMap { old =>
        setEither(a).fold(
          e => ZIO.fail(e),
          s => value.set(s).acquireRelease(value.set(old))(use)
        )
      }

    def set(a: A): IO[EA, Unit] =
      setEither(a).fold(ZIO.fail(_), value.set)
  }

  private abstract class DerivedAll[+EA, +EB, -A, +B] extends ZFiberRef[EA, EB, A, B] { self =>
    type S

    def getEither(s: S): Either[EB, B]

    def setEither(a: A)(s: S): Either[EA, S]

    val value: Runtime[S]

    def fold[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => Either[EC, A],
      bd: B => Either[ED, D]
    ): ZFiberRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def initialValue: Either[ED, D] = self.initialValue.left.map(eb).flatMap(bd)
        def setEither(c: C)(s: S): Either[EC, S] =
          ca(c).flatMap(a => self.setEither(a)(s).fold(e => Left(ea(e)), Right(_)))
        val value: Runtime[S] =
          self.value
      }

    def foldAll[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ec: EB => EC,
      ca: C => (B => Either[EC, A]),
      bd: B => Either[ED, D]
    ): ZFiberRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def initialValue: Either[ED, D] = self.initialValue.left.map(eb).flatMap(bd)
        def setEither(c: C)(s: S): Either[EC, S] =
          self
            .getEither(s)
            .fold(e => Left(ec(e)), ca(c))
            .flatMap(a => self.setEither(a)(s).fold(e => Left(ea(e)), Right(_)))
        val value: Runtime[S] =
          self.value
      }

    def get: IO[EB, B] =
      value.get.flatMap(getEither(_).fold(ZIO.fail(_), ZIO.succeedNow))

    def locally[R, EC >: EA, C](a: A)(use: ZIO[R, EC, C]): ZIO[R, EC, C] =
      value.get.flatMap { old =>
        setEither(a)(old).fold(
          e => ZIO.fail(e),
          s => value.set(s).acquireRelease(value.set(old))(use)
        )
      }

    def set(a: A): IO[EA, Unit] =
      value.modify { s =>
        setEither(a)(s) match {
          case Left(e)  => (Left(e), s)
          case Right(s) => (Right(()), s)
        }
      }.absolve
  }

  implicit final class UnifiedSyntax[E, A](private val self: ZFiberRef[E, E, A, A]) extends AnyVal {

    /**
     * Atomically sets the value associated with the current fiber and returns
     * the old value.
     */
    def getAndSet(a: A): IO[E, A] =
      modify(v => (v, a))

    /**
     * Atomically modifies the `FiberRef` with the specified function and returns
     * the old value.
     */
    def getAndUpdate(f: A => A): IO[E, A] =
      modify { v =>
        val result = f(v)
        (v, result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function and
     * returns the old value.
     * If the function is undefined on the current value it doesn't change it.
     */
    def getAndUpdateSome(pf: PartialFunction[A, A]): IO[E, A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (v, result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified function, which computes
     * a return value for the modification. This is a more powerful version of
     * `update`.
     */
    def modify[B](f: A => (B, A)): IO[E, B] =
      self match {
        case derived: Derived[E, E, A, A] =>
          derived.value.modify { s =>
            derived.getEither(s) match {
              case Left(e) => (Left(e), s)
              case Right(a1) => {
                val (b, a2) = f(a1)
                derived.setEither(a2) match {
                  case Left(e)  => (Left(e), s)
                  case Right(s) => (Right(b), s)
                }
              }
            }
          }.absolve
        case derivedAll: DerivedAll[E, E, A, A] =>
          derivedAll.value.modify { s =>
            derivedAll.getEither(s) match {
              case Left(e) => (Left(e), s)
              case Right(a1) => {
                val (b, a2) = f(a1)
                derivedAll.setEither(a2)(s) match {
                  case Left(e)  => (Left(e), s)
                  case Right(s) => (Right(b), s)
                }
              }
            }
          }.absolve
        case runtime: Runtime[A] => runtime.modify(f)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function, which computes
     * a return value for the modification if the function is defined in the current value
     * otherwise it returns a default value.
     * This is a more powerful version of `updateSome`.
     */
    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): IO[E, B] =
      modify { v =>
        pf.applyOrElse[A, (B, A)](v, _ => (default, v))
      }

    /**
     * Atomically modifies the `FiberRef` with the specified function.
     */
    def update(f: A => A): IO[E, Unit] =
      modify { v =>
        val result = f(v)
        ((), result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified function and returns
     * the result.
     */
    def updateAndGet(f: A => A): IO[E, A] =
      modify { v =>
        val result = f(v)
        (result, result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function.
     * If the function is undefined on the current value it doesn't change it.
     */
    def updateSome(pf: PartialFunction[A, A]): IO[E, Unit] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        ((), result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function.
     * If the function is undefined on the current value it returns the old value
     * without changing it.
     */
    def updateSomeAndGet(pf: PartialFunction[A, A]): IO[E, A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (result, result)
      }
  }
}
