package zio.test.environment

import java.util.concurrent.TimeUnit.NANOSECONDS

import zio.duration.Duration._
import zio.duration._
import zio.internal.Scheduler.CancelToken
import zio.scheduler.scheduler
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment.TestClock._
import zio.{ clock, DefaultRuntime, Promise, UIO, ZIO }
import SchedulerSpecUtil._
import zio.internal.{ Scheduler => IScheduler }

object SchedulerSpec
    extends ZIOBaseSpec(
      suite("SchedulerSpec")(
        testM("scheduled tasks get executed")(
          for {
            scheduler <- scheduler
            promise   <- Promise.make[Nothing, Unit]
            _         <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds))
            _         <- TestClock.adjust(10.seconds)
            _         <- promise.await
          } yield (assert((), anything)) //test will timeout if promise does not succeed so getting here = pass
        ),
        testM("scheduled tasks only get executed when time has passed")(
          for {
            scheduler <- scheduler
            promise   <- Promise.make[Nothing, Unit]
            _         <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds + 1.nanosecond))
            _         <- adjust(10.seconds)
            executed  <- promise.poll.map(_.nonEmpty)
          } yield assert(executed, isFalse)
        ),
        testM("scheduled tasks can be canceled")( //working
          for {
            scheduler <- scheduler
            promise   <- Promise.make[Nothing, Unit]
            cancel    <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds + 1.nanosecond))
            canceled  <- ZIO.effectTotal(cancel())
            _         <- adjust(10.seconds)
            executed  <- promise.poll.map(_.nonEmpty)
          } yield {
            assert(executed, isFalse) &&
            assert(canceled, isTrue)
          }
        ),
        testM("tasks that are cancelled after completion are not reported as interrupted")(
          for {
            scheduler <- scheduler
            promise   <- Promise.make[Nothing, Unit]
            cancel    <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds))
            _         <- adjust(10.seconds + 1.nanos)
            _         <- promise.await
            canceled  <- ZIO.effectTotal(cancel())
          } yield assert(canceled, isFalse)
        ),
        testM("scheduled tasks get executed before shutdown")(
          for {
            scheduler <- scheduler
            promise   <- Promise.make[Nothing, Unit]
            _         <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds))
            _         <- ZIO.effectTotal(scheduler.shutdown())
            _         <- promise.await //how does the test clock advance without an adjust?
            time      <- clock.currentTime(NANOSECONDS)
          } yield assert(fromNanos(time), equalTo(10.seconds))
        )
      ) @@ timeout(10.seconds) //avoid 3min penalty if the promise isn't fulfilled
    )

object SchedulerSpecUtil {
  val rt = new DefaultRuntime {}
  def runTask(scheduler: IScheduler, promise: Promise[Nothing, Unit], duration: Duration): CancelToken =
    scheduler.schedule(
      new Runnable {
        override def run(): Unit = {
          val _ = rt.unsafeRunToFuture(promise.succeed(()))
          ()
        }
      },
      duration
    )
}
