package zio.test

import zio.Clock._
import zio.test.Assertion._
import zio.test.TestAspect.failing
import zio.test.TestUtils.execute
import zio.{Clock, Has, ZIO}

object TestSpec extends ZIOBaseSpec {

  def spec: Spec[Has[Clock], TestFailure[Any], TestSuccess] = suite("TestSpec")(
    testM("assertM works correctly") {
      assertM(nanoTime)(equalTo(0L))
    },
    testM("testM error is test failure") {
      for {
        _      <- ZIO.fail("fail")
        result <- ZIO.succeed("succeed")
      } yield assert(result)(equalTo("succeed"))
    } @@ failing,
    testM("testM is polymorphic in error type") {
      for {
        _      <- ZIO.attempt(())
        result <- ZIO.succeed("succeed")
      } yield assert(result)(equalTo("succeed"))
    },
    testM("testM suspends effects") {
      var n = 0
      val spec = suite("suite")(
        testM("test1") {
          n += 1
          ZIO.succeed(assertCompletes)
        },
        testM("test2") {
          n += 1
          ZIO.succeed(assertCompletes)
        }
      ).filterLabels(_ == "test2").get
      for {
        _ <- execute(spec)
      } yield assert(n)(equalTo(1))
    }
  )
}
