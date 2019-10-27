package zio

import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{ Executor, Platform }

object URIO {

  /**
   * @see [[zio.ZIO.absolve]]
   */
  final def absolve[R, A](v: URIO[R, Either[Nothing, A]]): URIO[R, A] =
    ZIO.absolve(v)

  /**
   * @see [[zio.ZIO.access]]
   */
  final def access[R]: ZIO.AccessPartiallyApplied[R] = ZIO.access[R]

  /**
   * @see [[zio.ZIO.accessM]]
   */
  final def accessM[R]: ZIO.AccessMPartiallyApplied[R] = ZIO.accessM[R]

  /**
   * @see [[zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] = ZIO.allowInterrupt

  /**
   * @see [[zio.ZIO.apply]]
   */
  final def apply[A](a: => A): UIO[A] = ZIO.effectTotal(a)

  /**
   * @see bracket in [[zio.ZIO]]
   */
  final def bracket[R, A](acquire: URIO[R, A]): ZIO.BracketAcquire[R, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * @see bracket in [[zio.ZIO]]
   **/
  final def bracket[R, A, B](
    acquire: URIO[R, A],
    release: A => URIO[R, Any],
    use: A => URIO[R, B]
  ): URIO[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * @see bracketExit in [[zio.ZIO]]
   */
  final def bracketExit[R, A](acquire: URIO[R, A]): ZIO.BracketExitAcquire[R, Nothing, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see bracketExit in [[zio.ZIO]]
   */
  final def bracketExit[R, A, B](
    acquire: URIO[R, A],
    release: (A, Exit[Throwable, B]) => URIO[R, Any],
    use: A => URIO[R, B]
  ): URIO[R, B] = ZIO.bracketExit(acquire, release, use)

  /**
   * @see [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[R, A](f: InterruptStatus => URIO[R, A]): URIO[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see [[zio.ZIO.checkSupervised]]
   */
  final def checkSupervised[R, A](f: SuperviseStatus => URIO[R, A]): URIO[R, A] =
    ZIO.checkSupervised(f)

  /**
   * @see [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[R, A](f: TracingStatus => URIO[R, A]): URIO[R, A] =
    ZIO.checkTraced(f)

  /**
   * @see [[zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see [[zio.ZIO.collectAll]]
   */
  final def collectAll[R, A](in: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAll(in)

  /**
   * @see [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[R, A](as: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[R, A](n: Int)(as: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see [[zio.ZIO.collectAllSuccesses]]
   */
  final def collectAllSuccesses[R, A](in: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see [[zio.ZIO.collectAllSuccessesPar]]
   */
  final def collectAllSuccessesPar[R, A](as: Iterable[URIO[R, A]]): URIO[R, List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see [[zio.ZIO.collectAllSuccessesParN]]
   */
  final def collectAllSuccessesParN[E, A](n: Int)(as: Iterable[URIO[E, A]]): URIO[E, List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see [[zio.ZIO.collectAllWith]]
   */
  final def collectAllWith[R, A, B](in: Iterable[URIO[R, A]])(f: PartialFunction[A, B]): URIO[R, List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see [[zio.ZIO.collectAllWithPar]]
   */
  final def collectAllWithPar[R, A, B](as: Iterable[URIO[R, A]])(f: PartialFunction[A, B]): URIO[R, List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see [[zio.ZIO.collectAllWithParN]]
   */
  final def collectAllWithParN[R, A, B](n: Int)(as: Iterable[URIO[R, A]])(f: PartialFunction[A, B]): URIO[R, List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[R, A](f: Fiber.Descriptor => URIO[R, A]): URIO[R, A] =
    ZIO.descriptorWith(f)

  /**
   * @see [[zio.ZIO.die]]
   */
  final def die(t: Throwable): UIO[Nothing] = ZIO.die(t)

  /**
   * @see [[zio.ZIO.dieMessage]]
   */
  final def dieMessage(message: String): UIO[Nothing] = ZIO.dieMessage(message)

  /**
   * @see [[zio.ZIO.done]]
   */
  final def done[A](r: Exit[Nothing, A]): UIO[A] = ZIO.done(r)

  /**
   * @see [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[R, A](register: (URIO[R, A] => Unit) => Unit): URIO[R, A] =
    ZIO.effectAsync(register)

  /**
   * @see [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[R, A](register: (URIO[R, A] => Unit) => Option[URIO[R, A]]): URIO[R, A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * @see [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[R, A](register: (URIO[R, A] => Unit) => URIO[R, Any]): URIO[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[R, A](register: (URIO[R, A] => Unit) => Either[Canceler[R], URIO[R, A]]): URIO[R, A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * @see [[zio.ZIO.effectSuspendTotal]]
   */
  final def effectSuspendTotal[R, A](rio: => URIO[R, A]): URIO[R, A] = new ZIO.EffectSuspendTotalWith(_ => rio)

  /**
   * @see [[zio.ZIO.effectSuspendTotalWith]]
   */
  final def effectSuspendTotalWith[R, A](p: Platform => URIO[R, A]): URIO[R, A] = new ZIO.EffectSuspendTotalWith(p)

  /**
   * @see [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see [[zio.ZIO.environment]]
   */
  final def environment[R]: ZIO[R, Nothing, R] = ZIO.environment

  /**
   * @see [[zio.ZIO.firstSuccessOf]]
   */
  final def firstSuccessOf[R, A](
    rio: URIO[R, A],
    rest: Iterable[URIO[R, A]]
  ): URIO[R, A] = ZIO.firstSuccessOf(rio, rest)

  /**
   * @see [[zio.ZIO.flatten]]
   */
  final def flatten[R, A](taskr: URIO[R, URIO[R, A]]): URIO[R, A] =
    ZIO.flatten(taskr)

  /**
   * @see [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => URIO[R, S]): URIO[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see [[zio.ZIO.foreach]]
   */
  final def foreach[R, A, B](in: Iterable[A])(f: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[R, A, B](as: Iterable[A])(fn: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[R, A, B](n: Int)(as: Iterable[A])(fn: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see [[zio.ZIO.foreach_]]
   */
  final def foreach_[R, A](as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[R, A, B](as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[R, A, B](n: Int)(as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see [[zio.ZIO.forkAll]]
   */
  final def forkAll[R, A](as: Iterable[URIO[R, A]]): ZIO[R, Nothing, Fiber[Nothing, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[R, A](as: Iterable[URIO[R, A]]): ZIO[R, Nothing, Unit] =
    ZIO.forkAll_(as)

  /**
   * @see [[zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Nothing, A]): UIO[A] =
    ZIO.fromEither(v)

  /**
   * @see [[zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Nothing, A]): UIO[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see [[zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: UIO[Fiber[Nothing, A]]): UIO[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see [[zio.ZIO.fromFunction]]
   */
  final def fromFunction[R, A](f: R => A): URIO[R, A] =
    ZIO.fromFunction(f)

  /**
   * @see [[zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[R, A](f: R => UIO[A]): URIO[R, A] =
    ZIO.fromFunctionM(f)

  /**
   * @see [[zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Nothing]): UIO[Nothing] = ZIO.halt(cause)

  /**
   * @see [[zio.ZIO.haltWith]]
   */
  final def haltWith[R](function: (() => ZTrace) => Cause[Nothing]): URIO[R, Nothing] =
    ZIO.haltWith(function)

  /**
   * @see [[zio.ZIO.identity]]
   */
  final def identity[R]: URIO[R, R] = ZIO.identity

  /**
   * @see [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see [[zio.ZIO.interruptible]]
   */
  final def interruptible[R, A](taskr: URIO[R, A]): URIO[R, A] =
    ZIO.interruptible(taskr)

  /**
   * @see [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => URIO[R, A]): URIO[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * @see [[zio.ZIO.lock]]
   */
  final def lock[R, A](executor: Executor)(taskr: URIO[R, A]): URIO[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   *  @see [[zio.ZIO.left]]
   */
  final def left[R, A](a: A): URIO[R, Either[A, Nothing]] = ZIO.left(a)

  /**
   * @see [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[R, A, B](in: Iterable[URIO[R, A]])(zero: B)(f: (B, A) => B): URIO[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[R, A, B](in: Iterable[URIO[R, A]])(zero: B)(f: (B, A) => B): URIO[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * @see [[zio.ZIO.none]]
   */
  final val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see [[zio.ZIO.provide]]
   */
  final def provide[R, A](r: R): URIO[R, A] => UIO[A] =
    ZIO.provide(r)

  /**
   * @see [[zio.ZIO.raceAll]]
   */
  final def raceAll[R, R1 <: R, A](taskr: URIO[R, A], taskrs: Iterable[URIO[R1, A]]): URIO[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * @see [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[R, R1 <: R, A](a: URIO[R, A], as: Iterable[URIO[R1, A]])(f: (A, A) => A): URIO[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[R, R1 <: R, A](a: URIO[R, A], as: Iterable[URIO[R1, A]])(f: (A, A) => A): URIO[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see [[zio.ZIO.replicate]]
   */
  def replicate[R, A](n: Int)(effect: URIO[R, A]): Iterable[URIO[R, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see [[zio.ZIO.reserve]]
   */
  final def reserve[R, A, B](reservation: URIO[R, Reservation[R, Nothing, A]])(use: A => URIO[R, B]): URIO[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[R, B](b: B): RIO[R, Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see [[zio.ZIO.runtime]]
   */
  final def runtime[R]: URIO[R, Runtime[R]] = ZIO.runtime

  /**
   * @see [[zio.ZIO.sleep]]
   */
  final def sleep(duration: Duration): URIO[Clock, Unit] = ZIO.sleep(duration)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[R, A](a: A): URIO[R, Option[A]] = ZIO.some(a)

  /**
   * @see [[zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   * @see [[zio.ZIO.interruptChildren]]
   */
  final def interruptChildren[R, A](taskr: URIO[R, A]): URIO[R, A] = ZIO.interruptChildren(taskr)

  /**
   * @see [[zio.ZIO.handleChildrenWith]]
   */
  final def handleChildrenWith[R, A](
    taskr: URIO[R, A]
  )(supervisor: IndexedSeq[Fiber[Any, Any]] => URIO[R, Any]): URIO[R, A] =
    ZIO.handleChildrenWith(taskr)(supervisor)

  /**
   *  [[zio.ZIO.sequence]]
   */
  final def sequence[R, A](in: Iterable[URIO[R, A]]): URIO[R, List[A]] = ZIO.sequence(in)

  /**
   *  [[zio.ZIO.sequencePar]]
   */
  final def sequencePar[R, A](as: Iterable[URIO[R, A]]): URIO[R, List[A]] = ZIO.sequencePar(as)

  /**
   *  [[zio.ZIO.sequenceParN]]
   */
  final def sequenceParN[R, A](n: Int)(as: Iterable[URIO[R, A]]): URIO[R, List[A]] = ZIO.sequenceParN(n)(as)

  /**
   * @see [[zio.ZIO.supervised]]
   */
  final def supervised[R, A](taskr: URIO[R, A]): URIO[R, A] = ZIO.supervised(taskr)

  /**
   * @see [[zio.ZIO.superviseStatus]]
   */
  final def superviseStatus[R, A](status: SuperviseStatus)(taskr: URIO[R, A]): URIO[R, A] =
    ZIO.superviseStatus(status)(taskr)

  /**
   * @see [[zio.ZIO.swap]]
   */
  final def swap[R, A, B](implicit ev: R <:< (A, B)): URIO[R, (B, A)] = ZIO.swap

  /**
   * @see [[zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see [[zio.ZIO.traced]]
   */
  final def traced[R, A](zio: URIO[R, A]): URIO[R, A] = ZIO.traced(zio)

  /**
   * @see [[zio.ZIO.traverse]]
   */
  final def traverse[R, A, B](in: Iterable[A])(f: A => URIO[R, B]): URIO[R, List[B]] = ZIO.traverse(in)(f)

  /**
   * @see [[zio.ZIO.traversePar]]
   */
  final def traversePar[R, A, B](as: Iterable[A])(fn: A => URIO[R, B]): URIO[R, List[B]] = ZIO.traversePar(as)(fn)

  /**
   * Alias for [[ZIO.foreachParN]]
   */
  final def traverseParN[R, A, B](n: Int)(as: Iterable[A])(fn: A => URIO[R, B]): URIO[R, List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see [[zio.ZIO.traverse_]]
   */
  final def traverse_[R, A](as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] = ZIO.traverse_(as)(f)

  /**
   * @see [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[R, A](as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] = ZIO.traversePar_(as)(f)

  /**
   * @see [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[R, A](n: Int)(as: Iterable[A])(f: A => URIO[R, Any]): URIO[R, Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * @see [[zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[R, A](taskr: URIO[R, A]): URIO[R, A] = ZIO.uninterruptible(taskr)

  /**
   * @see [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => URIO[R, A]): URIO[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see [[zio.ZIO.unsandbox]]
   */
  final def unsandbox[R, A](v: IO[Cause[Nothing], A]): URIO[R, A] = ZIO.unsandbox(v)

  /**
   * @see [[zio.ZIO.unsupervised]]
   */
  final def unsupervised[R, A](rio: URIO[R, A]): URIO[R, A] = ZIO.unsupervised(rio)

  /**
   * @see [[zio.ZIO.untraced]]
   */
  final def untraced[R, A](zio: URIO[R, A]): URIO[R, A] = ZIO.untraced(zio)

  /**
   * @see [[zio.ZIO.when]]
   */
  final def when[R](b: Boolean)(rio: URIO[R, Any]): URIO[R, Unit] = ZIO.when(b)(rio)

  /**
   * @see [[zio.ZIO.whenCase]]
   */
  final def whenCase[R, A](a: A)(pf: PartialFunction[A, ZIO[R, Nothing, Any]]): URIO[R, Unit] = ZIO.whenCase(a)(pf)

  /**
   * @see [[zio.ZIO.whenCaseM]]
   */
  final def whenCaseM[R, A](a: URIO[R, A])(pf: PartialFunction[A, URIO[R, Any]]): URIO[R, Unit] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see [[zio.ZIO.whenM]]
   */
  final def whenM[R](b: URIO[R, Boolean])(rio: URIO[R, Any]): URIO[R, Unit] = ZIO.whenM(b)(rio)

  /**
   * @see [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

  /**
   * @see [[zio.ZIO._1]]
   */
  final def _1[R, A, B](implicit ev: R <:< (A, B)): URIO[R, A] = ZIO._1

  /**
   * @see [[zio.ZIO._2]]
   */
  final def _2[R, A, B](implicit ev: R <:< (A, B)): URIO[R, B] = ZIO._2

}
