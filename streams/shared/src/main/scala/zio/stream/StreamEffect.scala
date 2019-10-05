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

package zio.stream

import java.io.{ IOException, InputStream }

import zio._

private[stream] class StreamEffect[-R, +E, +A](val processEffect: ZManaged[R, E, () => A])
    extends ZStream[R, E, A](
      processEffect.map { thunk =>
        UIO.effectTotal {
          try UIO.succeed(thunk())
          catch {
            case StreamEffect.Failure(e) => IO.fail(Some(e.asInstanceOf[E]))
            case StreamEffect.End        => IO.fail(None)
          }
        }.flatten
      }
    ) { self =>

  override def collect[B](pf: PartialFunction[A, B]): StreamEffect[R, E, B] =
    StreamEffect[R, E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal { () =>
          {
            var b = null.asInstanceOf[B]

            while (b == null) {
              b = pf.applyOrElse(thunk(), (_: A) => null.asInstanceOf[B])
            }

            b
          }
        }
      }
    }

  override def collectWhile[B](pred: PartialFunction[A, B]): StreamEffect[R, E, B] =
    StreamEffect[R, E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var done = false

          () => {
            if (done) StreamEffect.end
            else pred.applyOrElse(thunk(), (_: A) => { done = true; StreamEffect.end })
          }
        }
      }
    }

  override def dropWhile(pred: A => Boolean): StreamEffect[R, E, A] =
    StreamEffect[R, E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var drop = true

          @annotation.tailrec
          def pull(): A = {
            val a = thunk()
            if (!drop) a
            else if (!pred(a)) {
              drop = false
              a
            } else pull()
          }

          () => pull()
        }
      }
    }

  override def filter(pred: A => Boolean): StreamEffect[R, E, A] =
    StreamEffect[R, E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          @annotation.tailrec
          def pull(): A = {
            val a = thunk()
            if (pred(a)) a else pull()
          }

          () => pull()
        }
      }
    }

  final override def foldWhileManaged[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): ZManaged[R, E, S] =
    processEffect.flatMap { thunk =>
      def fold(): Either[E, S] = {
        var state = s
        var done  = false
        var error = null.asInstanceOf[E]

        while (!done && error == null && cont(state)) {
          try {
            val a = thunk()
            state = f(state, a)
          } catch {
            case StreamEffect.Failure(e) => error = e.asInstanceOf[E]
            case StreamEffect.End        => done = true
          }
        }

        if (error == null) Right(state) else Left(error)
      }

      Managed.effectTotal(Managed.fromEither(fold())).flatten
    }

  override def map[B](f0: A => B): StreamEffect[R, E, B] =
    StreamEffect[R, E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal { () =>
          f0(thunk())
        }
      }
    }

  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamEffect[R, E, B] =
    StreamEffect[R, E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var state = s1

          () => {
            val (s2, b) = f1(state, thunk())
            state = s2
            b
          }
        }
      }
    }

  override def mapConcat[B](f: A => Chunk[B]): StreamEffect[R, E, B] =
    StreamEffect[R, E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var chunk: Chunk[B] = Chunk.empty
          var index           = 0

          () => {
            while (index == chunk.length) {
              chunk = f(thunk())
              index = 0
            }
            val b = chunk(index)
            index += 1
            b
          }
        }
      }
    }

  override def run[R1 <: R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R1, E1, A0, A1, B]): ZIO[R1, E1, B] =
    sink match {
      case sink: SinkPure[E1, A0, A1, B] =>
        foldWhileManaged[A1, sink.State](sink.initialPure)(sink.cont)(sink.stepPure).use[R1, E1, B] { state =>
          ZIO.fromEither(sink.extractPure(state).map(_._1))
        }

      case sink: ZSink[R1, E1, A0, A1, B] => super.run(sink)
    }

  override def take(n: Int): StreamEffect[R, E, A] =
    StreamEffect[R, E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var counter = 0

          () => {
            if (counter >= n) StreamEffect.end
            else {
              counter += 1
              thunk()
            }
          }
        }
      }
    }

  override def takeWhile(pred: A => Boolean): StreamEffect[R, E, A] =
    StreamEffect[R, E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal { () =>
          {
            val a = thunk()
            if (pred(a)) a
            else StreamEffect.end
          }
        }
      }
    }

  override def transduce[R1 <: R, E1 >: E, A1 >: A, B](
    sink: ZSink[R1, E1, A1, A1, B]
  ): ZStream[R1, E1, B] =
    sink match {
      case sink: SinkPure[E1, A1, A1, B] =>
        StreamEffect[R1, E1, B] {

          self.processEffect.flatMap { thunk =>
            Managed.effectTotal {
              var done                 = false
              var leftovers: Chunk[A1] = Chunk.empty

              () => {
                def go(state: sink.State, dirty: Boolean): B =
                  if (!dirty) {
                    if (done) StreamEffect.end
                    else if (leftovers.nonEmpty) {
                      val (newState, newLeftovers) = sink.stepChunkPure(state, leftovers)
                      leftovers = newLeftovers
                      go(newState, true)
                    } else {
                      val a = thunk()
                      go(sink.stepPure(state, a), true)
                    }
                  } else {
                    if (done || !sink.cont(state)) {
                      sink.extractPure(state) match {
                        case Left(e) => StreamEffect.fail(e)
                        case Right((b, newLeftovers)) =>
                          leftovers = leftovers ++ newLeftovers
                          b
                      }
                    } else {
                      try go(sink.stepPure(state, thunk()), true)
                      catch {
                        case StreamEffect.End =>
                          done = true
                          sink.extractPure(state) match {
                            case Left(e) => StreamEffect.fail(e)
                            case Right((b, newLeftovers)) =>
                              leftovers = leftovers ++ newLeftovers
                              b
                          }
                      }
                    }
                  }

                go(sink.initialPure, false)
              }
            }
          }
        }
      case sink: ZSink[R1, E1, A1, A1, B] => super.transduce(sink)
    }

  override final def toInputStream(
    implicit ev0: E <:< Throwable,
    ev1: A <:< Byte
  ): ZManaged[R, E, java.io.InputStream] =
    for {
      pull <- processEffect
      javaStream = new java.io.InputStream {
        override def read(): Int =
          try {
            pull().toInt
          } catch {
            case StreamEffect.End        => -1
            case StreamEffect.Failure(e) => throw e.asInstanceOf[E]
          }
      }
    } yield javaStream
}

private[stream] object StreamEffect extends Serializable {

  case class Failure[E](e: E) extends Throwable(e.toString, null, true, false) {
    override def fillInStackTrace() = this
  }

  case object End extends Throwable("stream end", null, true, false) {
    override def fillInStackTrace() = this
  }

  def end[A]: A = throw End

  def fail[E, A](e: E): A = throw Failure(e)

  final val empty: StreamEffect[Any, Nothing, Nothing] =
    StreamEffect[Any, Nothing, Nothing] {
      Managed.effectTotal { () =>
        end
      }
    }

  final def apply[R, E, A](pull: ZManaged[R, E, () => A]): StreamEffect[R, E, A] =
    new StreamEffect[R, E, A](pull)

  final def fromChunk[A](c: Chunk[A]): StreamEffect[Any, Nothing, A] =
    StreamEffect[Any, Nothing, A] {
      Managed.effectTotal {
        var index = 0
        val len   = c.length

        () => {
          if (index >= len) end
          else {
            val i = index
            index += 1
            c(i)
          }
        }
      }
    }

  final def fromIterable[A](as: Iterable[A]): StreamEffect[Any, Nothing, A] =
    StreamEffect[Any, Nothing, A] {
      Managed.effectTotal {
        val thunk = as.iterator

        () => if (thunk.hasNext) thunk.next() else end
      }
    }

  final def fromIterator[R, E, A](iterator: ZManaged[R, E, Iterator[A]]): StreamEffect[R, E, A] =
    StreamEffect[R, E, A] {
      iterator.flatMap { iterator =>
        Managed.effectTotal { () =>
          if (iterator.hasNext) iterator.next() else end
        }
      }
    }

  final def fromInputStream(
    is: InputStream,
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): StreamEffectChunk[Any, IOException, Byte] =
    StreamEffectChunk[Any, IOException, Byte] {
      StreamEffect[Any, IOException, Chunk[Byte]] {
        Managed.effectTotal {
          def pull(): Chunk[Byte] = {
            val buf = Array.ofDim[Byte](chunkSize)
            try {
              val bytesRead = is.read(buf)
              if (bytesRead < 0) end
              else if (0 < bytesRead && bytesRead < buf.length) Chunk.fromArray(buf).take(bytesRead)
              else Chunk.fromArray(buf)
            } catch {
              case e: IOException => fail(e)
            }
          }

          () => pull()
        }
      }
    }

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  final def unfold[S, A](s: S)(f0: S => Option[(A, S)]): StreamEffect[Any, Nothing, A] =
    StreamEffect[Any, Nothing, A] {
      Managed.effectTotal {
        var state = s

        () => {
          val opt = f0(state)
          if (opt.isDefined) {
            val res = opt.get
            state = res._2
            res._1
          } else end
        }
      }
    }

  final def succeed[A](a: A): StreamEffect[Any, Nothing, A] =
    StreamEffect[Any, Nothing, A] {
      Managed.effectTotal {
        var done = false

        () => {
          if (!done) {
            done = true
            a
          } else end
        }
      }
    }
}
