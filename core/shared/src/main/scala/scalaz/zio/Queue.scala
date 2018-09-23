// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz.zio

import scala.collection.immutable.{ Queue => IQueue }

import Queue.internal._

/**
 * A `Queue` is a lightweight, asynchronous queue. This implementation is
 * naive, if functional, and could benefit from significant optimization.
 *
 * TODO:
 *
 * 1. Investigate using a faster option than `Queue`, because `Queue` has
 *    `O(n)` `length` method.
 * 2. Benchmark to see how slow this implementation is and if there are any
 *    easy ways to improve performance.
 */
class Queue[A] private (capacity: Int, ref: Ref[State[A]]) {

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  final def size: IO[Nothing, Int] = ref.get.map(_.size)

  /**
   * Places the value in the queue. If the queue has reached capacity, then
   * the fiber performing the `offer` will be suspended until there is room in
   * the queue.
   */
  final def offer(a: A): IO[Nothing, Unit] = {
    val acquire: (Promise[Nothing, Unit], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        takers.dequeueOption match {
          case None => (p.complete(()), Surplus(IQueue.empty[A].enqueue(a), IQueue.empty))
          case Some((taker, takers)) =>
            (taker.complete(a) *> p.complete(()), Deficit(takers))
        }

      case (p, Surplus(values, putters)) =>
        if (values.length < capacity && putters.isEmpty) {
          (p.complete(()), Surplus(values.enqueue(a), putters))
        } else {
          (IO.now(false), Surplus(values, putters.enqueue((a, p))))
        }
    }

    val release: (Boolean, Promise[Nothing, Unit]) => IO[Nothing, Unit] = {
      case (_, p) => p.poll.void <> removePutter(p)
    }

    Promise.bracket[Nothing, State[A], Unit, Boolean](ref)(acquire)(release)
  }

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  final def take: IO[Nothing, A] = {

    val acquire: (Promise[Nothing, A], State[A]) => (IO[Nothing, Boolean], State[A]) = {
      case (p, Deficit(takers)) =>
        (IO.now(false), Deficit(takers.enqueue(p)))
      case (p, Surplus(values, putters)) =>
        values.dequeueOption match {
          case None =>
            putters.dequeueOption match {
              case None =>
                (IO.now(false), Deficit(IQueue.empty.enqueue(p)))
              case Some(((a, putter), putters)) =>
                (putter.complete(()) *> p.complete(a), Surplus(IQueue.empty, putters))
            }
          case Some((a, values)) =>
            (p.complete(a), Surplus(values, putters))
        }
    }

    val release: (Boolean, Promise[Nothing, A]) => IO[Nothing, Unit] = {
      case (_, p) => p.poll.void <> removeTaker(p)
    }
    Promise.bracket[Nothing, State[A], A, Boolean](ref)(acquire)(release)
  }

  /**
   * Interrupts any fibers that are suspended on `take` because the queue is
   * empty. If any fibers are interrupted, returns true, otherwise, returns
   * false.
   */
  final def interruptTake(t: Throwable): IO[Nothing, Boolean] =
    IO.flatten(ref.modify {
      case Deficit(takers) if takers.nonEmpty =>
        val forked: IO[Nothing, Fiber[Nothing, List[Boolean]]] =
          IO.forkAll[Nothing, Boolean](takers.map(_.interrupt(t)))
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(IQueue.empty[Promise[Nothing, A]]))
      case s =>
        (IO.now(false), s)
    })

  /**
   * Interrupts any fibers that are suspended on `offer` because the queue is
   * at capacity. If any fibers are interrupted, returns true, otherwise,
   * returns  false.
   */
  final def interruptOffer(t: Throwable): IO[Nothing, Boolean] =
    IO.flatten(ref.modify {
      case Surplus(_, putters) if putters.nonEmpty =>
        val forked: IO[Nothing, Fiber[Nothing, List[Boolean]]] =
          IO.forkAll[Nothing, Boolean](putters.map(_._2.interrupt(t)))
        (forked.flatMap(_.join).map(_.forall(identity)), Deficit(IQueue.empty[Promise[Nothing, A]]))
      case s =>
        (IO.now(false), s)
    })

  private final def removePutter(putter: Promise[Nothing, Unit]): IO[Nothing, Unit] =
    ref.update {
      case Surplus(values, putters) =>
        Surplus(values, putters.filterNot(_._2 == putter))
      case d => d
    }.void

  private final def removeTaker(taker: Promise[Nothing, A]): IO[Nothing, Unit] =
    ref.update {
      case Deficit(takers) =>
        Deficit(takers.filterNot(_ == taker))

      case d => d
    }.void

}
object Queue {

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   */
  final def bounded[A](capacity: Int): IO[Nothing, Queue[A]] =
    Ref[State[A]](Surplus[A](IQueue.empty, IQueue.empty)).map(new Queue[A](capacity, _))

  /**
   * Makes a new unbounded queue.
   */
  final def unbounded[A]: IO[Nothing, Queue[A]] = bounded(Int.MaxValue)

  private[zio] object internal {
    sealed trait State[A] {
      def size: Int
    }
    final case class Deficit[A](takers: IQueue[Promise[Nothing, A]]) extends State[A] {
      def size: Int = -takers.length
    }
    final case class Surplus[A](queue: IQueue[A], putters: IQueue[(A, Promise[Nothing, Unit])]) extends State[A] {
      def size: Int = queue.size + putters.length // TODO: O(n) for putters.length
    }
  }
}
