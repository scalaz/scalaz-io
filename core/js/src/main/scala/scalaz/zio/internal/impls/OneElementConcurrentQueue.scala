package scalaz.zio.internal.impls

import java.io.Serializable

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong, AtomicReference }

import scalaz.zio.internal.MutableConcurrentQueue

class OneElementConcurrentQueue[A] extends MutableConcurrentQueue[A] with Serializable {
  private[this] final val ref = new AtomicReference[AnyRef]()

  private[this] final val headCounter   = new AtomicLong(0L)
  private[this] final val deqInProgress = new AtomicBoolean(false)

  private[this] final val tailCounter   = new AtomicLong(0L)
  private[this] final val enqInProgress = new AtomicBoolean(false)

  final val capacity: Int = 1

  final override def dequeuedCount(): Long = headCounter.get()
  final override def enqueuedCount(): Long = tailCounter.get()

  final override def isEmpty(): Boolean = ref.get() == null
  final override def isFull(): Boolean  = !isEmpty()

  final override def offer(a: A): Boolean = {
    assert(a != null)

    var res     = false
    var looping = true

    while (looping) {
      if (isFull()) {
        looping = false
      } else {
        if (enqInProgress.compareAndSet(false, true)) { // get an exclusive right to offer
          if (ref.get() == null) {
            tailCounter.lazySet(tailCounter.get() + 1)
            ref.lazySet(a.asInstanceOf[AnyRef])
            res = true
          }

          enqInProgress.lazySet(false)
          looping = false
        }
      }
    }

    res
  }

  final override def poll(default: A): A = {
    var res     = default
    var looping = true

    while (looping) {
      if (isEmpty()) {
        looping = false
      } else {
        if (deqInProgress.compareAndSet(false, true)) { // get an exclusive right to poll
          val el = ref.get().asInstanceOf[A]

          if (el != null) {
            res = el
            headCounter.lazySet(headCounter.get() + 1)
            ref.lazySet(null.asInstanceOf[AnyRef])
          }

          deqInProgress.lazySet(false)
          looping = false
        }
      }
    }

    res
  }

  final override def size(): Int = if (isEmpty()) 0 else 1
}
