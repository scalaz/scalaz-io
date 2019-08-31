/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.ZIO
import zio.stream.ZStream

/**
 * A sample is a single observation from a random variable, together with a
 * tree of "shrinkings" used for minimization of "large" failures.
 */
final case class Sample[-R, +A](value: A, shrink: ZStream[R, Nothing, Sample[R, A]]) { self =>

  final def <*>[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.zip(that)

  final def flatMap[R1 <: R, B](f: A => Sample[R1, B]): Sample[R1, B] = {
    val sample = f(value)
    Sample(sample.value, sample.shrink ++ shrink.map(_.flatMap(f)))
  }

  final def map[B](f: A => B): Sample[R, B] =
    Sample(f(value), shrink.map(_.map(f)))

  final def shrinkSearch(f: A => Boolean): ZStream[R, Nothing, A] =
    if (!f(value))
      ZStream.empty
    else
      ZStream(value) ++ shrink.dropWhile(v => !(f(v.value))).take(1).flatMap(_.shrinkSearch(f))

  final def traverse[R1 <: R, B](f: A => ZIO[R1, Nothing, B]): ZIO[R1, Nothing, Sample[R1, B]] =
    f(value).map(Sample(_, shrink.mapM(_.traverse(f))))

  final def zip[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.flatMap(a => that.map(b => (a, b)))

  final def zipWith[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C): Sample[R1, C] =
    self.zip(that).map(f.tupled)
}
object Sample {

  /**
   * A sample without shrinking.
   */
  final def noShrink[A](a: A): Sample[Any, A] =
    Sample(a, ZStream.empty)

  final def shrinkFractional[A](smallest: A)(a: A)(implicit F: Fractional[A]): Sample[Any, A] =
    Sample.unfold((a)) { max =>
      (max, ZStream.unfold(smallest) { min =>
        val mid = F.plus(min, F.div(F.minus(max, min), F.fromInt(2)))
        if (mid == max) None
        else if (F.toDouble(F.abs(F.minus(max, mid))) < 0.001) Some((min, max))
        else Some((mid, mid))
      })
    }

  final def shrinkIntegral[A](smallest: A)(a: A)(implicit I: Integral[A]): Sample[Any, A] =
    Sample.unfold((a)) { max =>
      (max, ZStream.unfold(smallest) { min =>
        val mid = I.plus(min, I.quot(I.minus(max, min), I.fromInt(2)))
        if (mid == max) None
        else if (I.equiv(I.abs(I.minus(max, mid)), I.one)) Some((mid, max))
        else Some((mid, mid))
      })
    }

  final def unfold[R, A, S](s: S)(f: S => (A, ZStream[R, Nothing, S])): Sample[R, A] = {
    val (value, shrink) = f(s)
    Sample(value, shrink.map(unfold(_)(f)))
  }
}
