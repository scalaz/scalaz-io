package zio.test.mock

import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.random.Random
import zio.system.System
import zio.test.{ assertM, suite, testM, Assertion, TestAspect, ZIOBaseSpec }
import zio.{ clock, console, random, system, Has, Tagged, ULayer, ZIO }

object ComposedMockSpec extends ZIOBaseSpec {

  import Assertion._
  import Expectation._
  import TestAspect._

  private def testValueComposed[R1 <: Has[_]: Tagged, E, A](name: String)(
    mock: ULayer[R1],
    app: ZIO[R1, E, A],
    check: Assertion[A]
  ) = testM(name) {
    val result = mock.build.use[R1, E, A](app.provide _)
    assertM(result)(check)
  }

  def spec = suite("ComposedMockSpec")(
    suite("mocking composed environments")(
      {
        val cmd1     = MockClock.NanoTime(value(42L))
        val cmd2     = MockConsole.PutStrLn(equalTo("42"))
        val composed = (cmd1 ++ cmd2)

        val program =
          for {
            time <- clock.nanoTime
            _    <- console.putStrLn(time.toString)
          } yield ()

        testValueComposed[Clock with Console, Nothing, Unit]("Console with Clock")(composed, program, isUnit)
      }, {
        val cmd1 = MockRandom.NextInt(value(42))
        val cmd2 = MockClock.Sleep(equalTo(42.seconds))
        val cmd3 = MockSystem.Property(equalTo("foo"), value(None))
        val cmd4 = MockConsole.PutStrLn(equalTo("None"))

        val composed = (cmd1 ++ cmd2 ++ cmd3 ++ cmd4)

        val program =
          for {
            n <- random.nextInt
            _ <- clock.sleep(n.seconds)
            v <- system.property("foo")
            _ <- console.putStrLn(v.toString)
          } yield ()

        testValueComposed[Random with Clock with System with Console, Throwable, Unit](
          "Random with Clock with System with Console"
        )(composed, program, isUnit)
      }
    ) @@ exceptDotty
  )
}
