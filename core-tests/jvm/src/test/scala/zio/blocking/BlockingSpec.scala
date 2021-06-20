package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._

import java.util.concurrent.atomic.AtomicBoolean

object BlockingSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("BlockingSpec")(
    suite("Make a Blocking Service and verify that")(
      testM("effectBlocking completes successfully") {
        assertM(ZIO.attemptBlocking(()))(isUnit)
      },
      testM("effectBlocking runs on the blocking thread pool") {
        for {
          name <- ZIO.attemptBlocking(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingCancelable completes successfully") {
        assertM(ZIO.effectBlockingCancelable(())(UIO.unit))(isUnit)
      },
      testM("effectBlockingCancelable runs on the blocking thread pool") {
        for {
          name <- ZIO.effectBlockingCancelable(Thread.currentThread.getName)(UIO.unit)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingCancelable can be interrupted") {
        val release = new AtomicBoolean(false)
        val cancel  = UIO.succeed(release.set(true))
        assertM(ZIO.effectBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero))(isNone)
      },
      testM("effectBlockingInterrupt completes successfully") {
        assertM(ZIO.effectBlockingInterrupt(()))(isUnit)
      },
      testM("effectBlockingInterrupt runs on the blocking thread pool") {
        for {
          name <- ZIO.effectBlockingInterrupt(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingInterrupt can be interrupted") {
        assertM(ZIO.effectBlockingInterrupt(Thread.sleep(50000)).timeout(Duration.Zero))(isNone)
      } @@ nonFlaky
    )
  )

  def blockingAtomic(released: AtomicBoolean): Unit =
    while (!released.get()) {
      try {
        Thread.sleep(10L)
      } catch {
        case _: InterruptedException => ()
      }
    }
}
