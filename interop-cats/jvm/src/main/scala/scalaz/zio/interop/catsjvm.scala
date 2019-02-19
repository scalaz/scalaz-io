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

package scalaz.zio
package interop

import cats.effect.{ Concurrent, ContextShift, Effect, ExitCase }
import cats.{ effect, _ }
import scalaz.zio.{ clock => zioClock }
import scalaz.zio.clock.Clock

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, NANOSECONDS, TimeUnit }

abstract class CatsPlatform extends CatsInstances {
  val console = interop.console.cats
}

abstract class CatsInstances extends CatsInstances1 {
  implicit def ioContextShift[E]: ContextShift[IO[E, ?]] = new ContextShift[IO[E, ?]] {
    override def shift: IO[E, Unit] =
      IO.yieldNow

    override def evalOn[A](ec: ExecutionContext)(fa: IO[E, A]): IO[E, A] =
      fa.on(ec)
  }

  implicit def ioTimer[R <: Clock, E]: effect.Timer[ZIO[R, E, ?]] = new effect.Timer[ZIO[R, E, ?]] {
    override def clock: cats.effect.Clock[ZIO[R, E, ?]] = new effect.Clock[ZIO[R, E, ?]] {
      override def monotonic(unit: TimeUnit): ZIO[R, E, Long] =
        zioClock.nanoTime.map(unit.convert(_, NANOSECONDS))

      override def realTime(unit: TimeUnit): ZIO[R, E, Long] =
        zioClock.currentTime(unit)
    }

    override def sleep(duration: FiniteDuration): ZIO[R, E, Unit] =
      zioClock.sleep(scalaz.zio.duration.Duration.fromNanos(duration.toNanos))
  }

  implicit val taskEffectInstances: effect.ConcurrentEffect[Task] with SemigroupK[Task] =
    new CatsConcurrentEffect

  implicit val taskParallelInstance: Parallel[Task, Util.Par] =
    parallelInstance(taskEffectInstances)
}

sealed abstract class CatsInstances1 extends CatsInstances2 {
  implicit def ioMonoidInstances[E: Monoid]: MonadError[IO[E, ?], E] with Bifunctor[IO] with Alternative[IO[E, ?]] =
    new CatsAlternative[E] with CatsBifunctor

  implicit def parallelInstance[E](implicit M: Monad[IO[E, ?]]): Parallel[IO[E, ?], ParIO[E, ?]] =
    new CatsParallel[E](M)
}

sealed abstract class CatsInstances2 {
  implicit def ioInstances[E]: MonadError[IO[E, ?], E] with Bifunctor[IO] with SemigroupK[IO[E, ?]] =
    new CatsMonadError[E] with CatsSemigroupK[E] with CatsBifunctor
}

private class CatsConcurrentEffect extends CatsConcurrent with effect.ConcurrentEffect[Task] {
  override final def runCancelable[A](
    fa: Task[A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[effect.CancelToken[Task]] =
    effect.SyncIO {
      this.unsafeRun {
        fa.fork.flatMap { f =>
          f.await
            .flatMap(exit => IO.sync(cb(exitToEither(exit)).unsafeRunAsync(_ => ())))
            .fork
            .const(f.interrupt.void)
        }
      }
    }

  override final def toIO[A](fa: Task[A]): effect.IO[A] =
    effect.ConcurrentEffect.toIOFromRunCancelable(fa)(this)
}

private class CatsConcurrent extends CatsEffect with Concurrent[Task] {

  private[this] final def toFiber[A](f: Fiber[Throwable, A]): effect.Fiber[Task, A] = new effect.Fiber[Task, A] {
    override final val cancel: Task[Unit] = f.interrupt.void

    override final val join: Task[A] = f.join
  }

  override final def liftIO[A](ioa: cats.effect.IO[A]): Task[A] =
    Concurrent.liftIO(ioa)(this)

  override final def cancelable[A](k: (Either[Throwable, A] => Unit) => effect.CancelToken[Task]): Task[A] =
    IO.asyncInterrupt { (kk: Task[A] => Unit) =>
      val token: effect.CancelToken[Task] = {
        k(e => kk(eitherToIO(e)))
      }

      Left(token.keepNone)
    }

  override final def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
    racePair(fa, fb).flatMap {
      case Left((a, fiberB)) =>
        fiberB.cancel.const(Left(a))
      case Right((fiberA, b)) =>
        fiberA.cancel.const(Right(b))
    }

  override final def start[A](fa: Task[A]): Task[effect.Fiber[Task, A]] =
    fa.fork.map(toFiber)

  override final def racePair[A, B](
    fa: Task[A],
    fb: Task[B]
  ): Task[Either[(A, effect.Fiber[Task, B]), (effect.Fiber[Task, A], B)]] =
    (fa raceWith fb)(
      { case (l, f) => l.fold(f.interrupt *> IO.halt(_), IO.succeed).map(lv => Left((lv, toFiber(f)))) },
      { case (r, f) => r.fold(f.interrupt *> IO.halt(_), IO.succeed).map(rv => Right((toFiber(f), rv))) }
    )
}

private class CatsEffect extends CatsMonadError[Throwable] with Effect[Task] with CatsSemigroupK[Throwable] with RTS {
  @inline final protected[this] def exitToEither[A](e: Exit[Throwable, A]): Either[Throwable, A] =
    e.fold(_.failures[Throwable] match {
      case t :: Nil => Left(t)
      case _        => e.toEither
    }, Right(_))

  @inline final protected[this] def eitherToIO[A]: Either[Throwable, A] => Task[A] = {
    case Left(t)  => IO.fail(t)
    case Right(r) => IO.succeed(r)
  }

  @inline final private[this] def exitToExitCase[A]: Exit[Throwable, A] => ExitCase[Throwable] = {
    case Exit.Success(_)                          => ExitCase.Completed
    case Exit.Failure(cause) if cause.interrupted => ExitCase.Canceled
    case Exit.Failure(cause) =>
      cause.failureOrCause match {
        case Left(t) => ExitCase.Error(t)
        case _       => ExitCase.Error(FiberFailure(cause))
      }
  }

  override final def never[A]: Task[A] =
    IO.never

  override final def runAsync[A](
    fa: Task[A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[Unit] =
    effect.SyncIO {
      this.unsafeRunAsync(fa) { exit =>
        cb(exitToEither(exit)).unsafeRunAsync(_ => ())
      }
    }

  override final def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    IO.async { (kk: Task[A] => Unit) =>
      k(eitherToIO andThen kk)
    }

  override final def asyncF[A](k: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    IO.asyncM { (kk: Task[A] => Unit) =>
      k(eitherToIO andThen kk).keepNone
    }

  override final def suspend[A](thunk: => Task[A]): Task[A] =
    IO.flatten(IO.sync(thunk))

  override final def delay[A](thunk: => A): Task[A] =
    IO.sync(thunk)

  override final def bracket[A, B](acquire: Task[A])(use: A => Task[B])(
    release: A => Task[Unit]
  ): Task[B] =
    IO.bracket(acquire)(release(_).keepNone)(use)

  override final def bracketCase[A, B](
    acquire: Task[A]
  )(use: A => Task[B])(release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] =
    IO.bracket0[Any, Throwable, A, B](acquire) { (a, exit) =>
      val exitCase = exitToExitCase(exit)
      release(a, exitCase).keepNone
    }(use)

  override def uncancelable[A](fa: Task[A]): Task[A] =
    fa.uninterruptible

  override final def guarantee[A](fa: Task[A])(finalizer: Task[Unit]): Task[A] =
    fa.ensuring(finalizer.keepNone)
}

private class CatsMonad[E] extends Monad[IO[E, ?]] {
  override final def pure[A](a: A): IO[E, A]                                 = IO.succeed(a)
  override final def map[A, B](fa: IO[E, A])(f: A => B): IO[E, B]            = fa.map(f)
  override final def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] = fa.flatMap(f)
  override final def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
    f(a).flatMap {
      case Left(l)  => tailRecM(l)(f)
      case Right(r) => IO.succeed(r)
    }
}

private class CatsMonadError[E] extends CatsMonad[E] with MonadError[IO[E, ?], E] {
  override final def handleErrorWith[A](fa: IO[E, A])(f: E => IO[E, A]): IO[E, A] = fa.catchAll(f)
  override final def raiseError[A](e: E): IO[E, A]                                = IO.fail(e)
}

/** lossy, throws away errors using the "first success" interpretation of SemigroupK */
private trait CatsSemigroupK[E] extends SemigroupK[IO[E, ?]] {
  override final def combineK[A](a: IO[E, A], b: IO[E, A]): IO[E, A] = a.orElse(b)
}

private class CatsAlternative[E: Monoid] extends CatsMonadError[E] with Alternative[IO[E, ?]] {
  override final def combineK[A](a: IO[E, A], b: IO[E, A]): IO[E, A] =
    a.catchAll { e1 =>
      b.catchAll { e2 =>
        IO.fail(Monoid[E].combine(e1, e2))
      }
    }
  override final def empty[A]: IO[E, A] = raiseError(Monoid[E].empty)
}

trait CatsBifunctor extends Bifunctor[IO] {
  override final def bimap[A, B, C, D](fab: IO[A, B])(f: A => C, g: B => D): IO[C, D] =
    fab.bimap(f, g)
}

private class CatsParallel[E](final override val monad: Monad[IO[E, ?]]) extends Parallel[IO[E, ?], ParIO[E, ?]] {

  final override val applicative: Applicative[ParIO[E, ?]] =
    new CatsParApplicative[E]

  final override val sequential: ParIO[E, ?] ~> IO[E, ?] =
    new (ParIO[E, ?] ~> IO[E, ?]) { def apply[A](fa: ParIO[E, A]): IO[E, A] = Par.unwrap(fa) }

  final override val parallel: IO[E, ?] ~> ParIO[E, ?] =
    new (IO[E, ?] ~> ParIO[E, ?]) { def apply[A](fa: IO[E, A]): ParIO[E, A] = Par(fa) }
}

private class CatsParApplicative[E] extends Applicative[ParIO[E, ?]] {

  final override def pure[A](x: A): ParIO[E, A] =
    Par(IO.succeed(x))

  final override def map2[A, B, Z](fa: ParIO[E, A], fb: ParIO[E, B])(f: (A, B) => Z): ParIO[E, Z] =
    Par(Par.unwrap(fa).zipPar(Par.unwrap(fb)).map(f.tupled))

  final override def ap[A, B](ff: ParIO[E, A => B])(fa: ParIO[E, A]): ParIO[E, B] =
    Par(Par.unwrap(ff).flatMap(Par.unwrap(fa).map))

  final override def product[A, B](fa: ParIO[E, A], fb: ParIO[E, B]): ParIO[E, (A, B)] =
    map2(fa, fb)(_ -> _)

  final override def map[A, B](fa: ParIO[E, A])(f: A => B): ParIO[E, B] =
    Par(Par.unwrap(fa).map(f))

  final override def unit: ParIO[E, Unit] =
    Par(IO.unit)
}
