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

import zio._

private[stream] trait StreamPure[+A] extends ZStream[Any, Nothing, A] { self =>
  import ZStream.Fold

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def dropWhile(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => cont(tp._2)) {
          case ((true, s), a) if pred(a) => true  -> s
          case ((_, s), a)               => false -> f(s, a)
        }
        ._2

    override def fold[R, E, A1 >: A, S]: Fold[R, E, A1, S] =
      StreamPure.super.dropWhile(pred).fold[R, E, A1, S]
  }

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  override def filter(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self.foldPureLazy[A, S](s)(cont) { (s, a) =>
        if (pred(a)) f(s, a)
        else s
      }

    override def fold[R, E, A1 >: A, S]: Fold[R, E, A1, S] =
      StreamPure.super.filter(pred).fold[R, E, A1, S]
  }

  override def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): ZManaged[Any, Nothing, S] =
    ZManaged.succeedLazy(foldPureLazy(s)(_ => true)(f))

  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S

  /**
   * Maps over elements of the stream with the specified function.
   */
  override def map[B](f0: A => B): StreamPure[B] = new StreamPure[B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy[A, S](s)(cont)((s, a) => f(s, f0(a)))

    override def fold[R, E, B1 >: B, S]: Fold[R, E, B1, S] =
      StreamPure.super.map(f0).fold[R, E, B1, S]
  }

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamPure[B] = new StreamPure[B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self
        .foldPureLazy[A, (S, S1)](s -> s1)(tp => cont(tp._1)) {
          case ((s, s1), a) =>
            val (s2, b) = f1(s1, a)

            f(s, b) -> s2
        }
        ._1

    override def fold[R, E, B1 >: B, S]: Fold[R, E, B1, S] =
      StreamPure.super.mapAccum(s1)(f1).fold[R, E, B1, S]
  }

  override def mapConcat[B](f0: A => Chunk[B]): StreamPure[B] = new StreamPure[B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy(s)(cont)((s, a) => f0(a).foldLeftLazy(s)(cont)(f))

    override def fold[R, E, B1 >: B, S]: Fold[R, E, B1, S] =
      StreamPure.super.mapConcat(f0).fold[R, E, B1, S]
  }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def takeWhile(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => tp._1 && cont(tp._2)) {
          case ((_, s), a) =>
            if (pred(a)) true -> f(s, a)
            else false        -> s
        }
        ._2

    override def fold[R, E, A1 >: A, S]: Fold[R, E, A1, S] =
      StreamPure.super.takeWhile(pred).fold[R, E, A1, S]
  }

  override def run[R, E, A0, A1 >: A, B](sink: ZSink[R, E, A0, A1, B]): ZIO[R, E, B] =
    sink match {
      case sink: SinkPure[E, A0, A1, B] =>
        ZIO.fromEither(
          sink.extractPure(
            ZSink.Step.state(
              foldPureLazy[A1, ZSink.Step[sink.State, A0]](sink.initialPure)(ZSink.Step.cont) { (s, a) =>
                sink.stepPure(ZSink.Step.state(s), a)
              }
            )
          )
        )

      case sink: ZSink[R, E, A0, A1, B] => super.run(sink)
    }

  override def zipWithIndex: StreamPure[(A, Int)] = new StreamPure[(A, Int)] {
    override def foldPureLazy[A1 >: (A, Int), S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (S, Int)]((s, 0))(tp => cont(tp._1)) {
          case ((s, index), a) => (f(s, (a, index)), index + 1)
        }
        ._1

    override def fold[R, E, A1 >: (A, Int), S]: Fold[R, E, A1, S] =
      StreamPure.super.zipWithIndex.fold[R, E, A1, S]
  }
}

private[stream] object StreamPure extends Serializable {

  /**
   * Returns the empty stream.
   */
  final val empty: StreamPure[Nothing] = new StreamPure[Nothing] {
    override def fold[R1, E1, A1, S]: ZStream.Fold[R1, E1, A1, S] =
      ZManaged.succeedLazy((s, _, _) => ZManaged.succeed(s))

    override def foldPureLazy[A, S](s: S)(cont: S => Boolean)(f: (S, A) => S): S = s
  }

  /**
   * Constructs a pure stream from the specified `Iterable`.
   */
  final def fromIterable[A](it: Iterable[A]): StreamPure[A] = new StreamPure[A] {
    override def fold[R1, E1, A1 >: A, S]: ZStream.Fold[R1, E1, A1, S] =
      ZManaged.succeedLazy { (s, cont, f) =>
        ZManaged
          .fromEffect(ZIO.succeedLazy(it.iterator))
          .mapM { it =>
            def loop(s: S): ZIO[R1, E1, S] =
              if (!cont(s) || !it.hasNext) ZIO.succeed(s)
              else ZIO.effectTotal(it.next).flatMap(f(s, _)).flatMap(loop)

            loop(s)
          }
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = {
      val iterator = it.iterator

      def loop(s: S): S =
        if (iterator.hasNext && cont(s)) loop(f(s, iterator.next))
        else s

      loop(s)
    }
  }

  /**
   * Constructs a singleton stream from a strict value.
   */
  final def succeed[A](a: A): StreamPure[A] = new StreamPure[A] {
    override def fold[R1, E1, A1 >: A, S]: ZStream.Fold[R1, E1, A1, S] =
      ZManaged.succeedLazy { (s, cont, f) =>
        if (cont(s)) ZManaged.fromEffect(f(s, a))
        else ZManaged.succeed(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      if (cont(s)) f(s, a)
      else s
  }

  /**
   * Constructs a singleton stream from a lazy value.
   */
  final def succeedLazy[A](a: => A): StreamPure[A] = new StreamPure[A] {
    override def fold[R1, E1, A1 >: A, S]: ZStream.Fold[R1, E1, A1, S] =
      ZManaged.succeedLazy { (s, cont, f) =>
        if (cont(s)) ZManaged.fromEffect(f(s, a))
        else ZManaged.succeed(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      if (cont(s)) f(s, a)
      else s
  }
}
