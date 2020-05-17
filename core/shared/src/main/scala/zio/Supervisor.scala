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

import internal.{ Platform, Sync }

/**
 * A `Supervisor[A]` is allowed to supervise the launching and termination of
 * fibers, producing some visible value of type `A` from the supervision.
 */
trait Supervisor[+A] { self =>
  import Supervisor._

  /**
   * Returns an effect that succeeds with the value produced by this
   * supervisor. This value may change over time, reflecting what the
   * supervisor produces as it supervises fibers.
   */
  def value: UIO[A]

  /**
   * Returns a new supervisor that performs the function of this supervisor,
   * and the function of the specified supervisor, producing a tuple of the
   * outputs produced by both supervisors.
   *
   * The composite supervisor indicates that it has fully handled the
   * supervision event if only both component supervisors indicate they have
   * handled the supervision event.
   */
  final def &&[B](that0: => Supervisor[B]): Supervisor[(A, B)] =
    new Supervisor[(A, B)] {
      lazy val that = that0

      def value = self.value zip that.value

      def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber[_, _]],
        fiber: Fiber[E, A]
      ): Propagation =
        self.unsafeOnStart(environment, effect, parent, fiber) && that.unsafeOnStart(environment, effect, parent, fiber)

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber[E, A]): Propagation =
        self.unsafeOnEnd(value, fiber) && that.unsafeOnEnd(value, fiber)
    }

  /**
   * Returns a new supervisor that performs the function of this supervisor,
   * and the function of the specified supervisor, producing a tuple of the
   * outputs produced by both supervisors.
   *
   * The composite supervisor indicates that it has fully handled the
   * supervision event if either component supervisors indicate they have
   * handled the supervision event.
   */
  final def ||[B](that0: => Supervisor[B]): Supervisor[(A, B)] =
    new Supervisor[(A, B)] {
      lazy val that = that0

      def value = self.value zip that.value

      def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber[_, _]],
        fiber: Fiber[E, A]
      ): Propagation =
        self.unsafeOnStart(environment, effect, parent, fiber) || that.unsafeOnStart(environment, effect, parent, fiber)

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber[E, A]): Propagation =
        self.unsafeOnEnd(value, fiber) || that.unsafeOnEnd(value, fiber)
    }

  def unsafeOnStart[R, E, A](
    environment: R,
    effect: ZIO[R, E, A],
    parent: Option[Fiber[_, _]],
    fiber: Fiber[E, A]
  ): Propagation

  def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber[E, A]): Propagation
}
object Supervisor {

  /**
   * A hint indicating whether or not to propagate supervision events across
   * supervisor hierarchies.
   */
  sealed trait Propagation { self =>
    import Propagation._

    def ||(that: Propagation): Propagation =
      if (self == Continue || that == Continue) Continue else Stop

    def &&(that: Propagation): Propagation =
      if (self == Continue && that == Continue) Continue else Stop
  }
  object Propagation {

    /**
     * A hint indicating supervision events no longer require propagation.
     */
    case object Stop extends Propagation

    /**
     * A hint indicating supervision events require further propagation.
     */
    case object Continue extends Propagation
  }

  /**
   * Creates a new supervisor that tracks children in a set.
   *
   * @param weak Whether or not to track the children in a weak set, if
   *             possible (platform-dependent).
   */
  def track(weak: Boolean): UIO[Supervisor[Chunk[Fiber[_, _]]]] = UIO {
    val set: java.util.Set[Fiber[_, _]] =
      if (weak) Platform.newWeakSet[Fiber[_, _]]()
      else new java.util.HashSet[Fiber[_, _]]()

    new Supervisor[Chunk[Fiber[_, _]]] {
      def value: UIO[Chunk[Fiber[_, _]]] =
        UIO.effectTotal(Sync(set)(Chunk.fromArray(set.toArray[Fiber[_, _]](Array[Fiber[_, _]]()))))

      def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber[_, _]],
        fiber: Fiber[E, A]
      ): Propagation = {
        Sync(set)(set.add(fiber))

        Propagation.Continue
      }

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber[E, A]): Propagation = {
        Sync(set)(set.remove(fiber))

        Propagation.Continue
      }
    }
  }

  /**
   * A supervisor that doesn't do anything in response to supervision events.
   */
  val none: Supervisor[Unit] =
    new Supervisor[Unit] {
      def value = ZIO.unit

      def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber[_, _]],
        fiber: Fiber[E, A]
      ): Propagation = Propagation.Continue

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber[E, A]): Propagation = Propagation.Continue
    }
}
