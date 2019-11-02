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

package zio

/**
 * Fiber's counterpart for Java's `ThreadLocal`. Value is automatically propagated
 * to child on fork and merged back in after joining child.
 * {{{
 * for {
 *   fiberRef <- FiberRef.make("Hello world!")
 *   child <- fiberRef.set("Hi!).fork
 *   result <- child.join
 * } yield result
 * }}}
 *
 * `result` will be equal to "Hi!" as changes done by child were merged on join.
 *
 * FiberRef#make also allows specifying how the values will be combined when joining.
 * By default this will use the value of the joined fiber.
 * for {
 *   fiberRef <- FiberRef.make(0, math.max)
 *   child    <- fiberRef.update(_ + 1).fork
 *   _        <- fiberRef.update(_ + 2)
 *   _        <- child.join
 *   value    <- fiberRef.get
 * } yield value
 * }}}
 *
 * `value` will be 2 as the value in the joined fiber is lower and we specified `max` as our combine function.
 *
 * @param initial
 * @tparam A
 */
final class FiberRef[A](private[zio] val initial: A, private[zio] val combine: (A, A) => A) extends Serializable {

  /**
   * Reads the value associated with the current fiber. Returns initial value if
   * no value was `set` or inherited from parent.
   */
  final val get: UIO[A] = modify(v => (v, v))

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `bracket`.
   */
  final def locally[R, E, B](value: A)(use: ZIO[R, E, B]): ZIO[R, E, B] =
    for {
      oldValue <- get
      b        <- set(value).bracket_(set(oldValue))(use)
    } yield b

  /**
   * Atomically modifies the `FiberRef` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   */
  final def modify[B](f: A => (B, A)): UIO[B] = new ZIO.FiberRefModify(this, f)

  /**
   * Atomically modifies the `FiberRef` with the specified partial function, which computes
   * a return value for the modification if the function is defined in the current value
   * otherwise it returns a default value.
   * This is a more powerful version of `updateSome`.
   */
  final def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): UIO[B] = modify { v =>
    pf.applyOrElse[A, (B, A)](v, _ => (default, v))
  }

  /**
   * Sets the value associated with the current fiber.
   */
  final def set(value: A): UIO[Unit] = modify(_ => ((), value))

  /**
   * Atomically modifies the `FiberRef` with the specified function.
   */
  final def update(f: A => A): UIO[A] = modify { v =>
    val result = f(v)
    (result, result)
  }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function.
   * if the function is undefined in the current value it returns the old value without changing it.
   */
  final def updateSome(pf: PartialFunction[A, A]): UIO[A] = modify { v =>
    val result = pf.applyOrElse[A, A](v, identity)
    (result, result)
  }

}

object FiberRef extends Serializable {

  /**
   * Creates a new `FiberRef` with given initial value.
   */
  def make[A](initialValue: A, combine: (A, A) => A = (_: A, last: A) => last): UIO[FiberRef[A]] =
    new ZIO.FiberRefNew(initialValue, combine)
}
