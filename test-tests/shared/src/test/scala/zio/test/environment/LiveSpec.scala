package zio.test.environment

import java.util.concurrent.TimeUnit

import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.{ clock, console }

object LiveSpec
    extends ZIOBaseSpec(
      suite("LiveSpec")(
        testM("live can access real environment") {
          for {
            test <- clock.currentTime(TimeUnit.MILLISECONDS)
            live <- Live.live(clock.currentTime(TimeUnit.MILLISECONDS))
          } yield assert(test, equalTo(0L)) && assert(live, not(equalTo(0L)))
        },
        testM("withLive provides real environment to single effect") {
          for {
            _      <- Live.withLive(console.putStr("woot"))(_.delay(1.nanosecond))
            result <- TestConsole.output
          } yield assert(result, equalTo(Vector("woot")))
        }
      )
    )
