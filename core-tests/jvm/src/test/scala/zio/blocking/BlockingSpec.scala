package zio.blocking

import java.util.concurrent.atomic.AtomicBoolean

import zio.duration.Duration
import zio.{ UIO, ZIOBaseSpec }
import BlockingSpecUtil._
import zio.test._
import zio.test.Assertion._

object BlockingSpec
    extends ZIOBaseSpec(
      suite("BlockingSpec")(
        suite("Make a Blocking Service and verify that")(
          testM("effectBlocking completes successfully") {
            assertM(effectBlocking(()), isUnit)
          },
          testM("effectBlockingCancelable completes successfully") {
            assertM(effectBlockingCancelable(())(UIO.unit), isUnit)
          },
          testM("effectBlocking can be interrupted") {
            assertM(effectBlocking(Thread.sleep(50000)).timeout(Duration.Zero), isNone)
          },
          testM("effectBlockingCancelable can be interrupted") {
            val release = new AtomicBoolean(false)
            val cancel  = UIO.effectTotal(release.set(true))
            assertM(effectBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero), isNone)
          }
        )
      )
    )

object BlockingSpecUtil {
  def blockingAtomic(released: AtomicBoolean): Unit =
    while (!released.get()) {
      try {
        Thread.sleep(10L)
      } catch {
        case _: InterruptedException => ()
      }
    }
}
