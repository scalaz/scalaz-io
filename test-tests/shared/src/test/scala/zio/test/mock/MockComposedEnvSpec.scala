package zio.test.mock

import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.random.Random
import zio.system.System
import zio.test.{ suite, Assertion, TestAspect, ZIOBaseSpec }
import zio.{ clock, console, random, system }

object MockComposedEnvSpec extends ZIOBaseSpec with MockSpecUtils {

  import Assertion._
  import Expectation._
  import TestAspect._

  def spec = suite("MockComposedEnvSpec")(
    suite("mocking composed environments")(
      {
        val cmd1     = MockClock.NanoTime returns value(42L)
        val cmd2     = MockConsole.PutStrLn(equalTo("42")) returns unit
        val composed = (cmd1 ++ cmd2)

        val program =
          for {
            time <- clock.nanoTime
            _    <- console.putStrLn(time.toString)
          } yield ()

        testSpecComposed[Clock with Console, Nothing, Unit]("Console with Clock")(composed, program, isUnit)
      }, {
        val cmd1 = MockRandom.NextInt._1 returns value(42)
        val cmd2 = MockClock.Sleep(equalTo(42.seconds)) returns unit
        val cmd3 = MockSystem.Property(equalTo("foo")) returns value(None)
        val cmd4 = MockConsole.PutStrLn(equalTo("None")) returns unit

        val composed = (cmd1 ++ cmd2 ++ cmd3 ++ cmd4)

        val program =
          for {
            n <- random.nextInt
            _ <- clock.sleep(n.seconds)
            v <- system.property("foo")
            _ <- console.putStrLn(v.toString)
          } yield ()

        testSpecComposed[Random with Clock with System with Console, Throwable, Unit](
          "Random with Clock with System with Console"
        )(composed, program, isUnit)
      }
    ) @@ exceptDotty
  )
}
