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
import zio.clock.Clock
import zio.duration.Duration

object Sink {
  import ZSink.Step

  /**
   * see [[ZSink.await]]
   */
  final def await[A]: Sink[Unit, Nothing, A, A] =
    ZSink.await

  /**
   * see [[ZSink.collectAll]]
   */
  final def collectAll[A]: Sink[Nothing, Nothing, A, List[A]] =
    ZSink.collectAll

  /**
   * see [[ZSink.collectAllWhile]]
   */
  final def collectAllWhile[A](p: A => Boolean): Sink[Nothing, A, A, List[A]] =
    collectAllWhileM(a => IO.succeed(p(a)))

  /**
   * see [[ZSink.collectAllWhileM]]
   */
  final def collectAllWhileM[E, A](p: A => IO[E, Boolean]): Sink[E, A, A, List[A]] =
    ZSink.collectAllWhileM(p)

  /**
   * see [[ZSink.die]]
   */
  final def die(e: Throwable): Sink[Nothing, Nothing, Any, Nothing] =
    ZSink.die(e)

  /**
   * see [[ZSink.dieMessage]]
   */
  final def dieMessage(m: String): Sink[Nothing, Nothing, Any, Nothing] =
    ZSink.dieMessage(m)

  /**
   * see [[ZSink.drain]]
   */
  final def drain: Sink[Nothing, Nothing, Any, Unit] =
    ZSink.drain

  /**
   * see [[ZSink.fail]]
   */
  final def fail[E](e: E): Sink[E, Nothing, Any, Nothing] =
    ZSink.fail(e)

  /**
   * see [[ZSink.fold]]
   */
  final def fold[A0, A, S](z: S)(f: (S, A) => Step[S, A0]): Sink[Nothing, A0, A, S] =
    ZSink.fold(z)(f)

  /**
   * see [[ZSink.foldLeft]]
   */
  final def foldLeft[A0, A, S](z: S)(f: (S, A) => S): Sink[Nothing, A0, A, S] =
    ZSink.foldLeft(z)(f)

  /**
   * see [[ZSink.foldM]]
   */
  final def foldM[E, A0, A, S](z: S)(f: (S, A) => IO[E, Step[S, A0]]): Sink[E, A0, A, S] =
    ZSink.foldM(z)(f)

  /**
   * see [[ZSink.foldUntilM]]
   */
  final def foldUntilM[E, S, A](z: S, max: Long)(f: (S, A) => IO[E, S]): Sink[E, A, A, S] =
    ZSink.foldUntilM(z, max)(f)

  /**
   * see [[ZSink.foldUntil]]
   */
  final def foldUntil[S, A](z: S, max: Long)(f: (S, A) => S): Sink[Nothing, A, A, S] =
    ZSink.foldUntil(z, max)(f)

  /**
   * see [[ZSink.foldWeightedM]]
   */
  final def foldWeightedM[E, E1 >: E, A, S](
    z: S
  )(costFn: A => IO[E, Long], max: Long)(f: (S, A) => IO[E1, S]): Sink[E1, A, A, S] =
    ZSink.foldWeightedM[Any, Any, E, E1, A, S](z)(costFn, max)(f)

  /**
   * see [[ZSink.foldWeighted]]
   */
  final def foldWeighted[A, S](
    z: S
  )(costFn: A => Long, max: Long)(f: (S, A) => S): Sink[Nothing, A, A, S] = ZSink.foldWeighted(z)(costFn, max)(f)

  /**
   * see [[ZSink.fromEffect]]
   */
  final def fromEffect[E, B](b: => IO[E, B]): Sink[E, Nothing, Any, B] =
    ZSink.fromEffect(b)

  /**
   * see [[ZSink.fromFunction]]
   */
  final def fromFunction[A, B](f: A => B): Sink[Unit, Nothing, A, B] =
    ZSink.fromFunction(f)

  /**
   * see [[ZSink.halt]]
   */
  final def halt[E](e: Cause[E]): Sink[E, Nothing, Any, Nothing] =
    ZSink.halt(e)

  /**
   * see [[ZSink.identity]]
   */
  final def identity[A]: Sink[Unit, A, A, A] =
    ZSink.identity

  /**
   * see [[ZSink.ignoreWhile]]
   */
  final def ignoreWhile[A](p: A => Boolean): Sink[Nothing, A, A, Unit] =
    ZSink.ignoreWhile(p)

  /**
   * see [[ZSink.ignoreWhileM]]
   */
  final def ignoreWhileM[E, A](p: A => IO[E, Boolean]): Sink[E, A, A, Unit] =
    ZSink.ignoreWhileM(p)

  /**
   * see [[ZSink.pull1]]
   */
  final def pull1[E, A0, A, B](
    end: IO[E, B]
  )(input: A => Sink[E, A0, A, B]): Sink[E, A0, A, B] =
    ZSink.pull1(end)(input)

  /**
   * see [[ZSink.read1]]
   */
  final def read1[E, A](e: Option[A] => E)(p: A => Boolean): Sink[E, A, A, A] =
    ZSink.read1(e)(p)

  /**
   * see [[ZSink.succeedLazy]]
   */
  final def succeedLazy[B](b: => B): Sink[Nothing, Nothing, Any, B] =
    ZSink.succeedLazy(b)

  /**
   * see [[ZSink.throttleEnforce]]
   */
  final def throttleEnforce[A](units: Long, duration: Duration)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, Option[A]]] =
    ZSink.throttleEnforce(units, duration)(costFn)

  /**
   * see [[ZSink.throttleEnforceM]]
   */
  final def throttleEnforceM[E, A](units: Long, duration: Duration)(
    costFn: A => IO[E, Long]
  ): ZManaged[Clock, E, ZSink[Clock, E, Nothing, A, Option[A]]] =
    ZSink.throttleEnforceM[Any, E, A](units, duration)(costFn)

  /**
   * see [[ZSink.throttleShape]]
   */
  final def throttleShape[A](units: Long, duration: Duration)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, A]] =
    ZSink.throttleShape(units, duration)(costFn)

  /**
   * see [[ZSink.throttleShapeM]]
   */
  final def throttleShapeM[E, A](units: Long, duration: Duration)(
    costFn: A => IO[E, Long]
  ): ZManaged[Clock, E, ZSink[Clock, E, Nothing, A, A]] =
    ZSink.throttleShapeM[Any, E, A](units, duration)(costFn)
}
