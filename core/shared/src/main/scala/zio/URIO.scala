package zio

import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{ Executor, Platform }

object URIO {

  /**
   * @see [[zio.ZIO.absolve]]
   */
  def absolve[R, A](v: URIO[R, Either[Nothing, A]]): URIO[R, A] =
    ZIO.absolve(v)

  /**
   * @see See [[zio.ZIO.adopt]]
   */
  def adopt(fiber: Fiber[Any, Any]): UIO[Boolean] =
    ZIO.adopt(fiber)

  /**
   * @see [[zio.ZIO.access]]
   */
  def access[R]: ZIO.AccessPartiallyApplied[R] = ZIO.access[R]

  /**
   * @see [[zio.ZIO.accessM]]
   */
  def accessM[R]: ZIO.AccessMPartiallyApplied[R] = ZIO.accessM[R]

  /**
   * @see [[zio.ZIO.allowInterrupt]]
   */
  def allowInterrupt: UIO[Unit] = ZIO.allowInterrupt

  /**
   * @see [[zio.ZIO.apply]]
   */
  def apply[A](a: => A): UIO[A] = ZIO.effectTotal(a)

  /**
   * @see [[zio.ZIO.awaitAllChildren]]
   */
  val awaitAllChildren: UIO[Unit] = ZIO.awaitAllChildren

  /**
   * @see bracket in [[zio.ZIO]]
   */
  def bracket[R, A](acquire: URIO[R, A]): ZIO.BracketAcquire[R, Nothing, A] =
    ZIO.bracket(acquire)

  /**
   * @see bracket in [[zio.ZIO]]
   **/
  def bracket[R, A, B](
    acquire: URIO[R, A],
    release: A => URIO[R, Any],
    use: A => URIO[R, B]
  ): URIO[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * @see bracketExit in [[zio.ZIO]]
   */
  def bracketExit[R, A](acquire: URIO[R, A]): ZIO.BracketExitAcquire[R, Nothing, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see bracketExit in [[zio.ZIO]]
   */
  def bracketExit[R, A, B](
    acquire: URIO[R, A],
    release: (A, Exit[Nothing, B]) => URIO[R, Any],
    use: A => URIO[R, B]
  ): URIO[R, B] = ZIO.bracketExit(acquire, release, use)

  /**
   * @see [[zio.ZIO.checkInterruptible]]
   */
  def checkInterruptible[R, A](f: InterruptStatus => URIO[R, A]): URIO[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see [[zio.ZIO.checkTraced]]
   */
  def checkTraced[R, A](f: TracingStatus => URIO[R, A]): URIO[R, A] =
    ZIO.checkTraced(f)

  /**
   * @see [[zio.ZIO.children]]
   */
  def children: UIO[Iterable[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A](in:Iterable*]]]
   */
  def collectAll[R, A](in: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A](in:zio\.Chunk*]]]
   */
  def collectAll[R, A](in: Chunk[URIO[R, A]]): URIO[R, Chunk[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A](in:zio\.NonEmptyChunk*]]]
   */
  def collectAll[R, A](in: NonEmptyChunk[URIO[R, A]]): URIO[R, NonEmptyChunk[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[[zio.ZIO.collectAll_[R,E,A](in:Iterable*]]]
   */
  def collectAll_[R, A](in: Iterable[URIO[R, A]]): URIO[R, Unit] =
    ZIO.collectAll_(in)

  /**
   * @see See [[[zio.ZIO.collectAll_[R,E,A](in:zio\.Chunk*]]]
   */
  def collectAll_[R, A](in: Chunk[URIO[R, A]]): URIO[R, Unit] =
    ZIO.collectAll_(in)

  /**
   * @see See [[[zio.ZIO.collectAllPar[R,E,A](as:Iterable*]]]
   */
  def collectAllPar[R, A](as: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[[zio.ZIO.collectAllPar[R,E,A](as:zio\.Chunk*]]]
   */
  def collectAllPar[R, A](as: Chunk[URIO[R, A]]): URIO[R, Chunk[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[[zio.ZIO.collectAllPar[R,E,A](as:zio\.NonEmptyChunk*]]]
   */
  def collectAllPar[R, A](as: NonEmptyChunk[URIO[R, A]]): URIO[R, NonEmptyChunk[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[[zio.ZIO.collectAllPar_[R,E,A](as:Iterable*]]]
   */
  def collectAllPar_[R, A](in: Iterable[URIO[R, A]]): URIO[R, Unit] =
    ZIO.collectAllPar_(in)

  /**
   * @see See [[[zio.ZIO.collectAllPar_[R,E,A](as:zio\.Chunk*]]]
   */
  def collectAllPar_[R, A](in: Chunk[URIO[R, A]]): URIO[R, Unit] =
    ZIO.collectAllPar_(in)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  def collectAllParN[R, A](n: Int)(as: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllParN_]]
   */
  def collectAllParN_[R, A](n: Int)(as: Iterable[URIO[R, A]]): URIO[R, Unit] =
    ZIO.collectAllParN_(n)(as)

  /**
   * @see [[zio.ZIO.collectAllSuccesses]]
   */
  def collectAllSuccesses[R, A](in: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see [[zio.ZIO.collectAllSuccessesPar]]
   */
  def collectAllSuccessesPar[R, A](as: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see [[zio.ZIO.collectAllSuccessesParN]]
   */
  def collectAllSuccessesParN[E, A](n: Int)(as: Iterable[URIO[E, A]]): URIO[E, List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see [[zio.ZIO.collectAllWith]]
   */
  def collectAllWith[R, A, B](in: Iterable[URIO[R, A]])(f: PartialFunction[A, B]): URIO[R, List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see [[zio.ZIO.collectAllWithPar]]
   */
  def collectAllWithPar[R, A, B](as: Iterable[URIO[R, A]])(f: PartialFunction[A, B]): URIO[R, List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see [[zio.ZIO.collectAllWithParN]]
   */
  def collectAllWithParN[R, A, B](n: Int)(as: Iterable[URIO[R, A]])(f: PartialFunction[A, B]): URIO[R, List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see [[zio.ZIO.descriptor]]
   */
  def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see [[zio.ZIO.descriptorWith]]
   */
  def descriptorWith[R, A](f: Fiber.Descriptor => URIO[R, A]): URIO[R, A] =
    ZIO.descriptorWith(f)

  /**
   * @see [[zio.ZIO.die]]
   */
  def die(t: => Throwable): UIO[Nothing] = ZIO.die(t)

  /**
   * @see [[zio.ZIO.dieMessage]]
   */
  def dieMessage(message: => String): UIO[Nothing] = ZIO.dieMessage(message)

  /**
   * @see See [[zio.ZIO.disown]]
   */
  def disown(fiber: Fiber[Any, Any]): UIO[Boolean] = ZIO.disown(fiber)

  /**
   * @see [[zio.ZIO.done]]
   */
  def done[A](r: => Exit[Nothing, A]): UIO[A] = ZIO.done(r)

  /**
   * @see [[zio.ZIO.effectAsync]]
   */
  def effectAsync[R, A](register: (URIO[R, A] => Unit) => Any, blockingOn: List[Fiber.Id] = Nil): URIO[R, A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see [[zio.ZIO.effectAsyncMaybe]]
   */
  def effectAsyncMaybe[R, A](
    register: (URIO[R, A] => Unit) => Option[URIO[R, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): URIO[R, A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see [[zio.ZIO.effectAsyncM]]
   */
  def effectAsyncM[R, A](register: (URIO[R, A] => Unit) => URIO[R, Any]): URIO[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see [[zio.ZIO.effectAsyncInterrupt]]
   */
  def effectAsyncInterrupt[R, A](
    register: (URIO[R, A] => Unit) => Either[Canceler[R], URIO[R, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): URIO[R, A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * @see [[zio.ZIO.effectSuspendTotal]]
   */
  def effectSuspendTotal[R, A](rio: => URIO[R, A]): URIO[R, A] = ZIO.effectSuspendTotal(rio)

  /**
   * @see [[zio.ZIO.effectSuspendTotalWith]]
   */
  def effectSuspendTotalWith[R, A](p: (Platform, Fiber.Id) => URIO[R, A]): URIO[R, A] =
    ZIO.effectSuspendTotalWith(p)

  /**
   * @see [[zio.ZIO.effectTotal]]
   */
  def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see [[zio.ZIO.environment]]
   */
  def environment[R]: ZIO[R, Nothing, R] = ZIO.environment

  /**
   * @see [[zio.ZIO.fiberId]]
   */
  val fiberId: UIO[Fiber.Id] = ZIO.fiberId

  /**
   * @see [[zio.ZIO.filter]]
   */
  def filter[R, A](as: Iterable[A])(f: A => URIO[R, Boolean]): URIO[R, List[A]] =
    ZIO.filter(as)(f)

  /**
   * @see [[zio.ZIO.first]]
   */
  def first[A, B]: URIO[(A, B), A] = ZIO.first

  /**
   * @see [[zio.ZIO.firstSuccessOf]]
   */
  def firstSuccessOf[R, A](
    rio: URIO[R, A],
    rest: Iterable[URIO[R, A]]
  ): URIO[R, A] = ZIO.firstSuccessOf(rio, rest)

  /**
   * @see [[zio.ZIO.flatten]]
   */
  def flatten[R, A](taskr: URIO[R, URIO[R, A]]): URIO[R, A] =
    ZIO.flatten(taskr)

  /**
   * @see [[zio.ZIO.foldLeft]]
   */
  def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => URIO[R, S]): URIO[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see [[zio.ZIO.foldRight]]
   */
  def foldRight[R, S, A](in: Iterable[A])(zero: S)(f: (A, S) => URIO[R, S]): URIO[R, S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see [[[zio.ZIO.foreach[R,E,A,B](in:Iterable*]]]
   */
  def foreach[R, A, B](in: Iterable[A])(f: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see [[[zio.ZIO.foreach[R,E,A,B](in:Option*]]]
   */
  def foreach[R, A, B](in: Option[A])(f: A => URIO[R, B]): URIO[R, Option[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see [[[zio.ZIO.foreach[R,E,A,B](in:zio\.Chunk*]]]
   */
  def foreach[R, A, B](in: Chunk[A])(f: A => URIO[R, B]): URIO[R, Chunk[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see [[[zio.ZIO.foreach[R,E,A,B](in:zio\.NonEmptyChunk*]]]
   */
  def foreach[R, A, B](in: NonEmptyChunk[A])(f: A => URIO[R, B]): URIO[R, NonEmptyChunk[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachExec]]
   */
  final def foreachExec[R, A, B](as: Iterable[A])(exec: ExecutionStrategy)(f: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.foreachExec(as)(exec)(f)

  /**
   * @see [[[zio.ZIO.foreachPar[R,E,A,B](as:Iterable*]]]
   */
  def foreachPar[R, A, B](as: Iterable[A])(fn: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see [[[zio.ZIO.foreachPar[R,E,A,B](as:zio\.Chunk*]]]
   */
  def foreachPar[R, A, B](as: Chunk[A])(fn: A => URIO[R, B]): URIO[R, Chunk[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see [[[zio.ZIO.foreachPar[R,E,A,B](as:zio\.NonEmptyChunk*]]]
   */
  def foreachPar[R, A, B](as: NonEmptyChunk[A])(fn: A => URIO[R, B]): URIO[R, NonEmptyChunk[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see [[zio.ZIO.foreachParN]]
   */
  def foreachParN[R, A, B](n: Int)(as: Iterable[A])(fn: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see [[[zio.ZIO.foreach_[R,E,A](as:Iterable*]]]
   */
  def foreach_[R, A](as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see [[[zio.ZIO.foreach_[R,E,A](as:zio\.Chunk*]]]
   */
  final def foreach_[R, A](as: Chunk[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see [[[zio.ZIO.foreachPar_[R,E,A](as:Iterable*]]]
   */
  def foreachPar_[R, A, B](as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see [[[zio.ZIO.foreachPar_[R,E,A](as:zio\.Chunk*]]]
   */
  def foreachPar_[R, A, B](as: Chunk[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see [[zio.ZIO.foreachParN_]]
   */
  def foreachParN_[R, A, B](n: Int)(as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see [[zio.ZIO.forkAll]]
   */
  def forkAll[R, A](as: Iterable[URIO[R, A]]): ZIO[R, Nothing, Fiber[Nothing, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see [[zio.ZIO.forkAll_]]
   */
  def forkAll_[R, A](as: Iterable[URIO[R, A]]): ZIO[R, Nothing, Unit] =
    ZIO.forkAll_(as)

  /**
   * @see [[zio.ZIO.fromEither]]
   */
  def fromEither[A](v: => Either[Nothing, A]): UIO[A] =
    ZIO.fromEither(v)

  /**
   * @see [[zio.ZIO.fromFiber]]
   */
  def fromFiber[A](fiber: => Fiber[Nothing, A]): UIO[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see [[zio.ZIO.fromFiberM]]
   */
  def fromFiberM[A](fiber: UIO[Fiber[Nothing, A]]): UIO[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see [[zio.ZIO.fromFunction]]
   */
  def fromFunction[R, A](f: R => A): URIO[R, A] =
    ZIO.fromFunction(f)

  /**
   * @see [[zio.ZIO.fromFunctionM]]
   */
  def fromFunctionM[R, A](f: R => UIO[A]): URIO[R, A] =
    ZIO.fromFunctionM(f)

  /**
   * @see [[zio.ZIO.halt]]
   */
  def halt(cause: => Cause[Nothing]): UIO[Nothing] = ZIO.halt(cause)

  /**
   * @see [[zio.ZIO.haltWith]]
   */
  def haltWith[R](function: (() => ZTrace) => Cause[Nothing]): URIO[R, Nothing] =
    ZIO.haltWith(function)

  /**
   * @see [[zio.ZIO.identity]]
   */
  def identity[R]: URIO[R, R] = ZIO.identity

  /**
   * @see [[zio.ZIO.ifM]]
   */
  def ifM[R](b: URIO[R, Boolean]): ZIO.IfM[R, Nothing] =
    new ZIO.IfM(b)

  /**
   * @see [[zio.ZIO.infinity]]
   */
  val infinity: URIO[Clock, Nothing] = ZIO.sleep(Duration.fromNanos(Long.MaxValue)) *> ZIO.never

  /**
   * @see [[zio.ZIO.interrupt]]
   */
  val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [zio.ZIO.interruptAllChildren]
   */
  def interruptAllChildren: UIO[Unit] = ZIO.children.flatMap(Fiber.interruptAll(_))

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  def interruptAs(fiberId: => Fiber.Id): UIO[Nothing] = ZIO.interruptAs(fiberId)

  /**
   * @see [[zio.ZIO.interruptible]]
   */
  def interruptible[R, A](taskr: URIO[R, A]): URIO[R, A] =
    ZIO.interruptible(taskr)

  /**
   * @see [[zio.ZIO.interruptibleMask]]
   */
  def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => URIO[R, A]): URIO[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * @see See [[zio.ZIO.iterate]]
   */
  def iterate[R, S](initial: S)(cont: S => Boolean)(body: S => URIO[R, S]): URIO[R, S] =
    ZIO.iterate(initial)(cont)(body)

  /**
   * @see [[zio.ZIO.lock]]
   */
  def lock[R, A](executor: => Executor)(taskr: URIO[R, A]): URIO[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   *  @see See [[zio.ZIO.loop]]
   */
  def loop[R, A, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => URIO[R, A]): URIO[R, List[A]] =
    ZIO.loop(initial)(cont, inc)(body)

  /**
   *  @see See [[zio.ZIO.loop_]]
   */
  def loop_[R, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => URIO[R, Any]): URIO[R, Unit] =
    ZIO.loop_(initial)(cont, inc)(body)

  /**
   *  @see [[zio.ZIO.left]]
   */
  def left[R, A](a: => A): URIO[R, Either[A, Nothing]] = ZIO.left(a)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C]*]]
   */
  def mapN[R, A, B, C](urio1: URIO[R, A], urio2: URIO[R, B])(f: (A, B) => C): URIO[R, C] =
    ZIO.mapN(urio1, urio2)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D]*]]
   */
  def mapN[R, A, B, C, D](urio1: URIO[R, A], urio2: URIO[R, B], urio3: URIO[R, C])(
    f: (A, B, C) => D
  ): URIO[R, D] =
    ZIO.mapN(urio1, urio2, urio3)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D,F]*]]
   */
  def mapN[R, A, B, C, D, F](urio1: URIO[R, A], urio2: URIO[R, B], urio3: URIO[R, C], urio4: URIO[R, D])(
    f: (A, B, C, D) => F
  ): URIO[R, F] =
    ZIO.mapN(urio1, urio2, urio3, urio4)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C]*]]
   */
  def mapParN[R, A, B, C](urio1: URIO[R, A], urio2: URIO[R, B])(f: (A, B) => C): URIO[R, C] =
    ZIO.mapParN(urio1, urio2)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D]*]]
   */
  def mapParN[R, A, B, C, D](urio1: URIO[R, A], urio2: URIO[R, B], urio3: URIO[R, C])(
    f: (A, B, C) => D
  ): URIO[R, D] =
    ZIO.mapParN(urio1, urio2, urio3)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D,F]*]]
   */
  def mapParN[R, A, B, C, D, F](urio1: URIO[R, A], urio2: URIO[R, B], urio3: URIO[R, C], urio4: URIO[R, D])(
    f: (A, B, C, D) => F
  ): URIO[R, F] =
    ZIO.mapParN(urio1, urio2, urio3, urio4)(f)

  /**
   * @see [[zio.ZIO.mergeAll]]
   */
  def mergeAll[R, A, B](in: Iterable[URIO[R, A]])(zero: B)(f: (B, A) => B): URIO[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see [[zio.ZIO.mergeAllPar]]
   */
  def mergeAllPar[R, A, B](in: Iterable[URIO[R, A]])(zero: B)(f: (B, A) => B): URIO[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see [[zio.ZIO.never]]
   */
  val never: UIO[Nothing] = ZIO.never

  /**
   * @see [[zio.ZIO.none]]
   */
  val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see [[zio.ZIO.provide]]
   */
  def provide[R, A](r: => R): URIO[R, A] => UIO[A] =
    ZIO.provide(r)

  /**
   * @see [[zio.ZIO.raceAll]]
   */
  def raceAll[R, R1 <: R, A](taskr: URIO[R, A], taskrs: Iterable[URIO[R1, A]]): URIO[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * @see [[zio.ZIO.reduceAll]]
   */
  def reduceAll[R, R1 <: R, A](a: URIO[R, A], as: Iterable[URIO[R1, A]])(f: (A, A) => A): URIO[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see [[zio.ZIO.reduceAllPar]]
   */
  def reduceAllPar[R, R1 <: R, A](a: URIO[R, A], as: Iterable[URIO[R1, A]])(f: (A, A) => A): URIO[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see [[zio.ZIO.replicate]]
   */
  def replicate[R, A](n: Int)(effect: URIO[R, A]): Iterable[URIO[R, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see [[zio.ZIO.reserve]]
   */
  def reserve[R, A, B](reservation: URIO[R, Reservation[R, Nothing, A]])(use: A => URIO[R, B]): URIO[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[R, B](b: => B): RIO[R, Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see [[zio.ZIO.runtime]]
   */
  def runtime[R]: URIO[R, Runtime[R]] = ZIO.runtime

  /**
   * @see [[zio.ZIO.second]]
   */
  def second[A, B]: URIO[(A, B), B] = ZIO.second

  /**
   * @see [[zio.ZIO.sleep]]
   */
  def sleep(duration: => Duration): URIO[Clock, Unit] = ZIO.sleep(duration)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[R, A](a: => A): URIO[R, Option[A]] = ZIO.some(a)

  /**
   * @see [[zio.ZIO.succeed]]
   */
  def succeed[A](a: => A): UIO[A] = ZIO.succeed(a)

  /**
   * @see [[zio.ZIO.swap]]
   */
  def swap[A, B]: URIO[(A, B), (B, A)] = ZIO.swap

  /**
   * @see [[zio.ZIO.trace]]
   * */
  def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see [[zio.ZIO.traced]]
   */
  def traced[R, A](zio: URIO[R, A]): URIO[R, A] = ZIO.traced(zio)

  /**
   * @see [[zio.ZIO.unit]]
   */
  val unit: UIO[Unit] = ZIO.unit

  /**
   * @see [[zio.ZIO.uninterruptible]]
   */
  def uninterruptible[R, A](taskr: URIO[R, A]): URIO[R, A] = ZIO.uninterruptible(taskr)

  /**
   * @see [[zio.ZIO.uninterruptibleMask]]
   */
  def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => URIO[R, A]): URIO[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.unless]]
   */
  def unless[R](b: => Boolean)(zio: => URIO[R, Any]): URIO[R, Unit] =
    ZIO.unless(b)(zio)

  /**
   * @see See [[zio.ZIO.unlessM]]
   */
  def unlessM[R](b: URIO[R, Boolean])(zio: => URIO[R, Any]): URIO[R, Unit] =
    ZIO.unlessM(b)(zio)

  /**
   * @see [[zio.ZIO.unsandbox]]
   */
  def unsandbox[R, A](v: IO[Cause[Nothing], A]): URIO[R, A] = ZIO.unsandbox(v)

  /**
   * @see [[zio.ZIO.untraced]]
   */
  def untraced[R, A](zio: URIO[R, A]): URIO[R, A] = ZIO.untraced(zio)

  /**
   * @see [[zio.ZIO.when]]
   */
  def when[R](b: => Boolean)(rio: => URIO[R, Any]): URIO[R, Unit] = ZIO.when(b)(rio)

  /**
   * @see [[zio.ZIO.whenCase]]
   */
  def whenCase[R, A](a: => A)(pf: PartialFunction[A, URIO[R, Any]]): URIO[R, Unit] =
    ZIO.whenCase(a)(pf)

  /**
   * @see [[zio.ZIO.whenCaseM]]
   */
  def whenCaseM[R, A](a: URIO[R, A])(pf: PartialFunction[A, URIO[R, Any]]): URIO[R, Unit] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see [[zio.ZIO.whenM]]
   */
  def whenM[R](b: URIO[R, Boolean])(rio: => URIO[R, Any]): URIO[R, Unit] = ZIO.whenM(b)(rio)

  /**
   * @see [[zio.ZIO.yieldNow]]
   */
  val yieldNow: UIO[Unit] = ZIO.yieldNow

  private[zio] def succeedNow[A](a: A): UIO[A] = ZIO.succeedNow(a)
}
