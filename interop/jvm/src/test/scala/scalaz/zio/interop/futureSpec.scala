package scalaz.zio
package interop

import scala.concurrent.Future

import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv

import future._

class futureSpec(implicit ee: ExecutionEnv) extends Specification with RTS {

  def is = s2"""
  `IO.fromFuture` must
    be lazy on the `Future` parameter                    $lazyOnParamRef
    be lazy on the `Future` parameter inline             $lazyOnParamInline
    catch exceptions thrown by lazy block                $catchBlockException
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFuture
    return an `IO` that produces the value from `Future` $produceValueFromFuture
  `IO.toFuture` must
    produce always a successful `IO` of `Future`         $toFutureAlwaysSucceeds
    be polymorphic in error type                         $toFuturePoly
    return a `Future` that fails if `IO` fails           $toFutureFailed
    return a `Future` that produces the value from `IO`  $toFutureValue
  `IO.toFutureE` must
    convert error of type `E` to `Throwable`             $toFutureE
  """

  val lazyOnParamRef = {
    var evaluated = false
    def ftr       = Future { evaluated = true }
    IO.fromFuture(ftr _)
    evaluated must beFalse
  }

  val lazyOnParamInline = {
    var evaluated = false
    IO.fromFuture(() => Future { evaluated = true })
    evaluated must beFalse
  }

  val catchBlockException = {
    def noFuture: Future[Unit] = throw new Exception("no future for you!")
    unsafePerformIO(IO.fromFuture(noFuture _)) must throwA[Exception](message = "no future for you!")
  }

  val propagateExceptionFromFuture = {
    def noValue: Future[Unit] = Future { throw new Exception("no value for you!") }
    unsafePerformIO(IO.fromFuture(noValue _)) must throwA[Exception](message = "no value for you!")
  }

  val produceValueFromFuture = {
    def someValue: Future[Int] = Future { 42 }
    unsafePerformIO(IO.fromFuture(someValue _)) must_=== 42
  }

  val toFutureAlwaysSucceeds = {
    val failedIO = IO.fail[Throwable, Unit](new Exception("IOs also can fail"))
    unsafePerformIO(failedIO.toFuture) must beAnInstanceOf[Future[Unit]]
  }

  val toFuturePoly = {
    val unitIO: IO[Throwable, Unit]      = IO.unit
    val polyIO: IO[String, Future[Unit]] = unitIO.toFuture[String]
    val _                                = polyIO // avoid warning
    ok
  }

  val toFutureFailed = {
    val failedIO = IO.fail[Throwable, Unit](new Exception("IOs also can fail"))
    unsafePerformIO(failedIO.toFuture) must throwA[Exception](message = "IOs also can fail").await
  }

  val toFutureValue = {
    val someIO = IO.now[Throwable, Int](42)
    unsafePerformIO(someIO.toFuture) must beEqualTo(42).await
  }

  val toFutureE = {
    val failedIO = IO.fail[String, Unit]("IOs also can fail")
    unsafePerformIO(failedIO.toFutureE(new Exception(_))) must throwA[Exception](message = "IOs also can fail").await
  }

}
