package zio

import zio.LatchOps._
import zio.ZIOSpecHelper._
import zio.clock.Clock
import zio.duration._
import zio.test._
import zio.test.environment._
import zio.test.Assertion._
import zio.test.TestAspect.{ flaky, ignore, jvm, nonFlaky }

import scala.annotation.tailrec
import scala.util.{ Failure, Success }

object ZIOSpec
    extends ZIOBaseSpec(
      suite("ZIO")(
        suite("bracket")(
          testM("bracket happy path") {
            for {
              release  <- Ref.make(false)
              result   <- ZIO.bracket(IO.succeed(42), (_: Int) => release.set(true), (a: Int) => ZIO.effectTotal(a + 1))
              released <- release.get
            } yield assert(result, equalTo(43)) && assert(released, isTrue)
          },
          testM("bracket_ happy path") {
            for {
              release  <- Ref.make(false)
              result   <- IO.succeed(42).bracket_(release.set(true), ZIO.effectTotal(0))
              released <- release.get
            } yield assert(result, equalTo(0)) && assert(released, isTrue)
          },
          testM("bracketExit happy path") {
            for {
              release <- Ref.make(false)
              result <- ZIO.bracketExit(
                         IO.succeed(42),
                         (_: Int, _: Exit[Any, Any]) => release.set(true),
                         (_: Int) => IO.succeed(0L)
                       )
              released <- release.get
            } yield assert(result, equalTo(0L)) && assert(released, isTrue)
          },
          testM("bracketExit error handling") {
            val releaseDied: Throwable = new RuntimeException("release died")
            for {
              exit <- ZIO
                       .bracketExit[Any, String, Int, Int](
                         ZIO.succeed(42),
                         (_, _) => ZIO.die(releaseDied),
                         _ => ZIO.fail("use failed")
                       )
                       .run
              cause <- exit.foldM(cause => ZIO.succeed(cause), _ => ZIO.fail("effect should have failed"))
            } yield assert(cause.failures, equalTo(List("use failed"))) &&
              assert(cause.defects, equalTo(List(releaseDied)))
          }
        ),
        suite("foreachPar")(
          testM("runs effects in parallel") {
            assertM(for {
              p <- Promise.make[Nothing, Unit]
              _ <- UIO.foreachPar(List(UIO.never, p.succeed(())))(a => a).fork
              _ <- p.await
            } yield true, isTrue)
          },
          testM("propagates error") {
            val ints = List(1, 2, 3, 4, 5, 6)
            val odds = ZIO.foreachPar(ints) { n =>
              if (n % 2 != 0) ZIO.succeed(n) else ZIO.fail("not odd")
            }
            assertM(odds.flip, equalTo("not odd"))
          },
          testM("interrupts effects on first failure") {
            for {
              ref     <- Ref.make(false)
              promise <- Promise.make[Nothing, Unit]
              actions = List(
                ZIO.never,
                ZIO.succeed(1),
                ZIO.fail("C"),
                promise.await *> ref.set(true)
              )
              e <- ZIO.foreachPar(actions)(a => a).flip
              v <- ref.get
            } yield assert(e, equalTo("C")) && assert(v, isFalse)
          }
        ),
        suite("forkAll")(
          testM("happy-path") {
            val list = (1 to 1000).toList
            assertM(ZIO.forkAll(list.map(a => ZIO.effectTotal(a))).flatMap(_.join), equalTo(list))
          },
          testM("empty input") {
            assertM(ZIO.forkAll(List.empty).flatMap(_.join), equalTo(List.empty))
          },
          testM("propagate failures") {
            val boom = new Exception
            for {
              fiber  <- ZIO.forkAll(List(ZIO.fail(boom)))
              result <- fiber.join.flip
            } yield assert(result, equalTo(boom))
          },
          testM("propagates defects") {
            val boom = new Exception("boom")
            for {
              fiber  <- ZIO.forkAll(List(ZIO.die(boom)))
              result <- fiber.join.sandbox.flip
            } yield assert(result, equalTo(Cause.die(boom)))
          }
        ),
        suite("head")(
          testM("on non empty list") {
            assertM(ZIO.succeed(List(1, 2, 3)).head.either, isRight(equalTo(1)))
          },
          testM("on empty list") {
            assertM(ZIO.succeed(List.empty).head.either, isLeft(isNone))
          },
          testM("on failure") {
            assertM(ZIO.fail("Fail").head.either, isLeft(isSome(equalTo("Fail"))))
          }
        ),
        suite("left")(
          testM("on Left value") {
            assertM(ZIO.succeed(Left("Left")).left, equalTo("Left"))
          },
          testM("on Right value") {
            assertM(ZIO.succeed(Right("Right")).left.either, isLeft(isNone))
          },
          testM("on failure") {
            assertM(ZIO.fail("Fail").left.either, isLeft(isSome(equalTo("Fail"))))
          }
        ),
        suite("leftOrFail")(
          testM("on Left value") {
            assertM(UIO(Left(42)).leftOrFail(ExampleError), equalTo(42))
          },
          testM("on Right value") {
            assertM(UIO(Right(12)).leftOrFail(ExampleError).flip, equalTo(ExampleError))
          }
        ),
        suite("leftOrFailException")(
          testM("on Left value") {
            assertM(ZIO.succeed(Left(42)).leftOrFailException, equalTo(42))
          },
          testM("on Right value") {
            assertM(ZIO.succeed(Right(2)).leftOrFailException.run, fails(Assertion.anything))
          }
        ),
        suite("parallelErrors")(
          testM("oneFailure") {
            for {
              f1     <- IO.fail("error1").fork
              f2     <- IO.succeed("success1").fork
              errors <- f1.zip(f2).join.parallelErrors[String].flip
            } yield assert(errors, equalTo(List("error1")))
          },
          testM("allFailures") {
            for {
              f1     <- IO.fail("error1").fork
              f2     <- IO.fail("error2").fork
              errors <- f1.zip(f2).join.parallelErrors[String].flip
            } yield assert(errors, equalTo(List("error1", "error2")))
          }
        ),
        suite("raceAll")(
          testM("returns first success") {
            assertM(ZIO.fail("Fail").raceAll(List(IO.succeed(24))), equalTo(24))
          },
          testM("returns last failure") {
            assertM(live(ZIO.sleep(100.millis) *> ZIO.fail(24)).raceAll(List(ZIO.fail(25))).flip, equalTo(24))
          },
          testM("returns success when it happens after failure") {
            assertM(ZIO.fail(42).raceAll(List(IO.succeed(24) <* live(ZIO.sleep(100.millis)))), equalTo(24))
          }
        ),
        suite("option")(
          testM("return success in Some") {
            assertM(ZIO.succeed(11).option, equalTo(Some(11)))
          },
          testM("return failure as None") {
            assertM(ZIO.fail(123).option, equalTo(None))
          },
          testM("not catch throwable") {
            assertM(ZIO.die(ExampleError).option.run, dies(equalTo(ExampleError)))
          },
          testM("catch throwable after sandboxing") {
            assertM(ZIO.die(ExampleError).sandbox.option, equalTo(None))
          }
        ),
        suite("replicate")(
          testM("zero") {
            val lst: Iterable[UIO[Int]] = ZIO.replicate(0)(ZIO.succeed(12))
            assertM(ZIO.sequence(lst), equalTo(List.empty))
          },
          testM("negative") {
            val anotherList: Iterable[UIO[Int]] = ZIO.replicate(-2)(ZIO.succeed(12))
            assertM(ZIO.sequence(anotherList), equalTo(List.empty))
          },
          testM("positive") {
            val lst: Iterable[UIO[Int]] = ZIO.replicate(2)(ZIO.succeed(12))
            assertM(ZIO.sequence(lst), equalTo(List(12, 12)))
          }
        ),
        suite("right")(
          testM("on Right value") {
            assertM(ZIO.succeed(Right("Right")).right, equalTo("Right"))
          },
          testM("on Left value") {
            assertM(ZIO.succeed(Left("Left")).right.either, isLeft(isNone))
          },
          testM("on failure") {
            assertM(ZIO.fail("Fail").right.either, isLeft(isSome(equalTo("Fail"))))
          }
        ),
        suite("rightOrFail")(
          testM("on Right value") {
            assertM(UIO(Right(42)).rightOrFail(ExampleError), equalTo(42))
          },
          testM("on Left value") {
            assertM(UIO(Left(1)).rightOrFail(ExampleError).flip, equalTo(ExampleError))
          }
        ),
        suite("rightOrFailException")(
          testM("on Right value") {
            assertM(ZIO.succeed(Right(42)).rightOrFailException, equalTo(42))
          },
          testM("on Left value") {
            assertM(ZIO.succeed(Left(2)).rightOrFailException.run, fails(Assertion.anything))
          }
        ),
        suite("RTS synchronous correctness")(
          testM("widen Nothing") {
            val op1 = IO.effectTotal[String]("1")
            val op2 = IO.effectTotal[String]("2")

            assertM(op1.zipWith(op2)(_ + _), equalTo("12"))
          },
          testM("now must be eager") {
            val io =
              try {
                IO.succeed(throw ExampleError)
                IO.succeed(false)
              } catch {
                case _: Throwable => IO.succeed(true)
              }

            assertM(io, isTrue)
          },
          testM("effectSuspend must be lazy") {
            val io =
              try {
                IO.effectSuspend(throw ExampleError)
                IO.succeed(false)
              } catch {
                case _: Throwable => IO.succeed(true)
              }

            assertM(io, isFalse)
          },
          testM("effectSuspendTotal must not catch throwable") {
            val io = ZIO.effectSuspendTotal[Any, Nothing, Any](throw ExampleError).sandbox.either
            assertM(io, isLeft(equalTo(Cause.die(ExampleError))))
          },
          testM("effectSuspend must catch throwable") {
            val io = ZIO.effectSuspend[Any, Nothing](throw ExampleError).either
            assertM(io, isLeft(equalTo(ExampleError)))
          },
          testM("effectSuspendWith must catch throwable") {
            val io = ZIO.effectSuspendWith[Any, Nothing](_ => throw ExampleError).either
            assertM(io, isLeft(equalTo(ExampleError)))
          },
          testM("effectSuspendTotal must be evaluatable") {
            assertM(IO.effectSuspendTotal(IO.effectTotal(42)), equalTo(42))
          },
          testM("point, bind, map") {
            def fibIo(n: Int): Task[BigInt] =
              if (n <= 1) IO.succeed(n)
              else
                for {
                  a <- fibIo(n - 1)
                  b <- fibIo(n - 2)
                } yield a + b

            assertM(fibIo(10), equalTo(fib(10)))
          },
          testM("effect, bind, map") {
            def fibIo(n: Int): Task[BigInt] =
              if (n <= 1) IO.effect(n)
              else
                for {
                  a <- fibIo(n - 1)
                  b <- fibIo(n - 2)
                } yield a + b

            assertM(fibIo(10), equalTo(fib(10)))
          },
          testM("effect, bind, map, redeem") {
            def fibIo(n: Int): Task[BigInt] =
              if (n <= 1) Task.effect[BigInt](throw ExampleError).catchAll(_ => Task.effect(n))
              else
                for {
                  a <- fibIo(n - 1)
                  b <- fibIo(n - 2)
                } yield a + b

            assertM(fibIo(10), equalTo(fib(10)))
          },
          testM("sync effect") {
            def sumIo(n: Int): Task[Int] =
              if (n <= 0) IO.effectTotal(0)
              else IO.effectTotal(n).flatMap(b => sumIo(n - 1).map(a => a + b))

            assertM(sumIo(1000), equalTo(sum(1000)))
          },
          testM("deep effects") {
            def incLeft(n: Int, ref: Ref[Int]): Task[Int] =
              if (n <= 0) ref.get
              else incLeft(n - 1, ref) <* ref.update(_ + 1)

            def incRight(n: Int, ref: Ref[Int]): Task[Int] =
              if (n <= 0) ref.get
              else ref.update(_ + 1) *> incRight(n - 1, ref)

            val l =
              for {
                ref <- Ref.make(0)
                v   <- incLeft(100, ref)
              } yield v == 0

            val r =
              for {
                ref <- Ref.make(0)
                v   <- incRight(1000, ref)
              } yield v == 1000

            assertM(l.zipWith(r)(_ && _), isTrue)
          },
          testM("flip must make error into value") {
            val io = IO.fail(ExampleError).flip
            assertM(io, equalTo(ExampleError))
          },
          testM("flip must make value into error") {
            val io = IO.succeed(42).flip
            assertM(io.either, isLeft(equalTo(42)))
          },
          testM("flipping twice returns identical value") {
            val io = IO.succeed(42)
            assertM(io.flip.flip, equalTo(42))
          }
        ),
        suite("RTS failure")(
          testM("error in sync effect") {
            val io = IO.effect[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None)
            assertM(io, isSome(equalTo(ExampleError)))
          },
          testM("attempt . fail") {
            val io1 = TaskExampleError.either
            val io2 = IO.effectSuspendTotal(IO.effectSuspendTotal(TaskExampleError).either)

            io1.zipWith(io2) {
              case (r1, r2) =>
                assert(r1, isLeft(equalTo(ExampleError))) && assert(r2, isLeft(equalTo(ExampleError)))
            }
          },
          testM("deep attempt sync effect error") {
            assertM(deepErrorEffect(100).either, isLeft(equalTo(ExampleError)))
          },
          testM("deep attempt fail error") {
            assertM(deepErrorFail(100).either, isLeft(equalTo(ExampleError)))
          },
          testM("attempt . sandbox . terminate") {
            val io = IO.effectTotal[Int](throw ExampleError).sandbox.either
            assertM(io, isLeft(equalTo(Cause.die(ExampleError))))
          },
          testM("fold . sandbox . terminate") {
            val io = IO.effectTotal[Int](throw ExampleError).sandbox.fold(Some(_), Function.const(None))
            assertM(io, isSome(equalTo(Cause.die(ExampleError))))
          },
          testM("catch sandbox terminate") {
            val io = IO.effectTotal(throw ExampleError).sandbox.fold(identity, identity)
            assertM(io, equalTo(Cause.die(ExampleError)))
          },
          testM("uncaught fail") {
            assertM(TaskExampleError.run, fails(equalTo(ExampleError)))
          },
          testM("uncaught fail supervised") {
            val io = Task.fail(ExampleError).interruptChildren
            assertM(io.run, fails(equalTo(ExampleError)))
          },
          testM("uncaught sync effect error") {
            val io = IO.effectTotal[Int](throw ExampleError)
            assertM(io.run, dies(equalTo(ExampleError)))
          },
          testM("uncaught supervised sync effect error") {
            val io = IO.effectTotal[Int](throw ExampleError).interruptChildren
            assertM(io.run, dies(equalTo(ExampleError)))
          },
          testM("deep uncaught sync effect error") {
            assertM(deepErrorEffect(100).run, fails(equalTo(ExampleError)))
          },
          testM("catch failing finalizers with fail") {
            val io = IO
              .fail(ExampleError)
              .ensuring(IO.effectTotal(throw InterruptCause1))
              .ensuring(IO.effectTotal(throw InterruptCause2))
              .ensuring(IO.effectTotal(throw InterruptCause3))

            val expectedCause = Cause.fail(ExampleError) ++
              Cause.die(InterruptCause1) ++
              Cause.die(InterruptCause2) ++
              Cause.die(InterruptCause3)

            assertM(io.run, equalTo(Exit.halt(expectedCause)))
          },
          testM("catch failing finalizers with terminate") {
            val io = IO
              .die(ExampleError)
              .ensuring(IO.effectTotal(throw InterruptCause1))
              .ensuring(IO.effectTotal(throw InterruptCause2))
              .ensuring(IO.effectTotal(throw InterruptCause3))

            val expectedCause = Cause.die(ExampleError) ++
              Cause.die(InterruptCause1) ++
              Cause.die(InterruptCause2) ++
              Cause.die(InterruptCause3)

            assertM(io.run, equalTo(Exit.halt(expectedCause)))
          },
          testM("run preserves interruption status") {
            for {
              p    <- Promise.make[Nothing, Unit]
              f    <- (p.succeed(()) *> IO.never).run.fork
              _    <- p.await
              _    <- f.interrupt
              test <- f.await.map(_.interrupted)
            } yield assert(test, isTrue)
          },
          testM("run swallows inner interruption") {
            for {
              p   <- Promise.make[Nothing, Int]
              _   <- IO.interrupt.run *> p.succeed(42)
              res <- p.await
            } yield assert(res, equalTo(42))
          },
          testM("timeout a long computation") {
            val io = (clock.sleep(5.seconds) *> IO.succeed(true)).timeout(10.millis)
            assertM(io.provide(Clock.Live), isNone)
          },
          testM("catchAllCause") {
            val io =
              for {
                _ <- ZIO.succeed(42)
                f <- ZIO.fail("Uh oh!")
              } yield f

            assertM(io.catchAllCause(ZIO.succeed), equalTo(Cause.fail("Uh oh!")))
          },
          testM("exception in fromFuture does not kill fiber") {
            val io = ZIO.fromFuture(_ => throw ExampleError).either
            assertM(io, isLeft(equalTo(ExampleError)))
          }
        ),
        suite("RTS finalizers")(
          testM("fail ensuring") {
            var finalized = false

            val io = Task.fail(ExampleError).ensuring(IO.effectTotal { finalized = true; () })

            for {
              a1 <- assertM(io.run, fails(equalTo(ExampleError)))
              a2 = assert(finalized, isTrue)
            } yield a1 && a2
          },
          testM("fail on error") {
            @volatile var finalized = false

            val cleanup: Cause[Throwable] => UIO[Unit] =
              _ => IO.effectTotal[Unit] { finalized = true; () }

            val io = Task.fail(ExampleError).onError(cleanup)

            for {
              a1 <- assertM(io.run, fails(equalTo(ExampleError)))
              a2 = assert(finalized, isTrue)
            } yield a1 && a2
          },
          testM("finalizer errors not caught") {
            val e2 = new Error("e2")
            val e3 = new Error("e3")

            val io = TaskExampleError.ensuring(IO.die(e2)).ensuring(IO.die(e3))

            val expectedCause: Cause[Throwable] =
              Cause.Then(Cause.fail(ExampleError), Cause.Then(Cause.die(e2), Cause.die(e3)))

            assertM(io.sandbox.flip, equalTo(expectedCause))
          },
          testM("finalizer errors reported") {
            @volatile var reported: Exit[Nothing, Int] = null

            val io = IO
              .succeed[Int](42)
              .ensuring(IO.die(ExampleError))
              .fork
              .flatMap(_.await.flatMap[Any, Nothing, Any](e => UIO.effectTotal { reported = e }))

            for {
              a1 <- assertM(io, isUnit)
              a2 = assert(reported.succeeded, isFalse)
            } yield a1 && a2
          },
          testM("bracket exit is usage result") {
            val io = IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.succeed[Int](42))
            assertM(io, equalTo(42))
          },
          testM("error in just acquisition") {
            val io = IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit)
            assertM(io.run, fails(equalTo(ExampleError)))
          },
          testM("error in just release") {
            val io = IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)
            assertM(io.run, dies(equalTo(ExampleError)))
          },
          testM("error in just usage") {
            val io = IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.fail(ExampleError))
            assertM(io.run, fails(equalTo(ExampleError)))
          },
          testM("rethrown caught error in acquisition") {
            val io = IO.absolve(IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit).either)
            assertM(io.flip, equalTo(ExampleError))
          },
          testM("rethrown caught error in release") {
            val io = IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)
            assertM(io.run, dies(equalTo(ExampleError)))
          },
          testM("rethrown caught error in usage") {
            val io = IO.absolve(IO.unit.bracket_(IO.unit)(TaskExampleError).either)
            assertM(io.run, fails(equalTo(ExampleError)))
          },
          testM("test eval of async fail") {
            val io1 = IO.unit.bracket_(AsyncUnit[Nothing])(asyncExampleError[Unit])
            val io2 = AsyncUnit[Throwable].bracket_(IO.unit)(asyncExampleError[Unit])

            for {
              a1 <- assertM(io1.run, fails(equalTo(ExampleError)))
              a2 <- assertM(io2.run, fails(equalTo(ExampleError)))
              a3 <- assertM(IO.absolve(io1.either).run, fails(equalTo(ExampleError)))
              a4 <- assertM(IO.absolve(io2.either).run, fails(equalTo(ExampleError)))
            } yield a1 && a2 && a3 && a4
          },
          testM("bracket regression 1") {
            def makeLogger: Ref[List[String]] => String => UIO[Unit] =
              (ref: Ref[List[String]]) => (line: String) => ref.update(_ ::: List(line)).unit

            val io =
              for {
                ref <- Ref.make[List[String]](Nil)
                log = makeLogger(ref)
                f <- ZIO
                      .bracket(
                        ZIO.bracket(ZIO.unit)(_ => log("start 1") *> clock.sleep(10.millis) *> log("release 1"))(
                          _ => ZIO.unit
                        )
                      )(_ => log("start 2") *> clock.sleep(10.millis) *> log("release 2"))(_ => ZIO.unit)
                      .fork
                _ <- (ref.get <* clock.sleep(1.millis)).repeat(ZSchedule.doUntil[List[String]](_.contains("start 1")))
                _ <- f.interrupt
                _ <- (ref.get <* clock.sleep(1.millis)).repeat(ZSchedule.doUntil[List[String]](_.contains("release 2")))
                l <- ref.get
              } yield l

            assertM(io.provide(Clock.Live), hasSameElements(List("start 1", "release 1", "start 2", "release 2")))
          },
          testM("interrupt waits for finalizer") {
            val io =
              for {
                r  <- Ref.make(false)
                p1 <- Promise.make[Nothing, Unit]
                p2 <- Promise.make[Nothing, Int]
                s <- (p1.succeed(()) *> p2.await)
                      .ensuring(r.set(true) *> clock.sleep(10.millis))
                      .fork
                _    <- p1.await
                _    <- s.interrupt
                test <- r.get
              } yield test

            assertM(io.provide(Clock.Live), isTrue)
          }
        ),
        suite("RTS synchronous stack safety")(
          testM("deep map of now") {
            assertM(deepMapNow(10000), equalTo(10000))
          },
          testM("deep map of sync effect") {
            assertM(deepMapEffect(10000), equalTo(10000))
          },
          testM("deep attempt") {
            val io = (0 until 10000).foldLeft(IO.effect(())) { (acc, _) =>
              acc.either.unit
            }
            assertM(io, equalTo(()))
          },
          testM("deep flatMap") {
            def fib(n: Int, a: BigInt = 0, b: BigInt = 1): IO[Error, BigInt] =
              IO.succeed(a + b).flatMap { b2 =>
                if (n > 0)
                  fib(n - 1, b, b2)
                else
                  IO.succeed(b2)
              }

            val expected = BigInt(
              "113796925398360272257523782552224175572745930353730513145086634176691092536145985470146129334641866902783673042322088625863396052888690096969577173696370562180400527049497109023054114771394568040040412172632376"
            )

            assertM(fib(1000), equalTo(expected))
          },
          testM("deep absolve/attempt is identity") {
            val io = (0 until 1000).foldLeft(IO.succeed(42)) { (acc, _) =>
              IO.absolve(acc.either)
            }

            assertM(io, equalTo(42))
          },
          testM("deep async absolve/attempt is identity") {
            val io = (0 until 1000).foldLeft(IO.effectAsync[Int, Int](k => k(IO.succeed(42)))) { (acc, _) =>
              IO.absolve(acc.either)
            }

            assertM(io, equalTo(42))
          }
        ),
        suite("RTS asynchronous correctness")(
          testM("simple async must return") {
            val io = IO.effectAsync[Throwable, Int](k => k(IO.succeed(42)))
            assertM(io, equalTo(42))
          },
          testM("simple asyncIO must return") {
            val io = IO.effectAsyncM[Throwable, Int](k => IO.effectTotal(k(IO.succeed(42))))
            assertM(io, equalTo(42))
          },
          testM("deep asyncIO doesn't block threads") {
            def stackIOs(clock: Clock.Service[Any], count: Int): UIO[Int] =
              if (count <= 0) IO.succeed(42)
              else asyncIO(clock, stackIOs(clock, count - 1))

            def asyncIO(clock: Clock.Service[Any], cont: UIO[Int]): UIO[Int] =
              IO.effectAsyncM[Nothing, Int] { k =>
                clock.sleep(5.millis) *> cont *> IO.effectTotal(k(IO.succeed(42)))
              }

            val procNum = java.lang.Runtime.getRuntime.availableProcessors()

            val io = clock.clockService.flatMap(stackIOs(_, procNum + 1))

            assertM(io.provide(Clock.Live), equalTo(42))
          },
          testM("interrupt of asyncPure register") {
            for {
              release <- Promise.make[Nothing, Unit]
              acquire <- Promise.make[Nothing, Unit]
              fiber <- IO
                        .effectAsyncM[Nothing, Unit] { _ =>
                          acquire.succeed(()).bracket(_ => release.succeed(()))(_ => IO.never)
                        }
                        .fork
              _ <- acquire.await
              _ <- fiber.interrupt.fork
              a <- release.await
            } yield assert(a, isUnit)
          },
          testM("sleep 0 must return") {
            assertM(clock.sleep(1.nanos).provide(Clock.Live), isUnit)
          },
          testM("shallow bind of async chain") {
            val io = (0 until 10).foldLeft[Task[Int]](IO.succeed[Int](0)) { (acc, _) =>
              acc.flatMap(n => IO.effectAsync[Throwable, Int](_(IO.succeed(n + 1))))
            }

            assertM(io, equalTo(10))
          },
          testM("effectAsyncM can fail before registering") {
            val zio = ZIO
              .effectAsyncM[Any, String, Nothing](_ => ZIO.fail("Ouch"))
              .flip

            assertM(zio, equalTo("Ouch"))
          },
          testM("effectAsyncM can defect before registering") {
            val zio = ZIO
              .effectAsyncM[Any, String, Unit](_ => ZIO.effectTotal(throw new Error("Ouch")))
              .run
              .map(_.fold(_.defects.headOption.map(_.getMessage), _ => None))

            assertM(zio, isSome(equalTo("Ouch")))
          }
        ),
        suite("RTS concurrency correctness")(
          testM("shallow fork/join identity") {
            for {
              f <- IO.succeed(42).fork
              r <- f.join
            } yield assert(r, equalTo(42))
          },
          testM("deep fork/join identity") {
            val n = 20
            assertM(concurrentFib(n), equalTo(fib(n)))
          },
          testM("asyncPure creation is interruptible") {
            for {
              release <- Promise.make[Nothing, Int]
              acquire <- Promise.make[Nothing, Unit]
              task = IO.effectAsyncM[Nothing, Unit] { _ =>
                IO.bracket(acquire.succeed(()))(_ => release.succeed(42).unit)(_ => IO.never)
              }
              fiber <- task.fork
              _     <- acquire.await
              _     <- fiber.interrupt
              a     <- release.await
            } yield assert(a, equalTo(42))
          },
          testM("asyncInterrupt runs cancel token on interrupt") {
            for {
              release <- Promise.make[Nothing, Int]
              latch   = scala.concurrent.Promise[Unit]()
              async = IO.effectAsyncInterrupt[Nothing, Nothing] { _ =>
                latch.success(()); Left(release.succeed(42).unit)
              }
              fiber <- async.fork
              _ <- IO.effectAsync[Throwable, Unit] { k =>
                    latch.future.onComplete {
                      case Success(a) => k(IO.succeed(a))
                      case Failure(t) => k(IO.fail(t))
                    }(scala.concurrent.ExecutionContext.global)
                  }
              _      <- fiber.interrupt
              result <- release.await
            } yield assert(result, equalTo(42))
          },
          testM("supervising returns fiber refs") {
            def forkAwaitStart(ref: Ref[List[Fiber[Any, Any]]]) =
              withLatch(release => (release *> UIO.never).fork.tap(fiber => ref.update(fiber :: _)))

            val io =
              for {
                ref <- Ref.make(List.empty[Fiber[Any, Any]])
                f1  <- ZIO.children
                _   <- forkAwaitStart(ref)
                f2  <- ZIO.children
                _   <- forkAwaitStart(ref)
                f3  <- ZIO.children
              } yield assert(f1, isEmpty) && assert(f2, hasSize(equalTo(1))) && assert(f3, hasSize(equalTo(2)))

            io.supervised
          } @@ flaky,
          testM("supervising in unsupervised returns Nil") {
            for {
              ref  <- Ref.make(Option.empty[Fiber[Any, Any]])
              _    <- withLatch(release => (release *> UIO.never).fork.tap(fiber => ref.set(Some(fiber))))
              fibs <- ZIO.children
            } yield assert(fibs, isEmpty)
          } @@ flaky,
          testM("supervise fibers") {
            def makeChild(n: Int, fibers: Ref[List[Fiber[Any, Any]]]) =
              (clock.sleep(20.millis * n.toDouble) *> IO.unit).fork.tap(fiber => fibers.update(fiber :: _))

            val io =
              for {
                fibers  <- Ref.make(List.empty[Fiber[Any, Any]])
                counter <- Ref.make(0)
                _ <- (makeChild(1, fibers) *> makeChild(2, fibers)).handleChildrenWith { fs =>
                      fs.foldLeft(IO.unit)((io, f) => io *> f.join.either *> counter.update(_ + 1).unit)
                    }
                value <- counter.get
              } yield value

            assertM(io.provide(Clock.Live), equalTo(2))
          } @@ flaky,
          testM("supervise fibers in supervised") {
            for {
              pa <- Promise.make[Nothing, Int]
              pb <- Promise.make[Nothing, Int]
              _ <- (for {
                    p1 <- Promise.make[Nothing, Unit]
                    p2 <- Promise.make[Nothing, Unit]
                    _  <- p1.succeed(()).bracket_(pa.succeed(1).unit)(IO.never).fork
                    _  <- p2.succeed(()).bracket_(pb.succeed(2).unit)(IO.never).fork
                    _  <- p1.await *> p2.await
                  } yield ()).interruptChildren
              r <- pa.await zip pb.await
            } yield assert(r, equalTo((1, 2)))
          } @@ ignore,
          testM("supervise fibers in race") {
            for {
              pa <- Promise.make[Nothing, Int]
              pb <- Promise.make[Nothing, Int]

              p1 <- Promise.make[Nothing, Unit]
              p2 <- Promise.make[Nothing, Unit]
              f <- (
                    p1.succeed(()).bracket_(pa.succeed(1).unit)(IO.never) race
                      p2.succeed(()).bracket_(pb.succeed(2).unit)(IO.never)
                  ).interruptChildren.fork
              _ <- p1.await *> p2.await

              _ <- f.interrupt
              r <- pa.await zip pb.await
            } yield assert(r, equalTo((1, 2)))
          } @@ flaky,
          testM("supervise fibers in fork") {
            val io =
              for {
                pa <- Promise.make[Nothing, Int]
                pb <- Promise.make[Nothing, Int]

                p1 <- Promise.make[Nothing, Unit]
                p2 <- Promise.make[Nothing, Unit]
                f <- (
                      p1.succeed(()).bracket_(pa.succeed(1).unit)(IO.never).fork *>
                        p2.succeed(()).bracket_(pb.succeed(2).unit)(IO.never).fork *>
                        IO.never
                    ).interruptChildren.fork
                _ <- p1.await *> p2.await

                _ <- f.interrupt
                r <- pa.await zip pb.await
              } yield r

            assertM(io.eventually, equalTo((1, 2)))
          } @@ ignore,
          testM("race of fail with success") {
            val io = IO.fail(42).race(IO.succeed(24)).either
            assertM(io, isRight(equalTo(24)))
          },
          testM("race of terminate with success") {
            val io = IO.die(new Throwable {}).race(IO.succeed(24)).either
            assertM(io, isRight(equalTo(24)))
          },
          testM("race of fail with fail") {
            val io = IO.fail(42).race(IO.fail(42)).either
            assertM(io, isLeft(equalTo(42)))
          },
          testM("race of value & never") {
            val io = IO.effectTotal(42).race(IO.never)
            assertM(io, equalTo(42))
          },
          testM("firstSuccessOf of values") {
            val io = IO.firstSuccessOf(IO.fail(0), List(IO.succeed(100))).either
            assertM(io, isRight(equalTo(100)))
          },
          testM("firstSuccessOf of failures") {
            val io = ZIO.firstSuccessOf(IO.fail(0).delay(10.millis), List(IO.fail(101))).either
            assertM(io.provide(Clock.Live), isLeft(equalTo(101)))
          },
          testM("firstSuccessOF of failures & 1 success") {
            val io = ZIO.firstSuccessOf(IO.fail(0), List(IO.succeed(102).delay(1.millis))).either
            assertM(io.provide(Clock.Live), isRight(equalTo(102)))
          },
          testM("raceAttempt interrupts loser on success") {
            for {
              s      <- Promise.make[Nothing, Unit]
              effect <- Promise.make[Nothing, Int]
              winner = s.await *> IO.fromEither(Right(()))
              loser  = IO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => IO.never)
              race   = winner raceAttempt loser
              _      <- race.either
              b      <- effect.await
            } yield assert(b, equalTo(42))
          },
          testM("raceAttempt interrupts loser on failure") {
            for {
              s      <- Promise.make[Nothing, Unit]
              effect <- Promise.make[Nothing, Int]
              winner = s.await *> IO.fromEither(Left(new Exception))
              loser  = IO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => IO.never)
              race   = winner raceAttempt loser
              _      <- race.either
              b      <- effect.await
            } yield assert(b, equalTo(42))
          },
          testM("par regression") {
            val io = IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => IO.succeed(t._1 + t._2)).map(_ == 3)
            assertM(io, isTrue)
          } @@ jvm(nonFlaky(100)),
          testM("par of now values") {
            def countdown(n: Int): UIO[Int] =
              if (n == 0) IO.succeed(0)
              else
                IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => countdown(n - 1).map(y => t._1 + t._2 + y))

            assertM(countdown(50), equalTo(150))
          },
          testM("mergeAll") {
            val io = IO.mergeAll(List("a", "aa", "aaa", "aaaa").map(IO.succeed[String](_)))(0) { (b, a) =>
              b + a.length
            }

            assertM(io, equalTo(10))
          },
          testM("mergeAllEmpty") {
            val io = IO.mergeAll(List.empty[UIO[Int]])(0)(_ + _)
            assertM(io, equalTo(0))
          },
          testM("reduceAll") {
            val io = IO.reduceAll(IO.effectTotal(1), List(2, 3, 4).map(IO.succeed[Int](_)))(_ + _)
            assertM(io, equalTo(10))
          },
          testM("reduceAll Empty List") {
            val io = IO.reduceAll(IO.effectTotal(1), Seq.empty)(_ + _)
            assertM(io, equalTo(1))
          },
          testM("timeout of failure") {
            val io = IO.fail("Uh oh").timeout(1.hour)
            assertM(io.provide(Clock.Live).run, fails(equalTo("Uh oh")))
          },
          testM("timeout of terminate") {
            val io: ZIO[Clock, Nothing, Option[Int]] = IO.die(ExampleError).timeout(1.hour)
            assertM(io.provide(Clock.Live).run, dies(equalTo(ExampleError)))
          }
        ),
        suite("RTS option tests")(
          testM("lifting a value to an option") {
            assertM(ZIO.some(42), isSome(equalTo(42)))
          },
          testM("using the none value") {
            assertM(ZIO.none, isNone)
          }
        ),
        suite("RTS either helper tests")(
          testM("lifting a value into right") {
            assertM(ZIO.right(42), isRight(equalTo(42)))
          },
          testM("lifting a value into left") {
            assertM(ZIO.left(42), isLeft(equalTo(42)))
          }
        ),
        suite("RTS interruption")(
          testM("sync forever is interruptible") {
            val io =
              for {
                f <- IO.effectTotal[Int](1).forever.fork
                _ <- f.interrupt
              } yield true

            assertM(io, isTrue)
          },
          testM("interrupt of never") {
            val io =
              for {
                fiber <- IO.never.fork
                _     <- fiber.interrupt
              } yield 42

            assertM(io, equalTo(42))
          },
          testM("asyncPure is interruptible") {
            val io =
              for {
                fiber <- IO.effectAsyncM[Nothing, Nothing](_ => IO.never).fork
                _     <- fiber.interrupt
              } yield 42

            assertM(io, equalTo(42))
          },
          testM("async is interruptible") {
            val io =
              for {
                fiber <- IO.effectAsync[Nothing, Nothing](_ => ()).fork
                _     <- fiber.interrupt
              } yield 42

            assertM(io, equalTo(42))
          },
          testM("bracket is uninterruptible") {
            val io =
              for {
                promise <- Promise.make[Nothing, Unit]
                fiber   <- (promise.succeed(()) <* IO.never).bracket(_ => IO.unit)(_ => IO.unit).fork
                res     <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
              } yield res

            assertM(io.provide(Clock.Live), equalTo(42))
          },
          testM("bracketExit is uninterruptible") {
            val io =
              for {
                promise <- Promise.make[Nothing, Unit]
                fiber <- IO
                          .bracketExit(promise.succeed(()) *> IO.never *> IO.succeed(1))(
                            (_, _: Exit[Any, Any]) => IO.unit
                          )(
                            _ => IO.unit: IO[Nothing, Unit]
                          )
                          .fork
                res <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
              } yield res

            assertM(io.provide(Clock.Live), equalTo(42))
          },
          testM("bracket use is interruptible") {
            for {
              fiber <- IO.unit.bracket(_ => IO.unit)(_ => IO.never).fork
              res   <- fiber.interrupt
            } yield assert(res, isInterrupted)
          },
          testM("bracketExit use is interruptible") {
            for {
              fiber <- IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => IO.unit)(_ => IO.never).fork
              res   <- fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
            } yield assert(res, equalTo(0))
          },
          testM("bracket release called on interrupt") {
            val io =
              for {
                p1    <- Promise.make[Nothing, Unit]
                p2    <- Promise.make[Nothing, Unit]
                fiber <- IO.bracket(IO.unit)(_ => p2.succeed(()) *> IO.unit)(_ => p1.succeed(()) *> IO.never).fork
                _     <- p1.await
                _     <- fiber.interrupt
                _     <- p2.await
              } yield ()

            assertM(io.timeoutTo(42)(_ => 0)(1.second), equalTo(0))
          },
          testM("bracketExit release called on interrupt") {
            for {
              done <- Promise.make[Nothing, Unit]
              fiber <- withLatch { release =>
                        IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => done.succeed(()))(
                            _ => release *> IO.never
                          )
                          .fork
                      }

              _ <- fiber.interrupt
              r <- done.await.timeoutTo(42)(_ => 0)(60.second)
            } yield assert(r, equalTo(0))
          },
          testM("redeem + ensuring + interrupt") {
            for {
              cont <- Promise.make[Nothing, Unit]
              p1   <- Promise.make[Nothing, Boolean]
              f1   <- (cont.succeed(()) *> IO.never).catchAll(IO.fail).ensuring(p1.succeed(true)).fork
              _    <- cont.await
              _    <- f1.interrupt
              res  <- p1.await
            } yield assert(res, isTrue)
          },
          testM("finalizer can detect interruption") {
            for {
              p1  <- Promise.make[Nothing, Boolean]
              c   <- Promise.make[Nothing, Unit]
              f1  <- (c.succeed(()) *> IO.never).ensuring(IO.descriptor.flatMap(d => p1.succeed(d.interrupted))).fork
              _   <- c.await
              _   <- f1.interrupt
              res <- p1.await
            } yield assert(res, isTrue)
          },
          testM("interruption of raced") {
            for {
              ref   <- Ref.make(0)
              cont1 <- Promise.make[Nothing, Unit]
              cont2 <- Promise.make[Nothing, Unit]
              make  = (p: Promise[Nothing, Unit]) => (p.succeed(()) *> IO.never).onInterrupt(ref.update(_ + 1))
              raced <- (make(cont1) race (make(cont2))).fork
              _     <- cont1.await *> cont2.await
              _     <- raced.interrupt
              count <- ref.get
            } yield assert(count, equalTo(2))
          },
          testM("recovery of error in finalizer") {
            for {
              recovered <- Ref.make(false)
              fiber <- withLatch { release =>
                        (release *> ZIO.never)
                          .ensuring(
                            (ZIO.unit *> ZIO.fail("Uh oh")).catchAll(_ => recovered.set(true))
                          )
                          .fork
                      }
              _     <- fiber.interrupt
              value <- recovered.get
            } yield assert(value, isTrue)
          },
          testM("recovery of interruptible") {
            for {
              recovered <- Ref.make(false)
              fiber <- withLatch { release =>
                        (release *> ZIO.never.interruptible)
                          .foldCauseM(
                            cause => recovered.set(cause.interrupted),
                            _ => recovered.set(false)
                          )
                          .uninterruptible
                          .fork
                      }
              _     <- fiber.interrupt
              value <- recovered.get
            } yield assert(value, isTrue)
          },
          testM("sandbox of interruptible") {
            for {
              recovered <- Ref.make[Option[Either[Cause[Nothing], Any]]](None)
              fiber <- withLatch { release =>
                        (release *> ZIO.never.interruptible).sandbox.either
                          .flatMap(exit => recovered.set(Some(exit)))
                          .uninterruptible
                          .fork
                      }
              _     <- fiber.interrupt
              value <- recovered.get
            } yield assert(value, isSome(isLeft(equalTo(Cause.interrupt))))
          },
          testM("run of interruptible") {
            for {
              recovered <- Ref.make[Option[Exit[Nothing, Any]]](None)
              fiber <- withLatch { release =>
                        (release *> ZIO.never.interruptible).run
                          .flatMap(exit => recovered.set(Some(exit)))
                          .uninterruptible
                          .fork
                      }
              _     <- fiber.interrupt
              value <- recovered.get
            } yield assert(value, isSome(isInterrupted))
          },
          testM("alternating interruptibility") {
            for {
              counter <- Ref.make(0)
              fiber <- withLatch { release =>
                        ((((release *> ZIO.never.interruptible.run *> counter
                          .update(_ + 1)).uninterruptible).interruptible).run
                          *> counter.update(_ + 1)).uninterruptible.fork
                      }
              _     <- fiber.interrupt
              value <- counter.get
            } yield assert(value, equalTo(2))
          },
          testM("interruption after defect") {
            for {
              ref <- Ref.make(false)
              fiber <- withLatch { release =>
                        (ZIO.effect(throw new Error).run *> release *> ZIO.never)
                          .ensuring(ref.set(true))
                          .fork
                      }
              _     <- fiber.interrupt
              value <- ref.get
            } yield assert(value, isTrue)
          },
          testM("interruption after defect 2") {
            for {
              ref <- Ref.make(false)
              fiber <- withLatch { release =>
                        (ZIO.effect(throw new Error).run *> release *> ZIO.unit.forever)
                          .ensuring(ref.set(true))
                          .fork
                      }
              _     <- fiber.interrupt
              value <- ref.get
            } yield assert(value, isTrue)
          },
          testM("cause reflects interruption") {
            val io =
              for {
                finished <- Ref.make(false)
                fiber <- withLatch { release =>
                          (release *> ZIO.fail("foo")).catchAll(_ => finished.set(true)).fork
                        }
                exit     <- fiber.interrupt
                finished <- finished.get
              } yield exit.interrupted == true || finished == true

            assertM(io, isTrue)
          } @@ jvm(nonFlaky(100)),
          testM("bracket use inherits interrupt status") {
            val io =
              for {
                ref <- Ref.make(false)
                fiber1 <- withLatch { (release2, await2) =>
                           withLatch { release1 =>
                             release1
                               .bracket_(ZIO.unit, await2 *> clock.sleep(10.millis) *> ref.set(true))
                               .uninterruptible
                               .fork
                           } <* release2
                         }
                _     <- fiber1.interrupt
                value <- ref.get
              } yield value

            assertM(io.provide(Clock.Live), isTrue)
          },
          testM("bracket use inherits interrupt status 2") {
            val io =
              for {
                latch1 <- Promise.make[Nothing, Unit]
                latch2 <- Promise.make[Nothing, Unit]
                ref    <- Ref.make(false)
                fiber1 <- latch1
                           .succeed(())
                           .bracketExit[Clock, Nothing, Unit](
                             (_: Boolean, _: Exit[Any, Any]) => ZIO.unit,
                             (_: Boolean) => latch2.await *> clock.sleep(10.millis) *> ref.set(true).unit
                           )
                           .uninterruptible
                           .fork
                _     <- latch1.await
                _     <- latch2.succeed(())
                _     <- fiber1.interrupt
                value <- ref.get
              } yield value

            assertM(io.provide(Clock.Live), isTrue)
          },
          testM("async can be uninterruptible") {
            val io =
              for {
                ref <- Ref.make(false)
                fiber <- withLatch { release =>
                          (release *> clock.sleep(10.millis) *> ref.set(true).unit).uninterruptible.fork
                        }
                _     <- fiber.interrupt
                value <- ref.get
              } yield value

            assertM(io.provide(Clock.Live), isTrue)
          }
        ),
        suite("RTS environment")(
          testM("provide is modular") {
            val zio =
              for {
                v1 <- ZIO.environment[Int]
                v2 <- ZIO.environment[Int].provide(2)
                v3 <- ZIO.environment[Int]
              } yield (v1, v2, v3)

            assertM(zio.provide(4), equalTo((4, 2, 4)))
          },
          testM("provideManaged is modular") {
            def managed(v: Int): ZManaged[Any, Nothing, Int] =
              ZManaged.make(IO.succeed(v))(_ => IO.effectTotal(()))

            val zio =
              for {
                v1 <- ZIO.environment[Int]
                v2 <- ZIO.environment[Int].provideManaged(managed(2))
                v3 <- ZIO.environment[Int]
              } yield (v1, v2, v3)

            assertM(zio.provideManaged(managed(4)), equalTo((4, 2, 4)))
          },
          testM("effectAsync can use environment") {
            val zio = ZIO.effectAsync[Int, Nothing, Int](cb => cb(ZIO.environment[Int]))
            assertM(zio.provide(10), equalTo(10))
          }
        ),
        suite("RTS forking inheritability")(
          testM("interruption status is heritable") {
            for {
              latch <- Promise.make[Nothing, Unit]
              ref   <- Ref.make(InterruptStatus.interruptible)
              _     <- ZIO.uninterruptible((ZIO.checkInterruptible(ref.set) *> latch.succeed(())).fork *> latch.await)
              v     <- ref.get
            } yield assert(v, equalTo(InterruptStatus.uninterruptible))
          },
          testM("executor is heritable") {
            val io =
              for {
                ref  <- Ref.make(Option.empty[internal.Executor])
                exec = internal.Executor.fromExecutionContext(100)(scala.concurrent.ExecutionContext.Implicits.global)
                _ <- withLatch(
                      release => IO.descriptor.map(_.executor).flatMap(e => ref.set(Some(e)) *> release).fork.lock(exec)
                    )
                v <- ref.get
              } yield v.contains(exec)

            assertM(io, isTrue)
          } @@ jvm(nonFlaky(100)),
          testM("supervision is heritable") {
            val io =
              for {
                latch <- Promise.make[Nothing, Unit]
                ref   <- Ref.make(SuperviseStatus.unsupervised)
                _     <- ((ZIO.checkSupervised(ref.set) *> latch.succeed(())).fork *> latch.await).supervised
                v     <- ref.get
              } yield v == SuperviseStatus.Supervised

            assertM(io, isTrue)
          } @@ flaky,
          testM("supervision inheritance") {
            def forkAwaitStart[A](io: UIO[A], refs: Ref[List[Fiber[Any, Any]]]): UIO[Fiber[Nothing, A]] =
              withLatch(release => (release *> io).fork.tap(f => refs.update(f :: _)))

            val io =
              (for {
                ref  <- Ref.make[List[Fiber[Any, Any]]](Nil) // To make strong ref
                _    <- forkAwaitStart(forkAwaitStart(forkAwaitStart(IO.succeed(()), ref), ref), ref)
                fibs <- ZIO.children
              } yield fibs.size == 1).supervised

            assertM(io, isTrue)
          } @@ flaky
        ),
        suite("timeoutFork")(
          testM("returns `Right` with the produced value if the effect completes before the timeout elapses") {
            assertM(ZIO.unit.timeoutFork(100.millis), isRight(isUnit))
          },
          testM("returns `Left` with the interrupting fiber otherwise") {
            for {
              fiber  <- ZIO.never.uninterruptible.timeoutFork(100.millis).fork
              _      <- TestClock.adjust(100.millis)
              result <- fiber.join
            } yield assert(result, isLeft(anything))
          }
        ),
        suite("unsandbox")(
          testM("no information is lost during composition") {
            val causes = Gen.causes(Gen.anyString, Gen.throwable)
            def cause[R, E](zio: ZIO[R, E, Nothing]): ZIO[R, Nothing, Cause[E]] =
              zio.foldCauseM(ZIO.succeed, ZIO.fail)
            checkM(causes) { c =>
              for {
                result <- cause(ZIO.halt(c).sandbox.mapErrorCause(e => e.untraced).unsandbox)
              } yield assert(result, equalTo(c)) &&
                assert(result.prettyPrint, equalTo(c.prettyPrint))
            }
          }
        )
      )
    )

object ZIOSpecHelper {
  val ExampleError    = new Throwable("Oh noes!")
  val InterruptCause1 = new Throwable("Oh noes 1!")
  val InterruptCause2 = new Throwable("Oh noes 2!")
  val InterruptCause3 = new Throwable("Oh noes 3!")

  val TaskExampleError: Task[Int] = IO.fail[Throwable](ExampleError)

  def asyncExampleError[A]: Task[A] =
    IO.effectAsync[Throwable, A](_(IO.fail(ExampleError)))

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def deepMapNow(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.succeed(0))
  }

  def deepMapEffect(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.effectTotal(0))
  }

  def deepErrorEffect(n: Int): Task[Unit] =
    if (n == 0) IO.effect(throw ExampleError)
    else IO.unit *> deepErrorEffect(n - 1)

  def deepErrorFail(n: Int): Task[Unit] =
    if (n == 0) IO.fail(ExampleError)
    else IO.unit *> deepErrorFail(n - 1)

  def fib(n: Int): BigInt =
    if (n <= 1) n
    else fib(n - 1) + fib(n - 2)

  def concurrentFib(n: Int): Task[BigInt] =
    if (n <= 1) IO.succeed[BigInt](n)
    else
      for {
        f1 <- concurrentFib(n - 1).fork
        f2 <- concurrentFib(n - 2).fork
        v1 <- f1.join
        v2 <- f2.join
      } yield v1 + v2

  def AsyncUnit[E] = IO.effectAsync[E, Unit](_(IO.unit))
}
