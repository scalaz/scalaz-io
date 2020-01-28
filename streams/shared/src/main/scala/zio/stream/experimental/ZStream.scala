package zio.stream.experimental

import zio._

class ZStream[-R, +E, -M, +B, +A](
  val process: ZManaged[R, Nothing, ZStream.Control[R, E, M, B, A]]
) extends AnyVal
    with Serializable { self =>
  import ZStream.Control

  /**
   * Maps the elements of this stream using a ''pure'' function.
   *
   * @tparam C the value type of the new stream
   * @param f the ''pure'' transformation function
   * @return a stream of transformed values
   */
  def map[C](f: A => C): ZStream[R, E, M, B, C] =
    ZStream {
      self.process.map { control =>
        Control(
          control.pull.map(f),
          control.command
        )
      }
    }
}

object ZStream extends Serializable {

  final case class Control[-R, +E, -M, +B, +A](
    pull: ZIO[R, Either[E, B], A],
    command: M => ZIO[R, E, Any]
  )

  object Pull extends Serializable {
    def end[B](b: => B): IO[Either[Nothing, B], Nothing] = IO.fail(Right(b))

    val endUnit: IO[Either[Nothing, Unit], Nothing] = end(())
  }

  object Command extends Serializable {
    val noop: Any => UIO[Unit] = _ => UIO.unit
  }

  /**
   * Creates a stream from a scoped [[Control]].
   *
   * @tparam R the stream environment type
   * @tparam E the stream error type
   * @tparam M the stream input message type
   * @tparam B the stream exit value type
   * @tparam A the stream value type
   * @param process the scoped control
   * @return a new stream wrapping the scoped control
   */
  def apply[R, E, M, B, A](process: ZManaged[R, Nothing, Control[R, E, M, B, A]]): ZStream[R, E, M, B, A] =
    new ZStream(process)

  /**
   * Creates a single-valued stream from an effect.
   *
   * @tparam R the environment type
   * @tparam E the error type
   * @tparam A the effect value type
   * @param effect the effect
   * @return a single-valued stream
   */
  def fromEffect[R, E, A](effect: ZIO[R, E, A]): ZStream[R, E, Any, Unit, A] =
    ZStream {
      for {
        done <- Ref.make(false).toManaged_
        pull = done.get.flatMap {
          if (_) Pull.endUnit
          else
            (for {
              _ <- done.set(true)
              a <- effect
            } yield a).mapError(Left(_))
        }
      } yield Control(pull, Command.noop)
    }

  /**
   * Creates a single-valued stream from a managed resource.
   *
   * @tparam R the environment type
   * @tparam E the error type
   * @tparam A the managed resource type
   * @param managed the managed resource
   * @return a single-valued stream
   */
  def managed[R, E, A](managed: ZManaged[R, E, A]): ZStream[R, E, Any, Unit, A] =
    ZStream {
      for {
        doneRef   <- Ref.make(false).toManaged_
        finalizer <- ZManaged.finalizerRef[R](_ => UIO.unit)
        pull = ZIO.uninterruptibleMask { restore =>
          doneRef.get.flatMap { done =>
            if (done) IO.fail(Right(()))
            else
              (for {
                _           <- doneRef.set(true)
                reservation <- managed.reserve
                _           <- finalizer.set(reservation.release)
                a           <- restore(reservation.acquire)
              } yield a).mapError(Left(_))
          }
        }
      } yield Control(pull, Command.noop)
    }
}
