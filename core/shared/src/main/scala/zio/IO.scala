package zio

import scala.concurrent.ExecutionContext

import zio.internal.{ Executor, Platform }

object IO {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  def absolve[E, A](v: IO[E, Either[E, A]]): IO[E, A] =
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
   * @see See bracket [[zio.ZIO]]
   */
  def bracket[E, A](acquire: IO[E, A]): BracketAcquire[E, A] =
    new BracketAcquire(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  def bracket[E, A, B](acquire: IO[E, A], release: A => UIO[Any], use: A => IO[E, B]): IO[E, B] =
    ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  def bracketExit[E, A](acquire: IO[E, A]): ZIO.BracketExitAcquire[Any, E, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  def bracketExit[E, A, B](
    acquire: IO[E, A],
    release: (A, Exit[E, B]) => UIO[Any],
    use: A => IO[E, B]
  ): IO[E, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See bracketFork [[zio.ZIO]]
   */
  def bracketFork[E, A](acquire: IO[E, A]): BracketForkAcquire[E, A] =
    new BracketForkAcquire(acquire)

  /**
   * @see See bracketFork [[zio.ZIO]]
   */
  def bracketFork[E, A, B](acquire: IO[E, A], release: A => UIO[Any], use: A => IO[E, B]): IO[E, B] =
    ZIO.bracketFork(acquire, release, use)

  /**
   * @see See bracketForkExit [[zio.ZIO]]
   */
  def bracketForkExit[E, A](acquire: IO[E, A]): ZIO.BracketForkExitAcquire[Any, E, A] =
    ZIO.bracketForkExit(acquire)

  /**
   * @see See bracketForkExit [[zio.ZIO]]
   */
  def bracketForkExit[E, A, B](
    acquire: IO[E, A],
    release: (A, Exit[E, B]) => UIO[Any],
    use: A => IO[E, B]
  ): IO[E, B] =
    ZIO.bracketForkExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkDaemon]]
   */
  def checkDaemon[E, A](f: DaemonStatus => IO[E, A]): IO[E, A] =
    ZIO.checkDaemon(f)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  def checkInterruptible[E, A](f: InterruptStatus => IO[E, A]): IO[E, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  def checkTraced[E, A](f: TracingStatus => IO[E, A]): IO[E, A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  def children: UIO[Iterable[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see See [[zio.ZIO.collectAll]]
   */
  def collectAll[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAllPar]]
   */
  def collectAllPar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  def collectAllParN[E, A](n: Int)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccesses]]
   */
  def collectAllSuccesses[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesPar]]
   */
  def collectAllSuccessesPar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesParN]]
   */
  def collectAllSuccessesParN[E, A](n: Int)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllWith]]
   */
  def collectAllWith[E, A, B](in: Iterable[IO[E, A]])(f: PartialFunction[A, B]): IO[E, List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithPar]]
   */
  def collectAllWithPar[E, A, B](as: Iterable[IO[E, A]])(f: PartialFunction[A, B]): IO[E, List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithParN]]
   */
  def collectAllWithParN[E, A, B](n: Int)(as: Iterable[IO[E, A]])(f: PartialFunction[A, B]): IO[E, List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see See [[zio.ZIO.daemonMask]]
   */
  def daemonMask[E, A](k: ZIO.DaemonStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.daemonMask(k)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  def descriptorWith[E, A](f: Fiber.Descriptor => IO[E, A]): IO[E, A] =
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
  def done[E, A](r: Exit[E, A]): IO[E, A] = ZIO.done(r)

  /**
   * @see See [[zio.ZIO.effect]]
   */
  def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  def effectAsync[E, A](register: (IO[E, A] => Unit) => Unit, blockingOn: List[Fiber.Id] = Nil): IO[E, A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  def effectAsyncInterrupt[E, A](
    register: (IO[E, A] => Unit) => Either[Canceler[Any], IO[E, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): IO[E, A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  def effectAsyncM[E, A](register: (IO[E, A] => Unit) => IO[E, Any]): IO[E, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  def effectAsyncMaybe[E, A](
    register: (IO[E, A] => Unit) => Option[IO[E, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): IO[E, A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see [[zio.ZIO.effectSuspend]]
   */
  def effectSuspend[A](io: => IO[Throwable, A]): IO[Throwable, A] = ZIO.effectSuspend(io)

  /**
   * @see [[zio.ZIO.effectSuspendWith]]
   */
  def effectSuspendWith[A](p: (Platform, Fiber.Id) => IO[Throwable, A]): IO[Throwable, A] =
    ZIO.effectSuspendWith(p)

  /**
   * @see See [[zio.ZIO.effectSuspendTotal]]
   */
  def effectSuspendTotal[E, A](io: => IO[E, A]): IO[E, A] = ZIO.effectSuspendTotal(io)

  /**
   * @see See [[zio.ZIO.effectSuspendTotalWith]]
   */
  def effectSuspendTotalWith[E, A](p: (Platform, Fiber.Id) => IO[E, A]): IO[E, A] = ZIO.effectSuspendTotalWith(p)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.fail]]
   */
  def fail[E](error: E): IO[E, Nothing] = ZIO.fail(error)

  /**
   * @see [[zio.ZIO.fiberId]]
   */
  val fiberId: UIO[Fiber.Id] = ZIO.fiberId

  /**
   * @see See [[zio.ZIO.firstSuccessOf]]
   */
  def firstSuccessOf[E, A](
    io: IO[E, A],
    rest: Iterable[IO[E, A]]
  ): IO[E, A] = ZIO.firstSuccessOf(io, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  def flatten[E, A](io: IO[E, IO[E, A]]): IO[E, A] =
    ZIO.flatten(io)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  def foldLeft[E, S, A](in: Iterable[A])(zero: S)(f: (S, A) => IO[E, S]): IO[E, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foldRight]]
   */
  def foldRight[E, S, A](in: Iterable[A])(zero: S)(f: (A, S) => IO[E, S]): IO[E, S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foreach]]
   */
  def foreach[E, A, B](in: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  def foreachPar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  def foreachParN[E, A, B](n: Int)(as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  def foreach_[E, A](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  def foreachPar_[E, A, B](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  def foreachParN_[E, A, B](n: Int)(as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  def forkAll[E, A](as: Iterable[IO[E, A]]): UIO[Fiber[E, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  def forkAll_[E, A](as: Iterable[IO[E, A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  def fromEither[E, A](v: => Either[E, A]): IO[E, A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  def fromFiber[E, A](fiber: => Fiber[E, A]): IO[E, A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  def fromFiberM[E, A](fiber: IO[E, Fiber[E, A]]): IO[E, A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see [[zio.ZIO.fromFunction]]
   */
  def fromFunction[A](f: Any => A): IO[Nothing, A] = ZIO.fromFunction(f)

  /**
   * @see [[zio.ZIO.fromFunctionFuture]]
   */
  def fromFunctionFuture[A](f: Any => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFunctionFuture(f)

  /**
   * @see [[zio.ZIO.fromFunctionM]]
   */
  def fromFunctionM[E, A](f: Any => IO[E, A]): IO[E, A] = ZIO.fromFunctionM(f)

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

  /**
   * @see See [[zio.ZIO.fromOption]]
   */
  def fromOption[A](v: => Option[A]): IO[Unit, A] = ZIO.fromOption(v)

  /**
   * @see See [[zio.ZIO.fromTry]]
   */
  def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  def halt[E](cause: Cause[E]): IO[E, Nothing] = ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.haltWith]]
   */
  def haltWith[E](function: (() => ZTrace) => Cause[E]): IO[E, Nothing] =
    ZIO.haltWith(function)

  /**
   * @see [[zio.ZIO.identity]]
   */
  def identity: IO[Nothing, Any] = ZIO.identity

  /**
   * @see See See [[zio.ZIO.interrupt]]
   */
  val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  def interruptAs(fiberId: Fiber.Id): UIO[Nothing] = ZIO.interruptAs(fiberId)

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  def interruptible[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.interruptible(io)

  /**
   * @see See [[zio.ZIO.interruptibleFork]]
   */
  def interruptibleFork[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.interruptibleFork(io)

  /**
   * @see See [[zio.ZIO.interruptibleForkMask]]
   */
  def interruptibleForkMask[E, A](k: ZIO.InterruptStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.interruptibleForkMask(k)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  def interruptibleMask[E, A](k: ZIO.InterruptStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.interruptibleMask(k)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  def left[E, A](a: A): IO[E, Either[A, Nothing]] = ZIO.left(a)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  def lock[E, A](executor: Executor)(io: IO[E, A]): IO[E, A] =
    ZIO.lock(executor)(io)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C]*]]
   */
  def mapN[E, A, B, C](io1: IO[E, A], io2: IO[E, B])(f: (A, B) => C): IO[E, C] =
    ZIO.mapN(io1, io2)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D]*]]
   */
  def mapN[E, A, B, C, D](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C])(f: (A, B, C) => D): IO[E, D] =
    ZIO.mapN(io1, io2, io3)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D,F]*]]
   */
  def mapN[E, A, B, C, D, F](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C], io4: IO[E, D])(
    f: (A, B, C, D) => F
  ): IO[E, F] =
    ZIO.mapN(io1, io2, io3, io4)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C]*]]
   */
  def mapParN[E, A, B, C](io1: IO[E, A], io2: IO[E, B])(f: (A, B) => C): IO[E, C] =
    ZIO.mapParN(io1, io2)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D]*]]
   */
  def mapParN[E, A, B, C, D](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C])(f: (A, B, C) => D): IO[E, D] =
    ZIO.mapParN(io1, io2, io3)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D,F]*]]
   */
  def mapParN[E, A, B, C, D, F](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C], io4: IO[E, D])(
    f: (A, B, C, D) => F
  ): IO[E, F] =
    ZIO.mapParN(io1, io2, io3, io4)(f)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  def mergeAll[E, A, B](in: Iterable[IO[E, A]])(zero: B)(f: (B, A) => B): IO[E, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  def mergeAllPar[E, A, B](in: Iterable[IO[E, A]])(zero: B)(f: (B, A) => B): IO[E, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.never]]
   */
  val never: UIO[Nothing] = ZIO.never

  /**
   * @see See [[zio.ZIO.nonDaemonMask]]
   */
  def nonDaemonMask[E, A](k: ZIO.DaemonStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.nonDaemonMask(k)

  /**
   * @see See [[zio.ZIO.none]]
   */
  val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see See [[zio.ZIO.partitionM]]
   */
  def partitionM[E, A, B](
    in: Iterable[A]
  )(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[Nothing, (List[E], List[B])] =
    ZIO.partitionM(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMPar]]
   */
  def partitionMPar[E, A, B](
    in: Iterable[A]
  )(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[Nothing, (List[E], List[B])] =
    ZIO.partitionMPar(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMParN]]
   */
  def partitionMParN[E, A, B](n: Int)(
    in: Iterable[A]
  )(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[Nothing, (List[E], List[B])] =
    ZIO.partitionMParN(n)(in)(f)

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  def raceAll[E, A](io: IO[E, A], ios: Iterable[IO[E, A]]): IO[E, A] = ZIO.raceAll(io, ios)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  def reduceAll[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  def reduceAllPar[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.replicate]]
   */
  def replicate[E, A](n: Int)(effect: IO[E, A]): Iterable[IO[E, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see See [[zio.ZIO.require]]
   */
  def require[E, A](error: E): IO[E, Option[A]] => IO[E, A] =
    ZIO.require[Any, E, A](error)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  def reserve[E, A, B](reservation: IO[E, Reservation[Any, E, A]])(use: A => IO[E, B]): IO[E, B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[E, B](b: B): IO[E, Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  def runtime: UIO[Runtime[Any]] = ZIO.runtime

  /**
   *  See [[zio.ZIO.sequence]]
   */
  def sequence[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.sequence(in)

  /**
   *  See [[zio.ZIO.sequencePar]]
   */
  def sequencePar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  def sequenceParN[E, A](n: Int)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[E, A](a: A): IO[E, Option[A]] = ZIO.some(a)

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   * @see See [[zio.ZIO.trace]]
   * */
  def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  def traced[E, A](zio: IO[E, A]): IO[E, A] = ZIO.traced(zio)

  /**
   * @see See [[zio.ZIO.traverse]]
   */
  def traverse[E, A, B](in: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] =
    ZIO.traverse(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
   */
  def traversePar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.traversePar(as)(fn)

  /**
   * Alias for [[ZIO.foreachParN]]
   */
  def traverseParN[E, A, B](
    n: Int
  )(as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.traverse_]]
   */
  def traverse_[E, A](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  def traversePar_[E, A](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  def traverseParN_[E, A](
    n: Int
  )(as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  val unit: UIO[Unit] = ZIO.unit

  /**
   * @see See [[zio.ZIO.uninterruptible]]
   */
  def uninterruptible[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.uninterruptible(io)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  def uninterruptibleMask[E, A](k: ZIO.InterruptStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.unsandbox]]
   */
  def unsandbox[E, A](v: IO[Cause[E], A]): IO[E, A] = ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  def untraced[E, A](zio: IO[E, A]): IO[E, A] = ZIO.untraced(zio)

  /**
   * @see See [[zio.ZIO.validateM]]
   */
  def validateM[E, A, B](in: Iterable[A])(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[::[E], List[B]] =
    ZIO.validateM(in)(f)

  /**
   * @see See [[zio.ZIO.validateMPar]]
   */
  def validateMPar[E, A, B](in: Iterable[A])(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[::[E], List[B]] =
    ZIO.validateMPar(in)(f)

  /**
   * @see See [[zio.ZIO.validateFirstM]]
   */
  def validateFirstM[E, A, B](in: Iterable[A])(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[List[E], B] =
    ZIO.validateFirstM(in)(f)

  /**
   * @see See [[zio.ZIO.validateFirstMPar]]
   */
  def validateFirstMPar[E, A, B](in: Iterable[A])(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[List[E], B] =
    ZIO.validateFirstMPar(in)(f)

  /**
   * @see See [[zio.ZIO.when]]
   */
  def when[E](b: Boolean)(io: IO[E, Any]): IO[E, Unit] =
    ZIO.when(b)(io)

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
  def whenM[E](b: IO[E, Boolean])(io: IO[E, Any]): IO[E, Unit] =
    ZIO.whenM(b)(io)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  val yieldNow: UIO[Unit] = ZIO.yieldNow

  class BracketAcquire_[E](private val acquire: IO[E, Any]) extends AnyVal {
    def apply(release: IO[Nothing, Any]): BracketRelease_[E] =
      new BracketRelease_(acquire, release)
  }
  class BracketRelease_[E](acquire: IO[E, Any], release: IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: IO[E1, B]): IO[E1, B] =
      ZIO.bracket(acquire, (_: Any) => release, (_: Any) => use)
  }

  class BracketAcquire[E, A](private val acquire: IO[E, A]) extends AnyVal {
    def apply(release: A => IO[Nothing, Any]): BracketRelease[E, A] =
      new BracketRelease[E, A](acquire, release)
  }
  class BracketRelease[E, A](acquire: IO[E, A], release: A => IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: A => IO[E1, B]): IO[E1, B] =
      ZIO.bracket(acquire, release, use)
  }

  class BracketForkAcquire_[E](private val acquire: IO[E, Any]) extends AnyVal {
    def apply(release: IO[Nothing, Any]): BracketForkRelease_[E] =
      new BracketForkRelease_(acquire, release)
  }
  class BracketForkRelease_[E](acquire: IO[E, Any], release: IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: IO[E1, B]): IO[E1, B] =
      ZIO.bracketFork(acquire, (_: Any) => release, (_: Any) => use)
  }

  class BracketForkAcquire[E, A](private val acquire: IO[E, A]) extends AnyVal {
    def apply(release: A => IO[Nothing, Any]): BracketForkRelease[E, A] =
      new BracketForkRelease[E, A](acquire, release)
  }
  class BracketForkRelease[E, A](acquire: IO[E, A], release: A => IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: A => IO[E1, B]): IO[E1, B] =
      ZIO.bracketFork(acquire, release, use)
  }

}
