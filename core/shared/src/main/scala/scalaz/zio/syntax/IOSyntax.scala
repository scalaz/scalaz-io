package scalaz.zio.syntax
import scalaz.zio.{ Fiber, IO }

object IOSyntax {
  final class IOCreationLazySyntax[A](val a: () => A) extends AnyVal {
    def point: IO[Nothing, A]                                   = IO.point(a())
    def sync: IO[Nothing, A]                                    = IO.sync(a())
    def syncException: IO[Exception, A]                         = IO.syncException(a())
    def syncThrowable: IO[Throwable, A]                         = IO.syncThrowable(a())
    def syncCatch[E]: PartialFunction[Throwable, E] => IO[E, A] = IO.syncCatch(a())
  }

  final class IOCreationEagerSyntax[A](val a: A) extends AnyVal {
    def now: IO[Nothing, A]                         = IO.now(a)
    def fail: IO[A, Nothing]                        = IO.fail(a)
    def require[AA]: IO[A, Option[AA]] => IO[A, AA] = IO.require(a)
  }

  final class IOFlattenSyntax[E, A](val io: IO[E, IO[E, A]]) extends AnyVal {
    def flatten: IO[E, A] = IO.flatten(io)
  }

  final class IOAbsolveSyntax[E, A](val io: IO[E, Either[E, A]]) extends AnyVal {
    def absolved: IO[E, A] = IO.absolve(io)
  }

  final class IOUnsandboxedSyntax[E, A](val io: IO[Either[List[Throwable], E], A]) extends AnyVal {
    def unsandboxed: IO[E, A] = IO.unsandbox(io)
  }

  final class IOIterableSyntax[E, A](val ios: Iterable[IO[E, A]]) extends AnyVal {
    def mergeAll[B](zero: B, f: (B, A) => B): IO[E, B] = IO.mergeAll(ios)(zero, f)
    def parAll: IO[E, List[A]]                         = IO.parAll(ios)
    def forkAll: IO[Nothing, Fiber[E, List[A]]]        = IO.forkAll(ios)
    def sequence: IO[E, List[A]]                       = IO.sequence(ios)
  }

  final class IOSyntax[E, A](val io: IO[E, A]) extends AnyVal {
    def raceAll(ios: Iterable[IO[E, A]]): IO[E, A] = IO.raceAll(io, ios)
    def supervise: IO[E, A]                        = IO.supervise(io)
  }

  final class IOTuple2[E, A, B](val ios2: (IO[E, A], IO[E, B])) extends AnyVal {
    def map2[C](f: (A, B) => C): IO[E, C] = ios2._1.flatMap(a => ios2._2.map(f(a, _)))
  }

  final class IOTuple3[E, A, B, C](val ios3: (IO[E, A], IO[E, B], IO[E, C])) extends AnyVal {
    def map3[D](f: (A, B, C) => D): IO[E, D] =
      for {
        a <- ios3._1
        b <- ios3._2
        c <- ios3._3
      } yield f(a, b, c)
  }

  final class IOTuple4[E, A, B, C, D](val ios4: (IO[E, A], IO[E, B], IO[E, C], IO[E, D])) extends AnyVal {
    def map4[F](f: (A, B, C, D) => F): IO[E, F] =
      for {
        a <- ios4._1
        b <- ios4._2
        c <- ios4._3
        d <- ios4._4
      } yield f(a, b, c, d)
  }
}
