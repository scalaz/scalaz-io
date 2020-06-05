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

import java.util.Map
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }

import zio.internal.Sync

/**
 * A `ZScope[K, A]` is a value that allows adding finalizers identified by `K`.
 * Scopes are closed with a value of type `A`, which is provided to all the
 * finalizers when the scope is released.
 *
 * For safety reasons, this interface has no method to close a scope. Rather,
 * an open scope may be required with `ZScope.make`, which returns a function
 * that can close a scope. This allows scopes to be safely passed around
 * without fear they will be accidentally closed.
 */
sealed trait ZScope[+A] { self =>

  /**
   * Determines if the scope is closed at the instant the effect executes.
   * Returns an effect that will succeed with `true` if the scope is closed,
   * and `false` otherwise.
   */
  def closed: UIO[Boolean]

  /**
   * Prevents a previously added finalizer from being executed when the scope
   * is closed. The returned effect will succeed with `true` if the finalizer
   * will not be run by this scope, and `false` otherwise.
   */
  def deny(key: => ZScope.Key): UIO[Boolean]

  /**
   * Determines if the scope is empty (has no finalizers) at the instant the
   * effect executes. The returned effect will succeed with `true` if the scope
   * is empty, and `false` otherwise.
   */
  def empty: UIO[Boolean]

  /**
   * Adds a finalizer to the scope. If successful, this ensures that when the
   * scope exits, the finalizer will be run, assuming the key has not been
   * garbage collected.
   *
   * The returned effect will succeed with a key if the finalizer was added
   * to the scope, and `None` if the scope is already closed.
   */
  def ensure(finalizer: A => UIO[Any], mode: ZScope.Mode = ZScope.Mode.Strong): UIO[Option[ZScope.Key]]

  /**
   * Extends the specified scope so that it will not be closed until this
   * scope is closed. Note that extending a scope into the global scope
   * will result in the scope *never* being closed!
   *
   * Scope extension does not result in changes to the scope contract: open
   * scopes must *always* be closed.
   */
  final def extend(that: ZScope[Any]): UIO[Boolean] = UIO(unsafeExtend(that))

  /**
   * Determines if the scope is open at the moment the effect is executed.
   * Returns an effect that will succeed with `true` if the scope is open,
   * and `false` otherwise.
   */
  def open: UIO[Boolean] = closed.map(!_)

  /**
   * Determines if the scope has been released at the moment the effect is
   * executed executed. A scope can be closed yet unreleased, if it has been
   * extended by another scope which is not yet released.
   */
  def released: UIO[Boolean]

  private[zio] def unsafeEnsure(finalizer: A => UIO[Any], mode: ZScope.Mode): Option[ZScope.Key]
  private[zio] def unsafeExtend(that: ZScope[Any]): Boolean
}
object ZScope {
  sealed trait Mode
  object Mode {
    case object Weak   extends Mode
    case object Strong extends Mode
  }

  /**
   * Represents a key in a scope, which is associated with a single finalizer.
   */
  sealed trait Key {

    /**
     * Attempts to remove the finalizer associated with this key from the
     * scope. The returned effect will succeed with a boolean, which indicates
     * whether the attempt was successful. A value of `true` indicates the
     * finalizer will not be executed, while a value of `false` indicates the
     * finalizer was already executed.
     *
     * @return
     */
    def remove: UIO[Boolean]
  }
  object Key {
    def apply(remove0: UIO[Boolean]): Key = new Key { def remove = remove0 }
  }

  /**
   * The global scope, which is entirely stateless. Finalizers added to the
   * global scope will never be executed (nor kept in memory).
   */
  object global extends ZScope[Nothing] {
    private val unsafeEnsureResult = Some(Key(UIO(true)))
    private val ensureResult       = UIO(unsafeEnsureResult)

    def closed: UIO[Boolean] = UIO(false)

    def deny(key: => Key): UIO[Boolean] = UIO(true)

    def empty: UIO[Boolean] = UIO(false)

    def ensure(finalizer: Nothing => UIO[Any], mode: ZScope.Mode = ZScope.Mode.Strong): UIO[Option[Key]] = ensureResult

    def released: UIO[Boolean] = UIO(false)

    private[zio] def unsafeEnsure(finalizer: Nothing => UIO[Any], mode: ZScope.Mode): Option[Key] = unsafeEnsureResult
    private[zio] def unsafeExtend(that: ZScope[Any]): Boolean = that match {
      case local: Local[_] => local.unsafeAddRef()
      case _               => true
    }
  }

  /**
   * A tuple that contains an open scope, together with a function that closes
   * the scope.
   */
  final case class Open[A](close: A => UIO[Boolean], scope: Local[A])

  /**
   * An effect that makes a new open scope, which provides not just the scope,
   * but also a way to close the scope.
   */
  def make[A]: UIO[Open[A]] = UIO(unsafeMake())

  private[ZScope] final case class OrderedFinalizer(order: Int, finalizer: Any => UIO[Any])

  private[zio] def unsafeMake[A](): Open[A] = {
    val nullA: A = null.asInstanceOf[A]

    val exitValue = new AtomicReference(nullA)

    val weakFinalizers   = internal.Platform.newWeakHashMap[Key, OrderedFinalizer]()
    val strongFinalizers = new java.util.HashMap[Key, OrderedFinalizer]()

    val scope0 =
      new Local[A](new AtomicInteger(Int.MinValue), exitValue, new AtomicInteger(1), weakFinalizers, strongFinalizers)

    Open[A](
      (a: A) =>
        UIO.effectSuspendTotal {
          val result = scope0.unsafeClose(a)

          if (result eq null) UIO(false) else result as true
        },
      scope0
    )
  }

  final class Local[A](
    // A counter for finalizers, which is used for ordering purposes.
    private[zio] val finalizerCount: AtomicInteger,
    // The value that a scope is closed with (or `null`).
    private[zio] val exitValue: AtomicReference[A],
    // The number of references to the scope, which defaults to 1.
    private[zio] val references: AtomicInteger,
    // The weak finalizers attached to the scope.
    private[zio] val weakFinalizers: Map[Key, OrderedFinalizer],
    // The strong finalizers attached to the scope.
    private[zio] val strongFinalizers: Map[Key, OrderedFinalizer]
  ) extends ZScope[A] { self =>

    def closed: UIO[Boolean] = UIO(unsafeClosed())

    def deny(key: => Key): UIO[Boolean] = UIO(unsafeDeny(key))

    def empty: UIO[Boolean] = UIO(Sync(self)(weakFinalizers.size() == 0 && strongFinalizers.size() == 0))

    def ensure(finalizer: A => UIO[Any], mode: ZScope.Mode = ZScope.Mode.Strong): UIO[Option[Key]] =
      UIO(unsafeEnsure(finalizer, mode))

    def release: UIO[Boolean] = UIO.effectSuspendTotal {
      val result = unsafeRelease()

      if (result eq null) UIO(false) else result as true
    }

    def released: UIO[Boolean] = UIO(unsafeReleased())

    private[this] def finalizers(mode: ZScope.Mode): Map[Key, OrderedFinalizer] =
      if (mode == ZScope.Mode.Weak) weakFinalizers else strongFinalizers

    private[zio] def unsafeClosed(): Boolean = Sync(self)(exitValue.get() != null)

    private[zio] def unsafeClose(a0: A): UIO[Any] =
      Sync(self) {
        exitValue.compareAndSet(null.asInstanceOf[A], a0)

        unsafeRelease()
      }

    private[zio] def unsafeDeny(key: Key): Boolean =
      Sync(self) {
        if (unsafeClosed()) false
        else (weakFinalizers.remove(key) ne null) || (strongFinalizers.remove(key) ne null)
      }

    private[zio] def unsafeEnsure(finalizer: A => UIO[Any], mode: ZScope.Mode): Option[Key] =
      Sync(self) {
        def coerce(f: A => UIO[Any]): Any => UIO[Any] = f.asInstanceOf[Any => UIO[Any]]

        if (unsafeClosed()) None
        else {
          lazy val key: Key = Key(deny(key))

          finalizers(mode).put(key, OrderedFinalizer(finalizerCount.incrementAndGet(), coerce(finalizer)))

          Some(key)
        }
      }

    private[zio] def unsafeAddRef(): Boolean =
      Sync(self) {
        if (unsafeClosed()) false
        else {
          references.incrementAndGet()
          true
        }
      }

    private[zio] def unsafeEmpty(): Boolean =
      Sync(self) {
        (weakFinalizers.size() == 0) && (strongFinalizers.size() == 0)
      }

    private[zio] def unsafeExtend(that: ZScope[Any]): Boolean =
      if (self eq that) true
      else
        that match {
          case ZScope.global => true

          case child: ZScope.Local[Any] =>
            Sync(child) {
              Sync(self) {
                if (!self.unsafeClosed() && !child.unsafeClosed()) {
                  // If parent and child scopes are both open:
                  child.unsafeAddRef()

                  self.unsafeEnsure(_ => child.release, ZScope.Mode.Strong)

                  true
                } else false
              }
            }
        }

    private[zio] def unsafeRelease(): UIO[Unit] =
      Sync(self) {
        if (references.decrementAndGet() == 0) {
          val totalSize = weakFinalizers.size() + strongFinalizers.size()

          if (totalSize == 0) null
          else {
            val array = Array.ofDim[OrderedFinalizer](totalSize)

            var i        = 0
            var iterator = weakFinalizers.entrySet().iterator()

            while (iterator.hasNext()) {
              array(i) = iterator.next().getValue()
              i = i + 1
            }

            iterator = strongFinalizers.entrySet().iterator()

            while (iterator.hasNext()) {
              array(i) = iterator.next().getValue()
              i = i + 1
            }

            weakFinalizers.clear()
            strongFinalizers.clear()

            java.util.Arrays.sort(
              array,
              (l: OrderedFinalizer, r: OrderedFinalizer) =>
                if (l eq null) -1 else if (r eq null) 1 else l.order - r.order
            )

            val a = exitValue.get()

            array
              .foldLeft[UIO[Cause[Nothing]]](noCauseEffect) {
                case (acc, o) =>
                  if (o ne null) acc.zipWith(o.finalizer(a).cause)(_ ++ _)
                  else acc
              }
              .uncause[Nothing]
          }
        } else null
      }

    private[zio] def unsafeReleased(): Boolean = references.get() <= 0
  }

  private val noCause: Cause[Nothing]            = Cause.empty
  private val noCauseEffect: UIO[Cause[Nothing]] = UIO.succeedNow(noCause)
}
