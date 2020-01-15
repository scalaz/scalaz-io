package zio

import scala.concurrent.ExecutionContext

import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{ Executor, Platform }

object RIO {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  def absolve[R, A](v: RIO[R, Either[Throwable, A]]): RIO[R, A] =
    ZIO.absolve(v)

  /**
   * @see See [[zio.ZIO.allowInterrupt]]
   */
  def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see See [[zio.ZIO.apply]]
   */
  def apply[A](a: => A): Task[A] = ZIO.apply(a)

  /**
   * @see See [[zio.ZIO.access]]
   */
  def access[R]: ZIO.AccessPartiallyApplied[R] =
    ZIO.access

  /**
   * @see See [[zio.ZIO.accessM]]
   */
  def accessM[R]: ZIO.AccessMPartiallyApplied[R] =
    ZIO.accessM

  /**
   * @see See bracket [[zio.ZIO]]
   */
  def bracket[R, A](acquire: RIO[R, A]): ZIO.BracketAcquire[R, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  def bracket[R, A, B](
    acquire: RIO[R, A],
    release: A => ZIO[R, Nothing, Any],
    use: A => RIO[R, B]
  ): RIO[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  def bracketExit[R, A](acquire: RIO[R, A]): ZIO.BracketExitAcquire[R, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  def bracketExit[R, A, B](
    acquire: RIO[R, A],
    release: (A, Exit[Throwable, B]) => ZIO[R, Nothing, Any],
    use: A => RIO[R, B]
  ): RIO[R, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkDaemon]]
   */
  def checkDaemon[R, A](f: DaemonStatus => RIO[R, A]): RIO[R, A] =
    ZIO.checkDaemon(f)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  def checkInterruptible[R, A](f: InterruptStatus => RIO[R, A]): RIO[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  def checkTraced[R, A](f: TracingStatus => RIO[R, A]): RIO[R, A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  def children: UIO[Iterable[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see See [[zio.ZIO.collectAll]]
   */
  def collectAll[R, A](in: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAllPar]]
   */
  def collectAllPar[R, A](as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  def collectAllParN[R, A](n: Int)(as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccesses]]
   */
  def collectAllSuccesses[R, A](in: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesPar]]
   */
  def collectAllSuccessesPar[R, A](as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesParN]]
   */
  def collectAllSuccessesParN[E, A](n: Int)(as: Iterable[RIO[E, A]]): RIO[E, List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllWith]]
   */
  def collectAllWith[R, A, B](in: Iterable[RIO[R, A]])(f: PartialFunction[A, B]): RIO[R, List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithPar]]
   */
  def collectAllWithPar[R, A, B](as: Iterable[RIO[R, A]])(f: PartialFunction[A, B]): RIO[R, List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithParN]]
   */
  def collectAllWithParN[R, A, B](n: Int)(as: Iterable[RIO[R, A]])(f: PartialFunction[A, B]): RIO[R, List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see See [[zio.ZIO.daemonMask]]
   */
  def daemonMask[R, A](k: ZIO.DaemonStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.daemonMask(k)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  def descriptorWith[R, A](f: Fiber.Descriptor => RIO[R, A]): RIO[R, A] =
    ZIO.descriptorWith(f)

  /**
   * @see See [[zio.ZIO.die]]
   */
  def die(t: Throwable): UIO[Nothing] = ZIO.die(t)

  /**
   * @see See [[zio.ZIO.dieMessage]]
   */
  def dieMessage(message: String): UIO[Nothing] = ZIO.dieMessage(message)

  /**
   * @see See [[zio.ZIO.done]]
   */
  def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * @see See [[zio.ZIO.effect]]
   */
  def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  def effectAsync[R, A](register: (RIO[R, A] => Unit) => Unit, blockingOn: List[Fiber.Id] = Nil): RIO[R, A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  def effectAsyncMaybe[R, A](
    register: (RIO[R, A] => Unit) => Option[RIO[R, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): RIO[R, A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  def effectAsyncM[R, A](register: (RIO[R, A] => Unit) => RIO[R, Any]): RIO[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  def effectAsyncInterrupt[R, A](
    register: (RIO[R, A] => Unit) => Either[Canceler[R], RIO[R, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): RIO[R, A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * Returns a lazily constructed effect, whose construction may itself require effects.
   * When no environment is required (i.e., when R == Any) it is conceptually equivalent to `flatten(effect(io))`.
   */
  def effectSuspend[R, A](rio: => RIO[R, A]): RIO[R, A] = ZIO.effectSuspend(rio)

  /**
   * @see See [[zio.ZIO.effectSuspendTotal]]
   */
  def effectSuspendTotal[R, A](rio: => RIO[R, A]): RIO[R, A] = ZIO.effectSuspendTotal(rio)

  /**
   * @see See [[zio.ZIO.effectSuspendTotalWith]]
   */
  def effectSuspendTotalWith[R, A](p: (Platform, Fiber.Id) => RIO[R, A]): RIO[R, A] =
    ZIO.effectSuspendTotalWith(p)

  /**
   * Returns a lazily constructed effect, whose construction may itself require effects.
   * When no environment is required (i.e., when R == Any) it is conceptually equivalent to `flatten(effect(io))`.
   */
  def effectSuspendWith[R, A](p: (Platform, Fiber.Id) => RIO[R, A]): RIO[R, A] = ZIO.effectSuspendWith(p)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.environment]]
   */
  def environment[R]: ZIO[R, Nothing, R] = ZIO.environment

  /**
   * @see See [[zio.ZIO.fail]]
   */
  def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * @see [[zio.ZIO.fiberId]]
   */
  val fiberId: UIO[Fiber.Id] = ZIO.fiberId

  /**
   * @see See [[zio.ZIO.firstSuccessOf]]
   */
  def firstSuccessOf[R, A](
    rio: RIO[R, A],
    rest: Iterable[RIO[R, A]]
  ): RIO[R, A] = ZIO.firstSuccessOf(rio, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  def flatten[R, A](taskr: RIO[R, RIO[R, A]]): RIO[R, A] =
    ZIO.flatten(taskr)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => RIO[R, S]): RIO[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foldRight]]
   */
  def foldRight[R, S, A](in: Iterable[A])(zero: S)(f: (A, S) => RIO[R, S]): RIO[R, S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foreach]]
   */
  def foreach[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  def foreachPar[R, A, B](as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  def foreachParN[R, A, B](n: Int)(as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  def foreach_[R, A](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  def foreachPar_[R, A, B](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  def foreachParN_[R, A, B](n: Int)(as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  def forkAll[R, A](as: Iterable[RIO[R, A]]): ZIO[R, Nothing, Fiber[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  def forkAll_[R, A](as: Iterable[RIO[R, A]]): ZIO[R, Nothing, Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  def fromEither[A](v: => Either[Throwable, A]): Task[A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  def fromFiber[A](fiber: => Fiber[Throwable, A]): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  def fromFiberM[A](fiber: Task[Fiber[Throwable, A]]): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see See [[zio.ZIO.fromFunction]]
   */
  def fromFunction[R, A](f: R => A): URIO[R, A] =
    ZIO.fromFunction(f)

  /**
   * @see See [[zio.ZIO.fromFunctionFuture]]
   */
  def fromFunctionFuture[R, A](f: R => scala.concurrent.Future[A]): RIO[R, A] =
    ZIO.fromFunctionFuture(f)

  /**
   * @see See [[zio.ZIO.fromFunctionM]]
   */
  def fromFunctionM[R, A](f: R => Task[A]): RIO[R, A] =
    ZIO.fromFunctionM(f)

  /**
   * @see See [[zio.ZIO.fromFuture]]
   */
  def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)

  /**
   * @see See [[zio.ZIO.fromFutureInterrupt]]
   */
  def fromFutureInterrupt[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFutureInterrupt(make)

  def fromOption[A](v: => Option[A]): IO[Unit, A] = ZIO.fromOption(v)

  /**
   * @see See [[zio.ZIO.fromTry]]
   */
  def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  def halt(cause: Cause[Throwable]): Task[Nothing] = ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.haltWith]]
   */
  def haltWith[R](function: (() => ZTrace) => Cause[Throwable]): RIO[R, Nothing] =
    ZIO.haltWith(function)

  /**
   * @see See [[zio.ZIO.identity]]
   */
  def identity[R]: RIO[Nothing, R] = ZIO.identity

  /**
   * @see See [[zio.ZIO.interrupt]]
   */
  val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  def interruptAs(fiberId: Fiber.Id): UIO[Nothing] = ZIO.interruptAs(fiberId)

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  def interruptible[R, A](taskr: RIO[R, A]): RIO[R, A] =
    ZIO.interruptible(taskr)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  def lock[R, A](executor: Executor)(taskr: RIO[R, A]): RIO[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  def left[R, A](a: A): RIO[R, Either[A, Nothing]] = ZIO.left(a)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C]*]]
   */
  def mapN[R, A, B, C](rio1: RIO[R, A], rio2: RIO[R, B])(f: (A, B) => C): RIO[R, C] =
    ZIO.mapN(rio1, rio2)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D]*]]
   */
  def mapN[R, A, B, C, D](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C])(
    f: (A, B, C) => D
  ): RIO[R, D] =
    ZIO.mapN(rio1, rio2, rio3)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D,F]*]]
   */
  def mapN[R, A, B, C, D, F](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C], rio4: RIO[R, D])(
    f: (A, B, C, D) => F
  ): RIO[R, F] =
    ZIO.mapN(rio1, rio2, rio3, rio4)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C]*]]
   */
  def mapParN[R, A, B, C](rio1: RIO[R, A], rio2: RIO[R, B])(f: (A, B) => C): RIO[R, C] =
    ZIO.mapParN(rio1, rio2)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D]*]]
   */
  def mapParN[R, A, B, C, D](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C])(f: (A, B, C) => D): RIO[R, D] =
    ZIO.mapParN(rio1, rio2, rio3)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D,F]*]]
   */
  def mapParN[R, A, B, C, D, F](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C], rio4: RIO[R, D])(
    f: (A, B, C, D) => F
  ): RIO[R, F] =
    ZIO.mapParN(rio1, rio2, rio3, rio4)(f)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  def mergeAll[R, A, B](in: Iterable[RIO[R, A]])(zero: B)(f: (B, A) => B): RIO[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  def mergeAllPar[R, A, B](in: Iterable[RIO[R, A]])(zero: B)(f: (B, A) => B): RIO[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.never]]
   */
  val never: UIO[Nothing] = ZIO.never

  /**
   * @see See [[zio.ZIO.nonDaemonMask]]
   */
  def nonDaemonMask[R, A](k: ZIO.DaemonStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.nonDaemonMask(k)

  /**
   * @see See [[zio.ZIO.none]]
   */
  val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see See [[zio.ZIO.partitionM]]
   */
  def partitionM[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[Nothing, (List[Throwable], List[B])] =
    ZIO.partitionM(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMPar]]
   */
  def partitionMPar[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[Nothing, (List[Throwable], List[B])] =
    ZIO.partitionMPar(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMParN]]
   */
  def partitionMParN[R, A, B](n: Int)(
    in: Iterable[A]
  )(f: A => RIO[R, B]): RIO[Nothing, (List[Throwable], List[B])] =
    ZIO.partitionMParN(n)(in)(f)

  /**
   * @see See [[zio.ZIO.provide]]
   */
  def provide[R, A](r: R): RIO[R, A] => Task[A] =
    ZIO.provide(r)

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  def raceAll[R, R1 <: R, A](taskr: RIO[R, A], taskrs: Iterable[RIO[R1, A]]): RIO[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  def reduceAll[R, R1 <: R, A](a: RIO[R, A], as: Iterable[RIO[R1, A]])(f: (A, A) => A): RIO[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  def reduceAllPar[R, R1 <: R, A](a: RIO[R, A], as: Iterable[RIO[R1, A]])(f: (A, A) => A): RIO[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.replicate]]
   */
  def replicate[R, A](n: Int)(effect: RIO[R, A]): Iterable[RIO[R, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see See [[zio.ZIO.require]]
   */
  def require[A](error: Throwable): IO[Throwable, Option[A]] => IO[Throwable, A] =
    ZIO.require[Any, Throwable, A](error)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  def reserve[R, A, B](reservation: RIO[R, Reservation[R, Throwable, A]])(use: A => RIO[R, B]): RIO[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[R, B](b: B): RIO[R, Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  def runtime[R]: ZIO[R, Nothing, Runtime[R]] = ZIO.runtime

  /**
   * @see See [[zio.ZIO.sleep]]
   */
  def sleep(duration: Duration): RIO[Clock, Unit] =
    ZIO.sleep(duration)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[R, A](a: A): RIO[R, Option[A]] = ZIO.some(a)

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   *  See [[zio.ZIO.sequence]]
   */
  def sequence[R, A](in: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.sequence(in)

  /**
   *  See [[zio.ZIO.sequencePar]]
   */
  def sequencePar[R, A](as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  def sequenceParN[R, A](n: Int)(as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   * @see See [[zio.ZIO.swap]]
   */
  def swap[R, A, B](implicit ev: R <:< (A, B)): RIO[R, (B, A)] =
    ZIO.swap

  /**
   * @see See [[zio.ZIO.trace]]
   * */
  def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  def traced[R, A](zio: RIO[R, A]): RIO[R, A] = ZIO.traced(zio)

  /**
   * @see See [[zio.ZIO.traverse]]
   */
  def traverse[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.traverse(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
   */
  def traversePar[R, A, B](as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.traversePar(as)(fn)

  /**
   * Alias for [[ZIO.foreachParN]]
   */
  def traverseParN[R, A, B](
    n: Int
  )(as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.traverse_]]
   */
  def traverse_[R, A](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  def traversePar_[R, A](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  def traverseParN_[R, A](
    n: Int
  )(as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  val unit: UIO[Unit] = ZIO.unit

  /**
   * @see See [[zio.ZIO.uninterruptible]]
   */
  def uninterruptible[R, A](taskr: RIO[R, A]): RIO[R, A] =
    ZIO.uninterruptible(taskr)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.unsandbox]]
   */
  def unsandbox[R, A](v: IO[Cause[Throwable], A]): RIO[R, A] = ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  def untraced[R, A](zio: RIO[R, A]): RIO[R, A] = ZIO.untraced(zio)

  /**
   * @see See [[zio.ZIO.when]]
   */
  def when[R](b: Boolean)(rio: RIO[R, Any]): RIO[R, Unit] =
    ZIO.when(b)(rio)

  /**
   * @see See [[zio.ZIO.whenCase]]
   */
  def whenCase[R, E, A](a: A)(pf: PartialFunction[A, ZIO[R, E, Any]]): ZIO[R, E, Unit] =
    ZIO.whenCase(a)(pf)

  /**
   * @see See [[zio.ZIO.whenCaseM]]
   */
  def whenCaseM[R, E, A](a: ZIO[R, E, A])(pf: PartialFunction[A, ZIO[R, E, Any]]): ZIO[R, E, Unit] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see See [[zio.ZIO.whenM]]
   */
  def whenM[R](b: RIO[R, Boolean])(rio: RIO[R, Any]): RIO[R, Unit] =
    ZIO.whenM(b)(rio)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  val yieldNow: UIO[Unit] = ZIO.yieldNow

  /**
   * @see See [[zio.ZIO._1]]
   */
  def _1[R, A, B](implicit ev: R <:< (A, B)): RIO[R, A] = ZIO._1

  /**
   * @see See [[zio.ZIO._2]]
   */
  def _2[R, A, B](implicit ev: R <:< (A, B)): RIO[R, B] = ZIO._2

}
