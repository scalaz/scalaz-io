/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio

import scala.annotation.implicitNotFound

/**
 * The trait `Has[A]` is used with ZIO environment to express an effect's
 * dependency on a service of type `A`. For example,
 * `RIO[Has[Console.Service], Unit]` is an effect that requires a
 * `Console.Service` service. Inside the ZIO library, type aliases are provided
 * as shorthands for common services, e.g.:
 *
 * {{{
 * type Console = Has[ConsoleService]
 * }}}
 */
final class Has[A] private (
  private val map: Map[TagType, scala.Any],
  private var cache: Map[TagType, scala.Any] = Map()
) extends Serializable {
  override def equals(that: Any): Boolean = that match {
    case that: Has[_] => map == that.map
  }

  override def hashCode: Int = map.hashCode

  override def toString: String = map.mkString("Map(", ",\n", ")")

  /**
   * The size of the environment, which is the number of services contained
   * in the environment. This is intended primarily for testing purposes.
   */
  def size: Int = map.size
}
object Has {
  private val TaggedAnyRef: Tagged[AnyRef] = implicitly[Tagged[AnyRef]]

  type MustHave[A, B] = A <:< Has[B]

  @implicitNotFound(
    "Currently, your ZLayer produces ${R}, but to use this operator, you " +
      "must produce Has[${R}]. You can either map over your layer, and wrap " +
      "it with the Has(_) constructor, or you can directly wrap your " +
      "service in Has at the point where it is currently being constructed."
  )
  trait IsHas[-R] {
    def add[R0 <: R, M: Tagged](r: R0, m: M): R0 with Has[M]
    def union[R0 <: R, R1 <: Has[_]](r: R0, r1: R1)(implicit tagged: Tagged[R1]): R0 with R1
    def update[R0 <: R, M: Tagged](r: R0, f: M => M)(implicit ev: R0 <:< Has[M]): R0
  }
  object IsHas {
    implicit def ImplicitIs[R <: Has[_]]: IsHas[R] =
      new IsHas[R] {
        def add[R0 <: R, M: Tagged](r: R0, m: M): R0 with Has[M] = r.add(m)
        def union[R0 <: R, R1 <: Has[_]](r: R0, r1: R1)(implicit tagged: Tagged[R1]): R0 with R1 =
          r.union[R1](r1)
        def update[R0 <: R, M: Tagged](r: R0, f: M => M)(implicit ev: R0 <:< Has[M]): R0 = r.update(f)
      }
  }

  @implicitNotFound(
    "The ZLayer operator you are trying to use needs to combine multiple " +
      "services. While services cannot directly be combined, they can be " +
      "combined if first wrapped in the Has data type. Before you use this " +
      "operator, you must ensure the service produced by your layer is " +
      "wrapped in Has."
  )
  trait AreHas[-R, -R1] {
    def union[R0 <: R, R00 <: R1](r: R0, r1: R00)(implicit tagged: Tagged[R00]): R0 with R00
    def unionAll[R0 <: R, R00 <: R1](r: R0, r1: R00): R0 with R00
  }
  object AreHas {
    implicit def ImplicitAre[R <: Has[_], R1 <: Has[_]]: AreHas[R, R1] =
      new AreHas[R, R1] {
        def union[R0 <: R, R00 <: R1](r: R0, r1: R00)(implicit tagged: Tagged[R00]): R0 with R00 =
          r.union[R00](r1)
        def unionAll[R0 <: R, R00 <: R1](r: R0, r1: R00): R0 with R00 =
          r.unionAll[R00](r1)
      }
  }

  implicit final class HasSyntax[Self <: Has[_]](private val self: Self) extends AnyVal {
    def +[B](b: B)(implicit tag: Tagged[B]): Self with Has[B] = self.add(b)

    def ++[B <: Has[_]](that: B)(implicit tagged: Tagged[B]): Self with B = self.union[B](that)

    /**
     * Adds a service to the environment.
     */
    def add[B](b: B)(implicit tagged: Tagged[B]): Self with Has[B] =
      new Has(self.map + (taggedTagType(tagged) -> b)).asInstanceOf[Self with Has[B]]

    /**
     * Retrieves a service from the environment.
     */
    def get[B](implicit ev: Self <:< Has[B], tagged: Tagged[B]): B = {
      val tag = taggedTagType(tagged)

      self.map
        .getOrElse(
          tag,
          self.cache.getOrElse(
            tag,
            throw new Error(s"Defect in zio.Has: Could not find ${tag} inside ${self}")
          )
        )
        .asInstanceOf[B]
    }

    /**
     * Prunes the environment to the set of services statically known to be
     * contained within it.
     */
    def prune(implicit tagged: Tagged[Self]): Self = {
      val tag = taggedTagType(tagged)
      val set = taggedGetHasServices(tag)

      if (set.isEmpty) self
      else new Has(filterKeys(self.map)(set)).asInstanceOf[Self]
    }

    /**
     * Combines this environment with the specified environment.
     */
    def union[B <: Has[_]](that: B)(implicit tagged: Tagged[B]): Self with B =
      self.unionAll[B](that.prune)

    /**
     * Combines this environment with the specified environment. In the event
     * of service collisions, which may not be reflected in statically known
     * types, the right hand side will be preferred.
     */
    def unionAll[B <: Has[_]](that: B): Self with B =
      (new Has(self.map ++ that.map)).asInstanceOf[Self with B]

    /**
     * Updates a service in the environment.
     */
    def update[B: Tagged](f: B => B)(implicit ev: Self MustHave B): Self =
      self.add(f(get[B]))
  }

  /**
   * Constructs a new environment holding the single service.
   */
  def apply[A: Tagged](a: A): Has[A] = new Has[AnyRef](Map(), Map(taggedTagType(TaggedAnyRef) -> (()))).add(a)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged](a: A, b: B): Has[A] with Has[B] = Has(a).add(b)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged](a: A, b: B, c: C): Has[A] with Has[B] with Has[C] =
    Has(a).add(b).add(c)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged](
    a: A,
    b: B,
    c: C,
    d: D
  ): Has[A] with Has[B] with Has[C] with Has[D] =
    Has(a).add(b).add(c).add(d)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] =
    Has(a).add(b).add(c).add(d).add(e)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] =
    Has(a).add(b).add(c).add(d).add(e).add(f)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged, P: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o).add(p)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged, P: Tagged, Q: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P] with Has[Q] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o).add(p).add(q)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged, P: Tagged, Q: Tagged, R: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P] with Has[Q] with Has[R] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o).add(p).add(q).add(r)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged, P: Tagged, Q: Tagged, R: Tagged, S: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P] with Has[Q] with Has[R] with Has[S] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o).add(p).add(q).add(r).add(s)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged, P: Tagged, Q: Tagged, R: Tagged, S: Tagged, T: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S,
    t: T
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P] with Has[Q] with Has[R] with Has[S] with Has[T] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o).add(p).add(q).add(r).add(s).add(t)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged, P: Tagged, Q: Tagged, R: Tagged, S: Tagged, T: Tagged, U: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S,
    t: T,
    u: U
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P] with Has[Q] with Has[R] with Has[S] with Has[T] with Has[U] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o).add(p).add(q).add(r).add(s).add(t).add(u)

  /**
   * Constructs a new environment holding the specified services.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged, F: Tagged, G: Tagged, H: Tagged, I: Tagged, J: Tagged, K: Tagged, L: Tagged, M: Tagged, N: Tagged, O: Tagged, P: Tagged, Q: Tagged, R: Tagged, S: Tagged, T: Tagged, U: Tagged, V: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
    m: M,
    n: N,
    o: O,
    p: P,
    q: Q,
    r: R,
    s: S,
    t: T,
    u: U,
    v: V
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P] with Has[Q] with Has[R] with Has[S] with Has[T] with Has[U] with Has[V] =
    Has(a).add(b).add(c).add(d).add(e).add(f).add(g).add(h).add(i).add(j).add(k).add(l).add(m).add(n).add(o).add(p).add(q).add(r).add(s).add(t).add(u).add(v)

  /**
   * Modifies an environment in a scoped way.
   *
   * {{
   * Env.scoped[Logging](decorateLogger(_)) { effect }
   * }}
   */
  def scoped[A: Tagged](f: A => A): Scoped[A] = new Scoped(f)

  class Scoped[M: Tagged](f: M => M) {
    def apply[R <: Has[M], E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      ZIO.environment[R].flatMap(env => zio.provide(env.update(f)))
  }

  /**
   * Filters a map by retaining only keys satisfying a predicate.
   */
  private def filterKeys[K, V](map: Map[K, V])(f: K => Boolean): Map[K, V] =
    map.foldLeft[Map[K, V]](Map.empty) {
      case (acc, (key, value)) =>
        if (f(key)) acc + (key -> value) else acc
    }
}
