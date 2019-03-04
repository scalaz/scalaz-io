package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio._
import scalaz.zio.stream.{ Sink, Stream }

package object reactiveStreams {

  final implicit class streamToPublisher[R, E <: Throwable, A](val src: Stream[R, E, A]) extends AnyVal {
    def toPublisher: ZIO[R, Nothing, Publisher[A]] =
      StreamPublisher.sinkToPublisher(src)
  }

  final implicit class sinkToSubscriber[R, E <: Throwable, A0, A, B](val sink: Sink[R, E, A0, A, B]) extends AnyVal {
    def toSubscriber(qSize: Int = 10): ZIO[R, E, (Subscriber[A], Task[B])] =
      SinkSubscriber.sinkToSubscriber(sink, qSize)
  }
}
