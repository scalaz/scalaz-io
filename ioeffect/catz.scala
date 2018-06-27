package scalaz.ioeffect

import cats.effect
import cats.effect.Effect
import cats.syntax.all._

import scala.util.control.NonFatal

object catz extends RTS {

  implicit val catsEffectInstance: Effect[Task] = new Effect[Task] {
    def runAsync[A](
      fa: Task[A]
    )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.IO[Unit] =
      effect.IO {
        unsafePerformIO(
          fa.attempt[Throwable].flatMap { a =>
            IO.async[Throwable, Unit] { r =>
              cb(a.toEither).unsafeRunAsync {
                case Right(r2) => r(ExitResult.Completed(r2))
                case Left(l2)  => r(ExitResult.Failed(l2))
              }
            }
          }
        )
      }.attempt.void

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] = {
      val kk = k.compose[ExitResult[Throwable, A] => Unit] {
        _.compose[Either[Throwable, A]] {
          case Left(t)  => ExitResult.Failed(t)
          case Right(r) => ExitResult.Completed(r)
        }
      }

      IO.async(kk)
    }

    def suspend[A](thunk: =>Task[A]): Task[A] = IO.suspend(
      try {
        thunk
      } catch {
        case NonFatal(e) => IO.fail(e)
      }
    )

    def raiseError[A](e: Throwable): Task[A] = IO.fail(e)

    def handleErrorWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] =
      fa.catchAll(f)

    def pure[A](x: A): Task[A] = IO.now(x)

    def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)

    //LOL monad "law"
    def tailRecM[A, B](a: A)(f: A => Task[Either[A, B]]): Task[B] =
      f(a).flatMap {
        case Left(l)  => tailRecM(l)(f)
        case Right(r) => IO.now(r)
      }
  }

}
