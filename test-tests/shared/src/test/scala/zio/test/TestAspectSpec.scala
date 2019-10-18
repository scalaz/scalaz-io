package zio.test

import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestAspectSpecUtil.failsWithException
import zio.test.TestUtils._
import zio.{ Cause, Ref, ZIO }

import scala.reflect.ClassTag

object TestAspectSpec
    extends ZIOBaseSpec(
      suite("TestAspectSpec")(
        testM("around evaluates tests inside context of Managed") {
          for {
            ref <- Ref.make(0)
            spec = testM("test") {
              assertM(ref.get, equalTo(1))
            } @@ around(ref.set(1), ref.set(-1))
            result <- succeeded(spec)
            after  <- ref.get
          } yield {
            assert(result, isTrue) &&
            assert(after, equalTo(-1))
          }
        },
        testM("js applies test aspect only on ScalaJS") {
          for {
            ref    <- Ref.make(false)
            spec   = test("test")(assert(true, isTrue)) @@ js(after(ref.set(true)))
            _      <- execute(spec)
            result <- ref.get
          } yield if (TestPlatform.isJS) assert(result, isTrue) else assert(result, isFalse)
        },
        testM("jsOnly runs tests only on ScalaJS") {
          val spec   = test("Javascript-only")(assert(TestPlatform.isJS, isTrue)) @@ jsOnly
          val result = if (TestPlatform.isJS) succeeded(spec) else ignored(spec)
          assertM(result, isTrue)
        },
        testM("jvm applies test aspect only on jvm") {
          for {
            ref    <- Ref.make(false)
            spec   = test("test")(assert(true, isTrue)) @@ jvm(after(ref.set(true)))
            _      <- execute(spec)
            result <- ref.get
          } yield assert(if (TestPlatform.isJVM) result else !result, isTrue)
        },
        testM("jvmOnly runs tests only on the JVM") {
          val spec   = test("JVM-only")(assert(TestPlatform.isJVM, isTrue)) @@ jvmOnly
          val result = if (TestPlatform.isJVM) succeeded(spec) else ignored(spec)
          assertM(result, isTrue)
        },
        test("failure makes a test pass if the result was a failure") {
          assert(throw new java.lang.Exception("boom"), isFalse)
        } @@ failure,
        test("failure makes a test pass if it died with a specified failure") {
          assert(throw new NullPointerException(), isFalse)
        } @@ failure(failsWithException[NullPointerException]),
        test("failure does not make a test pass if it failed with an unexpected exception") {
          assert(throw new NullPointerException(), isFalse)
        } @@ failure(failsWithException[IllegalArgumentException])
          @@ failure,
        test("failure does not make a test pass if the specified failure does not match") {
          assert(throw new RuntimeException(), isFalse)
        } @@ failure(
          isCase[TestFailure[String], Cause[String]]("Runtime", {
            case TestFailure.Runtime(e) => Some(e); case _ => None
          }, equalTo(Cause.fail("boom")))
        ) @@ failure,
        test("failure makes tests pass on any assertion failure") {
          assert(true, equalTo(false))
        } @@ failure,
        test("failure makes tests pass on an expected assertion failure") {
          assert(true, equalTo(false))
        } @@ failure(
          isCase[TestFailure[Any], Any](
            "Assertion",
            { case TestFailure.Assertion(result) => Some(result); case _ => None },
            anything
          )
        ),
        /*doesn't compile, aspects do seem to work as intended below though...
        test("test aspects do not have type inference issues") {
          val spec = testM("timeout makes tests fail after given duration") {
            assertM(ZIO.never *> ZIO.unit, equalTo(()))
          } @@ timeout(1.nanos) @@ failure(failsWithException[TestTimeoutException])
          val result = succeeded(spec)
          assertM(result, isTrue)
        },*/
        testM("timeout makes tests fail after given duration") {
          assertM(ZIO.never *> ZIO.unit, equalTo(()))
        } @@ timeout(1.nanos)
          @@ failure(failsWithException[TestTimeoutException]),
        testM("timeout reports problem with interruption") {
          assertM(ZIO.never.uninterruptible *> ZIO.unit, equalTo(()))
        } @@ timeout(10.millis, 1.nano)
          @@ failure(
            isCase[TestFailure[Any], Throwable](
              "Runtime",
              { case TestFailure.Runtime(e) => Some(e).map(_.dieOption.head); case _ => None },
              equalTo(
                TestTimeoutException(
                  "Timeout of 10 ms exceeded. Couldn't interrupt test within 1 ns, possible resource leak!"
                )
              )
            )
          )
      )
    )
object TestAspectSpecUtil {
  def failsWithException[E](implicit ct: ClassTag[E]): Assertion[TestFailure[E]] =
    isCase(
      "Runtime", {
        case TestFailure.Runtime(c) => c.dieOption
        case _                      => None
      },
      isSubtype[E](anything)
    )
}
