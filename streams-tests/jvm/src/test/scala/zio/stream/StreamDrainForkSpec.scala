package zio.stream

import zio._
import zio.test._
import zio.test.Assertion.{ dies, equalTo, fails, isTrue }

object StreamDrainForkSpec extends ZIOBaseSpec {
  def spec = suite("ZStream.drainFork")(
    testM("runs the other stream in the background") {
      for {
        latch <- Promise.make[Nothing, Unit]
        _ <- ZStream
              .fromEffect(latch.await)
              .drainFork(ZStream.fromEffect(latch.succeed(())))
              .runDrain
      } yield assertCompletes
    },
    testM("interrupts the background stream when the foreground exits") {
      for {
        bgInterrupted <- Ref.make(false)
        _ <- ZStream(1, 2, 3)
              .drainFork(
                ZStream.fromEffect(ZIO.never.onInterrupt(bgInterrupted.set(true)))
              )
              .runDrain
        result <- bgInterrupted.get
      } yield assert(result, isTrue)
    },
    testM("fails the foreground stream if the background fails with a typed error") {
      assertM(ZStream.never.drainFork(ZStream.fail("Boom")).runDrain.run, fails(equalTo("Boom")))
    },
    testM("fails the foreground stream if the background fails with a defect") {
      val ex = new RuntimeException("Boom")
      assertM(ZStream.never.drainFork(ZStream.die(ex)).runDrain.run, dies(equalTo(ex)))
    }
  )
}
