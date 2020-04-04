package zio.stream.experimental

import zio._

abstract class ZTransducer[-R, +E, -I, +O](
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Unit], Chunk[O]]]
) extends ZConduit[R, E, I, O, Unit](push) { self =>

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](that: ZTransducer[R1, E1, O2, O3]): ZTransducer[R1, E1, I, O3] =
    ZTransducer {
      for {
        pushLeft  <- self.push
        pushRight <- that.push
        push = (input: Option[Chunk[I]]) =>
          pushLeft(input).foldM(
            {
              case Left(e)  => ZIO.fail(Left(e))
              case Right(_) => pushRight(None)
            },
            chunk => pushRight(Some(chunk))
          )
      } yield push
    }

  /**
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, Z](that: ZSink[R1, E1, O2, Z]): ZSink[R1, E1, I, Z] =
    ZSink {
      for {
        pushSelf <- self.push
        pushThat <- that.push
        push = (input: Option[Chunk[I]]) =>
          pushSelf(input).foldM(
            {
              case Left(e)  => ZIO.fail(Left(e))
              case Right(_) => pushThat(None)
            },
            chunk => pushThat(Some(chunk))
          )
      } yield push
    }
}

object ZTransducer {
  def apply[R, E, I, O](
    push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Unit], Chunk[O]]]
  ): ZTransducer[R, E, I, O] =
    new ZTransducer(push) {}

  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, Either[E, Unit], Chunk[O]]): ZTransducer[R, E, I, O] =
    ZTransducer(Managed.succeed(push))

  /**
   * A transducer that re-chunks the elements fed to it into chunks of up to
   * `n` elements each.
   */
  def chunkN[I](n: Int): ZTransducer[Any, Nothing, I, I] =
    ZTransducer {
      for {
        buffered <- ZRef.makeManaged[Chunk[I]](Chunk.empty)
        push = { (input: Option[Chunk[I]]) =>
          input match {
            case None => buffered.getAndSet(Chunk.empty)
            case Some(is) =>
              buffered.modify { buf0 =>
                val buf = buf0 ++ is
                if (buf.length >= n) {
                  val (out, buf1) = buf.splitAt(n)
                  Push.emit(out) -> buf1
                } else
                  Push.next -> buf
              }.flatten
          }
        }
      } yield push
    }

  def collectAllWhile[I](p: I => Boolean): ZTransducer[Any, Nothing, I, I] =
    ZTransducer {
      for {
        buffered <- ZRef.makeManaged[Chunk[I]](Chunk.empty)
        push = { (in: Option[Chunk[I]]) =>
          buffered.modify { buf0 =>
            val buf = in.foldLeft(buf0)(_ ++ _)
            val out = buf.takeWhile(p)
            (if (out.isEmpty) Push.next else Push.emit(out)) -> buf.drop(out.length + 1)
          }.flatten
        }
      } yield push
    }

  object Push {
    def emit[A](a: A): UIO[Chunk[A]]                          = IO.succeedNow(Chunk.single(a))
    def emit[A](as: Chunk[A]): UIO[Chunk[A]]                  = IO.succeedNow(as)
    def fail[E](e: E): IO[Either[E, Nothing], Nothing]        = IO.fail(Left(e))
    def halt[E](c: Cause[E]): IO[Either[E, Nothing], Nothing] = IO.halt(c).mapError(Left(_))
    val next: IO[Either[Nothing, Unit], Nothing]              = IO.fail(Right(()))
  }
}
