// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio {
  type Callback[E, A] = ExitResult[E, A] => Unit
  type Canceler       = () => Unit
  type PureCanceler   = () => IO[Nothing, Unit]

  implicit class IOEagerSyntax[A](a: A) {
    def now: IO[Nothing, A]                       = IO.now(a)
    def fail: IO[A, Nothing]                      = IO.fail(a)
    def ensure[AA]: IO[A, Option[AA]] ⇒ IO[A, AA] = IO.require(a)
  }

  implicit class IOLazySyntax[A](a: ⇒ A) {
    def point: IO[Nothing, A]                                  = IO.point(a)
    def sync: IO[Nothing, A]                                   = IO.sync(a)
    def syncException: IO[Exception, A]                        = IO.syncException(a)
    def syncThrowable: IO[Throwable, A]                        = IO.syncThrowable(a)
    def syncCatch[E]: PartialFunction[Throwable, E] ⇒ IO[E, A] = IO.syncCatch(a)
  }
}
