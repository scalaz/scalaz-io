package zio.stream.experimental

import scala.concurrent.ExecutionContext

import ZStreamGen._

import zio._
import zio.duration._
import zio.stream.ChunkUtils._
import zio.test.Assertion._
import zio.test.TestAspect.flaky
import zio.test._
import zio.test.environment.Live

object ZStreamSpec extends ZIOBaseSpec {
  def inParallel(action: => Unit)(implicit ec: ExecutionContext): Unit =
    ec.execute(() => action)

  def spec = suite("ZStreamSpec")(
    suite("Combinators")(
      suite("absolve")(
        testM("happy path")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { xs =>
          val stream = ZStream.fromIterable(xs.map(Right(_)))
          assertM(stream.absolve.runCollect)(equalTo(xs))
        }),
        testM("failure")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { xs =>
          val stream = ZStream.fromIterable(xs.map(Right(_))) ++ ZStream.succeed(Left("Ouch"))
          assertM(stream.absolve.runCollect.run)(fails(equalTo("Ouch")))
        }),
        testM("round-trip #1")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt)), Gen.anyString) { (xs, s) =>
          val xss    = ZStream.fromIterable(xs.map(Right(_)))
          val stream = xss ++ ZStream(Left(s)) ++ xss
          for {
            res1 <- stream.runCollect
            res2 <- stream.absolve.either.runCollect
          } yield assert(res1)(startsWith(res2))
        }),
        testM("round-trip #2")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt)), Gen.anyString) { (xs, s) =>
          val xss    = ZStream.fromIterable(xs)
          val stream = xss ++ ZStream.fail(s)
          for {
            res1 <- stream.runCollect.run
            res2 <- stream.either.absolve.runCollect.run
          } yield assert(res1)(fails(equalTo(s))) && assert(res2)(fails(equalTo(s)))
        })
      ),
      suite("aggregateAsync")(
        // testM("aggregateAsync") {
        //   ZStream(1, 1, 1, 1)
        //     .aggregateAsync(ZTransducer.foldUntil(List[Int](), 3)((acc, el: Int) => el :: acc).map(_.reverse))
        //     .runCollect
        //     .map { result =>
        //       assert(result.flatten)(equalTo(List(1, 1, 1, 1))) &&
        //       assert(result.forall(_.length <= 3))(isTrue)
        //     }
        // },
        testM("error propagation") {
          val e = new RuntimeException("Boom")
          assertM(
            ZStream(1, 1, 1, 1)
              .aggregateAsync(ZTransducer.die(e))
              .runCollect
              .run
          )(dies(equalTo(e)))
        },
        // testM("error propagation") {
        //   val e    = new RuntimeException("Boom")
        //   val sink = ZTransducer.foldM[Nothing, Int, Int, List[Int]](List[Int]())(_ => true)((_, _) => ZIO.die(e))

        //   assertM(
        //     ZStream(1, 1)
        //       .aggregateAsync(sink)
        //       .runCollect
        //       .run
        //   )(dies(equalTo(e)))
        // },
        // testM("interruption propagation") {
        //   for {
        //     latch     <- Promise.make[Nothing, Unit]
        //     cancelled <- Ref.make(false)
        //     sink = ZTransducer.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
        //       if (el == 1) UIO.succeedNow((el :: acc, Chunk[Int]()))
        //       else
        //         (latch.succeed(()) *> ZIO.infinity)
        //           .onInterrupt(cancelled.set(true))
        //     }
        //     fiber  <- ZStream(1, 1, 2).aggregateAsync(sink).runCollect.untraced.fork
        //     _      <- latch.await
        //     _      <- fiber.interrupt
        //     result <- cancelled.get
        //   } yield assert(result)(isTrue)
        // },
        testM("interruption propagation") {
          for {
            latch     <- Promise.make[Nothing, Unit]
            cancelled <- Ref.make(false)
            sink = ZTransducer.fromEffect {
              (latch.succeed(()) *> ZIO.infinity)
                .onInterrupt(cancelled.set(true))
            }
            fiber  <- ZStream(1, 1, 2).aggregateAsync(sink).runCollect.untraced.fork
            _      <- latch.await
            _      <- fiber.interrupt
            result <- cancelled.get
          } yield assert(result)(isTrue)
        }
        // testM("leftover handling") {
        //   val data = List(1, 2, 2, 3, 2, 3)
        //   assertM(
        //     ZStream(data: _*)
        //       .aggregateAsync(
        //         ZTransducer
        //           .foldWeighted(List[Int]())((i: Int) => i.toLong, 4)((acc, el) => el :: acc)
        //           .map(_.reverse)
        //       )
        //       .runCollect
        //       .map(_.flatten)
        //   )(equalTo(data))
        // }
      ),
      suite("aggregate")(
        testM("aggregate") {
          val s      = ZStream('1', '2', ',', '3', '4')
          val parser = ZTransducer.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt)

          assertM(s.aggregate(parser).runCollect)(equalTo(List(12, 34)))
        },
        // testM("no remainder") {
        //   val t = ZTransducer.fold(100)(_ % 2 == 0)((s, a: Int) => (s + a, Chunk[Int]()))
        //   assertM(ZStream(1, 2, 3, 4).aggregate(t).runCollect)(equalTo(List(101, 105, 104)))
        // },
        // testM("with remainder") {
        //   val t = ZTransducer
        //     .fold[Int, Int, (Int, Boolean)]((0, true))(_._2) { (s, a: Int) =>
        //       a match {
        //         case 1 => ((s._1 + 100, true), Chunk.empty)
        //         case 2 => ((s._1 + 100, true), Chunk.empty)
        //         case 3 => ((s._1 + 3, false), Chunk.single(a + 1))
        //         case _ => ((s._1 + 4, false), Chunk.empty)
        //       }
        //     }
        //     .map(_._1)

        //   assertM(ZStream(1, 2, 3).aggregate(t).runCollect)(equalTo(List(203, 4)))
        // },
        // testM("with a sink that always signals more") {
        //   val t = Sink.foldLeft(0)((s, a: Int) => s + a)
        //   assertM(ZStream(1, 2, 3).aggregate(t).runCollect)(equalTo(List(1 + 2 + 3)))
        // },
        // testM("managed") {
        //   final class TestSink(ref: Ref[Int]) extends ZSink[Any, Throwable, Int, Int, List[Int]] {
        //     type State = (List[Int], Boolean)

        //     def extract(state: State) = UIO.succeedNow((state._1, Chunk.empty))

        //     def initial = UIO.succeedNow((Nil, true))

        //     def step(state: State, a: Int) =
        //       for {
        //         i <- ref.get
        //         _ <- if (i != 1000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
        //       } yield (List(a, a), false)

        //     def cont(state: State) = state._2
        //   }

        //   val stream = ZStream(1, 2, 3, 4)

        //   for {
        //     resource <- Ref.make(0)
        //     sink     = ZManaged.make(resource.set(1000).as(new TestSink(resource)))(_ => resource.set(2000))
        //     result   <- stream.aggregateManaged(sink).runCollect
        //     i        <- resource.get
        //     _        <- if (i != 2000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
        //   } yield assert(result)(equalTo(List(List(1, 1), List(2, 2), List(3, 3), List(4, 4))))
        // },
        testM("propagate managed error") {
          val fail = "I'm such a failure!"
          val t    = ZTransducer.fail(fail)
          assertM(ZStream(1, 2, 3).aggregate(t).runCollect.either)(isLeft(equalTo(fail)))
        }
      ),
      suite("bracket")(
        testM("bracket")(
          for {
            done           <- Ref.make(false)
            iteratorStream = ZStream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(ZStream.fromIterable(_))
            result         <- iteratorStream.runCollect
            released       <- done.get
          } yield assert(result)(equalTo(List(0, 1, 2))) && assert(released)(isTrue)
        ),
        testM("bracket short circuits")(
          for {
            done <- Ref.make(false)
            iteratorStream = ZStream
              .bracket(UIO(0 to 3))(_ => done.set(true))
              .flatMap(ZStream.fromIterable(_))
              .take(2)
            result   <- iteratorStream.runCollect
            released <- done.get
          } yield assert(result)(equalTo(List(0, 1))) && assert(released)(isTrue)
        ),
        testM("no acquisition when short circuiting")(
          for {
            acquired       <- Ref.make(false)
            iteratorStream = (ZStream(1) ++ ZStream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
            _              <- iteratorStream.runDrain
            result         <- acquired.get
          } yield assert(result)(isFalse)
        ),
        testM("releases when there are defects") {
          for {
            ref <- Ref.make(false)
            _ <- ZStream
                  .bracket(ZIO.unit)(_ => ref.set(true))
                  .flatMap(_ => ZStream.fromEffect(ZIO.dieMessage("boom")))
                  .runDrain
                  .run
            released <- ref.get
          } yield assert(released)(isTrue)
        },
        testM("flatMap associativity doesn't affect bracket lifetime")(
          for {
            leftAssoc <- ZStream
                          .bracket(Ref.make(true))(_.set(false))
                          .flatMap(ZStream.succeed(_))
                          .flatMap(r => ZStream.fromEffect(r.get))
                          .runCollect
                          .map(_.head)
            rightAssoc <- ZStream
                           .bracket(Ref.make(true))(_.set(false))
                           .flatMap(ZStream.succeed(_).flatMap(r => ZStream.fromEffect(r.get)))
                           .runCollect
                           .map(_.head)
          } yield assert(leftAssoc -> rightAssoc)(equalTo(true -> true))
        )
      ),
      suite("broadcast")(
        testM("Values") {
          ZStream
            .range(0, 5)
            .broadcast(2, 12)
            .use {
              case s1 :: s2 :: Nil =>
                for {
                  out1     <- s1.runCollect
                  out2     <- s2.runCollect
                  expected = Range(0, 5).toList
                } yield assert(out1)(equalTo(expected)) && assert(out2)(equalTo(expected))
              case _ =>
                UIO(assert(())(Assertion.nothing))
            }
        },
        testM("Errors") {
          (ZStream.range(0, 1) ++ ZStream.fail("Boom")).broadcast(2, 12).use {
            case s1 :: s2 :: Nil =>
              for {
                out1     <- s1.runCollect.either
                out2     <- s2.runCollect.either
                expected = Left("Boom")
              } yield assert(out1)(equalTo(expected)) && assert(out2)(equalTo(expected))
            case _ =>
              UIO(assert(())(Assertion.nothing))
          }
        },
        testM("BackPressure") {
          ZStream
            .range(0, 5)
            .broadcast(2, 2)
            .use {
              case s1 :: s2 :: Nil =>
                for {
                  ref       <- Ref.make[List[Int]](Nil)
                  latch1    <- Promise.make[Nothing, Unit]
                  fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
                  _         <- latch1.await
                  snapshot1 <- ref.get
                  _         <- s2.runDrain
                  _         <- fib.await
                  snapshot2 <- ref.get
                } yield assert(snapshot1)(equalTo(List(2, 1, 0))) && assert(snapshot2)(
                  equalTo(Range(0, 5).toList.reverse)
                )
              case _ =>
                UIO(assert(())(Assertion.nothing))
            }
        },
        testM("Unsubscribe") {
          ZStream.range(0, 5).broadcast(2, 2).use {
            case s1 :: s2 :: Nil =>
              for {
                _    <- s1.process.use_(ZIO.unit).ignore
                out2 <- s2.runCollect
              } yield assert(out2)(equalTo(Range(0, 5).toList))
            case _ =>
              UIO(assert(())(Assertion.nothing))
          }
        }
      ),
      suite("buffer")(
        testM("maintains elements and ordering")(checkM(Gen.listOf(smallChunks(Gen.anyInt))) { list =>
          assertM(
            ZStream
              .fromChunks(list: _*)
              .buffer(2)
              .runCollect
          )(equalTo(Chunk.fromIterable(list).flatten.toList))
        }),
        testM("buffer the Stream with Error") {
          val e = new RuntimeException("boom")
          assertM(
            (ZStream.range(0, 10) ++ ZStream.fail(e))
              .buffer(2)
              .runCollect
              .run
          )(fails(equalTo(e)))
        },
        testM("fast producer progress independently") {
          for {
            ref   <- Ref.make(List[Int]())
            latch <- Promise.make[Nothing, Unit]
            s     = ZStream.range(1, 5).tap(i => ref.update(i :: _) *> latch.succeed(()).when(i == 4)).buffer(2)
            l <- s.process.use { as =>
                  for {
                    _ <- as
                    _ <- latch.await
                    l <- ref.get
                  } yield l
                }
          } yield assert(l.reverse)(equalTo((1 to 4).toList))
        }
      ),
      suite("bufferDropping")(
        testM("buffer the Stream with Error") {
          val e = new RuntimeException("boom")
          assertM(
            (ZStream.range(1, 1000) ++ ZStream.fail(e) ++ ZStream.range(1001, 2000))
              .bufferDropping(2)
              .runCollect
              .run
          )(fails(equalTo(e)))
        },
        testM("fast producer progress independently") {
          for {
            ref    <- Ref.make(List.empty[Int])
            latch1 <- Promise.make[Nothing, Unit]
            latch2 <- Promise.make[Nothing, Unit]
            latch3 <- Promise.make[Nothing, Unit]
            latch4 <- Promise.make[Nothing, Unit]
            s1 = ZStream(0) ++ ZStream
              .fromEffect(latch1.await)
              .flatMap(_ => ZStream.range(1, 17).ensuring(latch2.succeed(())))
            s2 = ZStream
              .fromEffect(latch3.await)
              .flatMap(_ => ZStream.range(17, 25).ensuring(latch4.succeed(())))
            s = (s1 ++ s2).bufferDropping(8)
            snapshots <- s.process.use { as =>
                          for {
                            zero      <- as
                            _         <- latch1.succeed(())
                            _         <- latch2.await
                            _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                            snapshot1 <- ref.get
                            _         <- latch3.succeed(())
                            _         <- latch4.await
                            _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                            snapshot2 <- ref.get
                          } yield (zero, snapshot1, snapshot2)
                        }
          } yield assert(snapshots._1)(equalTo(Chunk.single(0))) && assert(snapshots._2)(
            equalTo(List(8, 7, 6, 5, 4, 3, 2, 1))
          ) &&
            assert(snapshots._3)(equalTo(List(24, 23, 22, 21, 20, 19, 18, 17, 8, 7, 6, 5, 4, 3, 2, 1)))
        }
      ),
      suite("bufferSliding")(
        testM("buffer the Stream with Error") {
          val e = new RuntimeException("boom")
          assertM(
            (ZStream.range(1, 1000) ++ ZStream.fail(e) ++ ZStream.range(1001, 2000))
              .bufferSliding(2)
              .runCollect
              .run
          )(fails(equalTo(e)))
        },
        testM("fast producer progress independently") {
          for {
            ref    <- Ref.make(List.empty[Int])
            latch1 <- Promise.make[Nothing, Unit]
            latch2 <- Promise.make[Nothing, Unit]
            latch3 <- Promise.make[Nothing, Unit]
            latch4 <- Promise.make[Nothing, Unit]
            s1 = ZStream(0) ++ ZStream
              .fromEffect(latch1.await)
              .flatMap(_ => ZStream.range(1, 17).ensuring(latch2.succeed(())))
            s2 = ZStream
              .fromEffect(latch3.await)
              .flatMap(_ => ZStream.range(17, 25).ensuring(latch4.succeed(())))
            s = (s1 ++ s2).bufferSliding(8)
            snapshots <- s.process.use { as =>
                          for {
                            zero      <- as
                            _         <- latch1.succeed(())
                            _         <- latch2.await
                            _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                            snapshot1 <- ref.get
                            _         <- latch3.succeed(())
                            _         <- latch4.await
                            _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                            snapshot2 <- ref.get
                          } yield (zero, snapshot1, snapshot2)
                        }
          } yield assert(snapshots._1)(equalTo(Chunk.single(0))) && assert(snapshots._2)(
            equalTo(List(16, 15, 14, 13, 12, 11, 10, 9))
          ) &&
            assert(snapshots._3)(equalTo(List(24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9)))
        }
      ),
      suite("bufferUnbounded")(
        testM("buffer the Stream")(checkM(Gen.listOf(Gen.anyInt)) { list =>
          assertM(
            ZStream
              .fromIterable(list)
              .bufferUnbounded
              .runCollect
          )(equalTo(list))
        }),
        testM("buffer the Stream with Error") {
          val e = new RuntimeException("boom")
          assertM((ZStream.range(0, 10) ++ ZStream.fail(e)).bufferUnbounded.runCollect.run)(fails(equalTo(e)))
        },
        testM("fast producer progress independently") {
          for {
            ref   <- Ref.make(List[Int]())
            latch <- Promise.make[Nothing, Unit]
            s = ZStream
              .fromEffect(UIO.succeedNow(()))
              .flatMap(_ => ZStream.range(1, 1000).tap(i => ref.update(i :: _)).ensuring(latch.succeed(())))
              .bufferUnbounded
            l <- s.process.use { as =>
                  for {
                    _ <- as
                    _ <- latch.await
                    l <- ref.get
                  } yield l
                }
          } yield assert(l.reverse)(equalTo(Range(1, 1000).toList))
        }
      ),
      suite("catchAllCause")(
        testM("recovery from errors") {
          val s1 = ZStream(1, 2) ++ ZStream.fail("Boom")
          val s2 = ZStream(3, 4)
          s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
        },
        testM("recovery from defects") {
          val s1 = ZStream(1, 2) ++ ZStream.dieMessage("Boom")
          val s2 = ZStream(3, 4)
          s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
        },
        testM("happy path") {
          val s1 = ZStream(1, 2)
          val s2 = ZStream(3, 4)
          s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2))))
        },
        testM("executes finalizers") {
          for {
            fins   <- Ref.make(List[String]())
            s1     = (ZStream(1, 2) ++ ZStream.fail("Boom")).ensuring(fins.update("s1" :: _))
            s2     = (ZStream(3, 4) ++ ZStream.fail("Boom")).ensuring(fins.update("s2" :: _))
            _      <- s1.catchAllCause(_ => s2).runCollect.run
            result <- fins.get
          } yield assert(result)(equalTo(List("s2", "s1")))
        }
      ),
      testM("Stream.collect") {
        assertM(ZStream(Left(1), Right(2), Left(3)).collect {
          case Right(n) => n
        }.runCollect)(equalTo(List(2)))
      },
      suite("Stream.collectM")(
        testM("collectM") {
          assertM(
            ZStream(Left(1), Right(2), Left(3)).collectM {
              case Right(n) => ZIO(n * 2)
            }.runCollect
          )(equalTo(List(4)))
        },
        testM("collectM fails") {
          assertM(
            ZStream(Left(1), Right(2), Left(3)).collectM {
              case Right(_) => ZIO.fail("Ouch")
            }.runDrain.either
          )(isLeft(isNonEmptyString))
        }
      ),
      suite("Stream.collectWhile")(
        testM("collectWhile") {
          assertM(ZStream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile {
            case Some(v) => v
          }.runCollect)(equalTo(List(1, 2, 3)))
        },
        testM("collectWhile short circuits") {
          assertM((ZStream(Option(1)) ++ ZStream.fail("Ouch")).collectWhile {
            case None => 1
          }.runDrain.either)(isRight(isUnit))
        }
      ),
      suite("Stream.collectWhileM")(
        testM("collectWhileM") {
          assertM(
            ZStream(Some(1), Some(2), Some(3), None, Some(4)).collectWhileM {
              case Some(v) => ZIO(v * 2)
            }.runCollect
          )(equalTo(List(2, 4, 6)))
        },
        testM("collectWhileM short circuits") {
          assertM(
            (ZStream(Option(1)) ++ ZStream.fail("Ouch"))
              .collectWhileM[Any, String, Int] {
                case None => ZIO.succeedNow(1)
              }
              .runDrain
              .either
          )(isRight(isUnit))
        },
        testM("collectWhileM fails") {
          assertM(
            ZStream(Some(1), Some(2), Some(3), None, Some(4)).collectWhileM {
              case Some(_) => ZIO.fail("Ouch")
            }.runDrain.either
          )(isLeft(isNonEmptyString))
        }
      ),
      suite("distributedWithDynamic")(
        testM("ensures no race between subscription and stream end") {
          ZStream.empty.distributedWithDynamic(1, _ => UIO.succeedNow(_ => true)).use { add =>
            val subscribe = ZStream.unwrap(add.map {
              case (_, queue) =>
                ZStream.fromQueue(queue).unExit
            })
            Promise.make[Nothing, Unit].flatMap { onEnd =>
              subscribe.ensuring(onEnd.succeed(())).runDrain.fork *>
                onEnd.await *>
                subscribe.runDrain *>
                ZIO.succeedNow(assertCompletes)
            }
          }
        }
      ),
      suite("flatMap")(
        testM("deep flatMap stack safety") {
          def fib(n: Int): ZStream[Any, Nothing, Int] =
            if (n <= 1) ZStream.succeed(n)
            else
              fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => ZStream.succeed(a + b)))

          val stream   = fib(20)
          val expected = 6765

          assertM(stream.runCollect)(equalTo(List(expected)))
        },
        testM("left identity")(checkM(Gen.anyInt, Gen.function(pureStreamOfInts)) { (x, f) =>
          for {
            res1 <- ZStream(x).flatMap(f).runCollect
            res2 <- f(x).runCollect
          } yield assert(res1)(equalTo(res2))
        }),
        testM("right identity")(
          checkM(pureStreamOfInts)(m =>
            for {
              res1 <- m.flatMap(i => ZStream(i)).runCollect
              res2 <- m.runCollect
            } yield assert(res1)(equalTo(res2))
          )
        ),
        testM("associativity") {
          val tinyStream = Gen.int(0, 2).flatMap(pureStreamGen(Gen.anyInt, _))
          val fnGen      = Gen.function(tinyStream)
          checkM(tinyStream, fnGen, fnGen) { (m, f, g) =>
            for {
              leftStream  <- m.flatMap(f).flatMap(g).runCollect
              rightStream <- m.flatMap(x => f(x).flatMap(g)).runCollect
            } yield assert(leftStream)(equalTo(rightStream))
          }
        },
        testM("inner finalizers") {
          for {
            effects <- Ref.make(List[Int]())
            push    = (i: Int) => effects.update(i :: _)
            latch   <- Promise.make[Nothing, Unit]
            fiber <- ZStream(
                      ZStream.bracket(push(1))(_ => push(1)),
                      ZStream.fromEffect(push(2)),
                      ZStream.bracket(push(3))(_ => push(3)) *> ZStream.fromEffect(
                        latch.succeed(()) *> ZIO.never
                      )
                    ).flatMap(identity).runDrain.fork
            _      <- latch.await
            _      <- fiber.interrupt
            result <- effects.get
          } yield assert(result)(equalTo(List(3, 3, 2, 1, 1)))

        },
        testM("finalizer ordering") {
          for {
            effects <- Ref.make(List[String]())
            push    = (i: String) => effects.update(i :: _)
            stream = for {
              _ <- ZStream.bracket(push("open1"))(_ => push("close1"))
              _ <- ZStream.fromChunks(Chunk(()), Chunk(())).tap(_ => push("use2")).ensuring(push("close2"))
              _ <- ZStream.bracket(push("open3"))(_ => push("close3"))
              _ <- ZStream.fromChunks(Chunk(()), Chunk(())).tap(_ => push("use4")).ensuring(push("close4"))
            } yield ()
            _      <- stream.runDrain
            result <- effects.get
          } yield assert(result.reverse)(
            equalTo(
              List(
                "open1",
                "use2",
                "open3",
                "use4",
                "use4",
                "close4",
                "close3",
                "use2",
                "open3",
                "use4",
                "use4",
                "close4",
                "close3",
                "close2",
                "close1"
              )
            )
          )
        },
        testM("exit signal") {
          for {
            ref <- Ref.make(false)
            inner = ZStream
              .bracketExit(UIO.unit)((_, e) =>
                e match {
                  case Exit.Failure(_) => ref.set(true)
                  case Exit.Success(_) => UIO.unit
                }
              )
              .flatMap(_ => ZStream.fail("Ouch"))
            _   <- ZStream.succeed(()).flatMap(_ => inner).runDrain.either.unit
            fin <- ref.get
          } yield assert(fin)(isTrue)
        }
      ),
      suite("flatMapPar")(
        testM("guarantee ordering")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { (m: List[Int]) =>
          for {
            flatMap    <- ZStream.fromIterable(m).flatMap(i => ZStream(i, i)).runCollect
            flatMapPar <- ZStream.fromIterable(m).flatMapPar(1)(i => ZStream(i, i)).runCollect
          } yield assert(flatMap)(equalTo(flatMapPar))
        }),
        testM("consistent with flatMap")(checkM(Gen.int(1, Int.MaxValue), Gen.small(Gen.listOfN(_)(Gen.anyInt))) {
          (n, m) =>
            for {
              flatMap    <- ZStream.fromIterable(m).flatMap(i => ZStream(i, i)).runCollect.map(_.toSet)
              flatMapPar <- ZStream.fromIterable(m).flatMapPar(n)(i => ZStream(i, i)).runCollect.map(_.toSet)
            } yield assert(n)(isGreaterThan(0)) implies assert(flatMap)(equalTo(flatMapPar))
        }),
        testM("short circuiting") {
          assertM(
            ZStream
              .mergeAll(2)(
                ZStream.never,
                ZStream(1)
              )
              .take(1)
              .runCollect
          )(equalTo(List(1)))
        },
        testM("interruption propagation") {
          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            fiber <- ZStream(())
                      .flatMapPar(1)(_ =>
                        ZStream.fromEffect(
                          (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                        )
                      )
                      .runDrain
                      .fork
            _         <- latch.await
            _         <- fiber.interrupt
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue)
        },
        testM("inner errors interrupt all fibers") {
          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- ZStream(
                       ZStream.fromEffect(
                         (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                       ),
                       ZStream.fromEffect(latch.await *> ZIO.fail("Ouch"))
                     ).flatMapPar(2)(identity).runDrain.either
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
        },
        testM("outer errors interrupt all fibers") {
          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> ZIO.fail("Ouch")))
                       .flatMapPar(2) { _ =>
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         )
                       }
                       .runDrain
                       .either
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
        },
        testM("inner defects interrupt all fibers") {
          val ex = new RuntimeException("Ouch")

          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- ZStream(
                       ZStream.fromEffect(
                         (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                       ),
                       ZStream.fromEffect(latch.await *> ZIO.die(ex))
                     ).flatMapPar(2)(identity).runDrain.run
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
        },
        testM("outer defects interrupt all fibers") {
          val ex = new RuntimeException()

          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> ZIO.die(ex)))
                       .flatMapPar(2) { _ =>
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         )
                       }
                       .runDrain
                       .run
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
        },
        testM("finalizer ordering") {
          for {
            execution <- Ref.make[List[String]](Nil)
            inner = ZStream
              .bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
            _ <- ZStream
                  .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ => execution.update("OuterRelease" :: _))
                  .flatMapPar(2)(identity)
                  .runDrain
            results <- execution.get
          } yield assert(results)(equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
        }
      ),
      suite("flatMapParSwitch")(
        testM("guarantee ordering no parallelism") {
          for {
            lastExecuted <- Ref.make(false)
            semaphore    <- Semaphore.make(1)
            _ <- ZStream(1, 2, 3, 4)
                  .flatMapParSwitch(1) { i =>
                    if (i > 3) ZStream.bracket(UIO.unit)(_ => lastExecuted.set(true)).flatMap(_ => ZStream.empty)
                    else ZStream.managed(semaphore.withPermitManaged).flatMap(_ => ZStream.never)
                  }
                  .runDrain
            result <- semaphore.withPermit(lastExecuted.get)
          } yield assert(result)(isTrue)
        },
        testM("guarantee ordering with parallelism") {
          for {
            lastExecuted <- Ref.make(0)
            semaphore    <- Semaphore.make(4)
            _ <- ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                  .flatMapParSwitch(4) { i =>
                    if (i > 8)
                      ZStream.bracket(UIO.unit)(_ => lastExecuted.update(_ + 1)).flatMap(_ => ZStream.empty)
                    else ZStream.managed(semaphore.withPermitManaged).flatMap(_ => ZStream.never)
                  }
                  .runDrain
            result <- semaphore.withPermits(4)(lastExecuted.get)
          } yield assert(result)(equalTo(4))
        },
        testM("short circuiting") {
          assertM(
            ZStream(ZStream.never, ZStream(1))
              .flatMapParSwitch(2)(identity)
              .take(1)
              .runCollect
          )(equalTo(List(1)))
        },
        testM("interruption propagation") {
          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            fiber <- ZStream(())
                      .flatMapParSwitch(1)(_ =>
                        ZStream.fromEffect(
                          (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                        )
                      )
                      .runCollect
                      .fork
            _         <- latch.await
            _         <- fiber.interrupt
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue)
        } @@ flaky,
        testM("inner errors interrupt all fibers") {
          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- ZStream(
                       ZStream.fromEffect(
                         (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                       ),
                       ZStream.fromEffect(latch.await *> IO.fail("Ouch"))
                     ).flatMapParSwitch(2)(identity).runDrain.either
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
        } @@ flaky,
        testM("outer errors interrupt all fibers") {
          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> IO.fail("Ouch")))
                       .flatMapParSwitch(2) { _ =>
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         )
                       }
                       .runDrain
                       .either
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
        },
        testM("inner defects interrupt all fibers") {
          val ex = new RuntimeException("Ouch")

          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- ZStream(
                       ZStream.fromEffect(
                         (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                       ),
                       ZStream.fromEffect(latch.await *> ZIO.die(ex))
                     ).flatMapParSwitch(2)(identity).runDrain.run
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
        },
        testM("outer defects interrupt all fibers") {
          val ex = new RuntimeException()

          for {
            substreamCancelled <- Ref.make[Boolean](false)
            latch              <- Promise.make[Nothing, Unit]
            result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> ZIO.die(ex)))
                       .flatMapParSwitch(2) { _ =>
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         )
                       }
                       .runDrain
                       .run
            cancelled <- substreamCancelled.get
          } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
        },
        testM("finalizer ordering") {
          for {
            execution <- Ref.make(List.empty[String])
            inner     = ZStream.bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
            _ <- ZStream
                  .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ => execution.update("OuterRelease" :: _))
                  .flatMapParSwitch(2)(identity)
                  .runDrain
            results <- execution.get
          } yield assert(results)(equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
        }
      ),
      suite("foreach")(
        testM("foreach") {
          for {
            ref <- Ref.make(0)
            _   <- ZStream(1, 1, 1, 1, 1).foreach[Any, Nothing](a => ref.update(_ + a))
            sum <- ref.get
          } yield assert(sum)(equalTo(5))
        },
        testM("foreachWhile") {
          for {
            ref <- Ref.make(0)
            _ <- ZStream(1, 1, 1, 1, 1, 1).foreachWhile[Any, Nothing](a =>
                  ref.modify(sum =>
                    if (sum >= 3) (false, sum)
                    else (true, sum + a)
                  )
                )
            sum <- ref.get
          } yield assert(sum)(equalTo(3))
        },
        testM("foreachWhile short circuits") {
          for {
            flag    <- Ref.make(true)
            _       <- (ZStream(true, true, false) ++ ZStream.fromEffect(flag.set(false)).drain).foreachWhile(ZIO.succeedNow)
            skipped <- flag.get
          } yield assert(skipped)(isTrue)
        }
      ),
      testM("forever") {
        for {
          ref <- Ref.make(0)
          _ <- ZStream(1).forever.foreachWhile[Any, Nothing](_ =>
                ref.modify(sum => (if (sum >= 9) false else true, sum + 1))
              )
          sum <- ref.get
        } yield assert(sum)(equalTo(10))
      },
      suite("groupBy")(
        testM("values") {
          val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
          assertM(
            ZStream
              .fromIterable(words)
              .groupByKey(identity, 8192) {
                case (k, s) =>
                  ZStream.fromEffect(s.runCollect.map(l => k -> l.size))
              }
              .runCollect
              .map(_.toMap)
          )(equalTo((0 to 100).map((_.toString -> 1000)).toMap))
        },
        testM("first") {
          val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
          assertM(
            ZStream
              .fromIterable(words)
              .groupByKey(identity, 1050)
              .first(2) {
                case (k, s) =>
                  ZStream.fromEffect(s.runCollect.map(l => k -> l.size))
              }
              .runCollect
              .map(_.toMap)
          )(equalTo((0 to 1).map((_.toString -> 1000)).toMap))
        },
        testM("filter") {
          val words = List.fill(1000)(0 to 100).flatten
          assertM(
            ZStream
              .fromIterable(words)
              .groupByKey(identity, 1050)
              .filter(_ <= 5) {
                case (k, s) =>
                  ZStream.fromEffect(s.runCollect.map(l => k -> l.size))
              }
              .runCollect
              .map(_.toMap)
          )(equalTo((0 to 5).map((_ -> 1000)).toMap))
        },
        testM("outer errors") {
          val words = List("abc", "test", "test", "foo")
          assertM(
            (ZStream.fromIterable(words) ++ ZStream.fail("Boom"))
              .groupByKey(identity) { case (_, s) => s.drain }
              .runCollect
              .either
          )(isLeft(equalTo("Boom")))
        }
      ),
      testM("interleave") {
        val s1 = ZStream(2, 3)
        val s2 = ZStream(5, 6, 7)

        assertM(s1.interleave(s2).runCollect)(equalTo(List(2, 5, 3, 6, 7)))
      },
      testM("interleaveWith") {
        def interleave(b: List[Boolean], s1: => List[Int], s2: => List[Int]): List[Int] =
          b.headOption.map { hd =>
            if (hd) s1 match {
              case h :: t =>
                h :: interleave(b.tail, t, s2)
              case _ =>
                if (s2.isEmpty) List.empty
                else interleave(b.tail, List.empty, s2)
            }
            else
              s2 match {
                case h :: t =>
                  h :: interleave(b.tail, s1, t)
                case _ =>
                  if (s1.isEmpty) List.empty
                  else interleave(b.tail, s1, List.empty)
              }
          }.getOrElse(List.empty)

        val int = Gen.int(0, 5)

        checkM(
          int.flatMap(pureStreamGen(Gen.boolean, _)),
          int.flatMap(pureStreamGen(Gen.anyInt, _)),
          int.flatMap(pureStreamGen(Gen.anyInt, _))
        ) { (b, s1, s2) =>
          for {
            interleavedStream <- s1.interleaveWith(s2)(b).runCollect
            b                 <- b.runCollect
            s1                <- s1.runCollect
            s2                <- s2.runCollect
            interleavedLists  = interleave(b, s1, s2)
          } yield assert(interleavedStream)(equalTo(interleavedLists))
        }
      },
      testM("mapConcat")(checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
        for {
          res1 <- s.mapConcat(f).runCollect
          res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
        } yield assert(res1)(equalTo(res2))
      }),
      testM("mapConcatChunk")(checkM(pureStreamOfBytes, Gen.function(smallChunks(Gen.anyInt))) { (s, f) =>
        for {
          res1 <- s.mapConcatChunk(f).runCollect
          res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
        } yield assert(res1)(equalTo(res2))
      }),
      suite("mapConcatChunkM")(
        testM("mapConcatChunkM happy path") {
          checkM(pureStreamOfBytes, Gen.function(smallChunks(Gen.anyInt))) { (s, f) =>
            for {
              res1 <- s.mapConcatChunkM(b => UIO.succeedNow(f(b))).runCollect
              res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
            } yield assert(res1)(equalTo(res2))
          }
        },
        testM("mapConcatChunkM error") {
          ZStream(1, 2, 3)
            .mapConcatChunkM(_ => IO.fail("Ouch"))
            .runCollect
            .either
            .map(assert(_)(equalTo(Left("Ouch"))))
        }
      ),
      suite("mapConcatM")(
        testM("mapConcatM happy path") {
          checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
            for {
              res1 <- s.mapConcatM(b => UIO.succeedNow(f(b))).runCollect
              res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
            } yield assert(res1)(equalTo(res2))
          }
        },
        testM("mapConcatM error") {
          ZStream(1, 2, 3)
            .mapConcatM(_ => IO.fail("Ouch"))
            .runCollect
            .either
            .map(assert(_)(equalTo(Left("Ouch"))))
        }
      ),
      testM("mapError") {
        ZStream
          .fail("123")
          .mapError(_.toInt)
          .runCollect
          .either
          .map(assert(_)(isLeft(equalTo(123))))
      },
      testM("mapErrorCause") {
        ZStream
          .halt(Cause.fail("123"))
          .mapErrorCause(_.map(_.toInt))
          .runCollect
          .either
          .map(assert(_)(isLeft(equalTo(123))))
      },
      testM("mapM") {
        checkM(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(Gen.successes(Gen.anyByte))) { (data, f) =>
          val s = ZStream.fromIterable(data)

          for {
            l <- s.mapM(f).runCollect
            r <- IO.foreach(data)(f)
          } yield assert(l)(equalTo(r))
        }
      },
      suite("Stream.mapMPar")(
        testM("foreachParN equivalence") {
          checkM(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(Gen.successes(Gen.anyByte))) { (data, f) =>
            val s = ZStream.fromIterable(data)

            for {
              l <- s.mapMPar(8)(f).runCollect
              r <- IO.foreachParN(8)(data)(f)
            } yield assert(l)(equalTo(r))
          }
        },
        testM("order when n = 1") {
          for {
            queue  <- Queue.unbounded[Int]
            _      <- ZStream.range(0, 9).mapMPar(1)(queue.offer).runDrain
            result <- queue.takeAll
          } yield assert(result)(equalTo(result.sorted))
        },
        testM("interruption propagation") {
          for {
            interrupted <- Ref.make(false)
            latch       <- Promise.make[Nothing, Unit]
            fib <- ZStream(())
                    .mapMPar(1)(_ => (latch.succeed(()) *> ZIO.infinity).onInterrupt(interrupted.set(true)))
                    .runDrain
                    .fork
            _      <- latch.await
            _      <- fib.interrupt
            result <- interrupted.get
          } yield assert(result)(isTrue)
        },
        testM("guarantee ordering")(checkM(Gen.int(1, 4096), Gen.listOf(Gen.anyInt)) { (n: Int, m: List[Int]) =>
          for {
            mapM    <- ZStream.fromIterable(m).mapM(UIO.succeedNow).runCollect
            mapMPar <- ZStream.fromIterable(m).mapMPar(n)(UIO.succeedNow).runCollect
          } yield assert(n)(isGreaterThan(0)) implies assert(mapM)(equalTo(mapMPar))
        })
      ),
      suite("mergeWith")(
        testM("equivalence with set union")(checkM(streamOfInts, streamOfInts) {
          (s1: ZStream[Any, String, Int], s2: ZStream[Any, String, Int]) =>
            for {
              mergedStream <- (s1 merge s2).runCollect.map(_.toSet).run
              mergedLists <- s1.runCollect
                              .zipWith(s2.runCollect)((left, right) => left ++ right)
                              .map(_.toSet)
                              .run
            } yield assert(!mergedStream.succeeded && !mergedLists.succeeded)(isTrue) || assert(mergedStream)(
              equalTo(mergedLists)
            )
        }),
        testM("prioritizes failure") {
          val s1 = ZStream.never
          val s2 = ZStream.fail("Ouch")

          assertM(s1.mergeWith(s2)(_ => (), _ => ()).runCollect.either)(isLeft(equalTo("Ouch")))
        }
      ),
      suite("partitionEither")(
        testM("allows repeated runs without hanging") {
          val stream = ZStream
            .fromIterable[Int](Seq.empty)
            .partitionEither(i => ZIO.succeedNow(if (i % 2 == 0) Left(i) else Right(i)))
            .map { case (evens, odds) => evens.mergeEither(odds) }
            .use(_.runCollect)
          assertM(ZIO.collectAll(Range(0, 100).toList.map(_ => stream)).map(_ => 0))(equalTo(0))
        },
        testM("values") {
          ZStream
            .range(0, 6)
            .partitionEither { i =>
              if (i % 2 == 0) ZIO.succeedNow(Left(i))
              else ZIO.succeedNow(Right(i))
            }
            .use {
              case (s1, s2) =>
                for {
                  out1 <- s1.runCollect
                  out2 <- s2.runCollect
                } yield assert(out1)(equalTo(List(0, 2, 4))) && assert(out2)(equalTo(List(1, 3, 5)))
            }
        },
        testM("errors") {
          (ZStream.range(0, 1) ++ ZStream.fail("Boom")).partitionEither { i =>
            if (i % 2 == 0) ZIO.succeedNow(Left(i))
            else ZIO.succeedNow(Right(i))
          }.use {
            case (s1, s2) =>
              for {
                out1 <- s1.runCollect.either
                out2 <- s2.runCollect.either
              } yield assert(out1)(isLeft(equalTo("Boom"))) && assert(out2)(isLeft(equalTo("Boom")))
          }
        },
        testM("backpressure") {
          ZStream
            .range(0, 6)
            .partitionEither(
              i =>
                if (i % 2 == 0) ZIO.succeedNow(Left(i))
                else ZIO.succeedNow(Right(i)),
              1
            )
            .use {
              case (s1, s2) =>
                for {
                  ref       <- Ref.make[List[Int]](Nil)
                  latch1    <- Promise.make[Nothing, Unit]
                  fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
                  _         <- latch1.await
                  snapshot1 <- ref.get
                  other     <- s2.runCollect
                  _         <- fib.await
                  snapshot2 <- ref.get
                } yield assert(snapshot1)(equalTo(List(2, 0))) && assert(snapshot2)(equalTo(List(4, 2, 0))) && assert(
                  other
                )(
                  equalTo(
                    List(
                      1,
                      3,
                      5
                    )
                  )
                )
            }
        }
      ),
      testM("orElse") {
        val s1 = ZStream(1, 2, 3) ++ ZStream.fail("Boom")
        val s2 = ZStream(4, 5, 6)
        s1.orElse(s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4, 5, 6))))
      },
      suite("repeat")(
        testM("repeat")(
          assertM(
            ZStream(1)
              .repeat(Schedule.recurs(4))
              .runCollect
          )(equalTo(List(1, 1, 1, 1, 1)))
        ),
        testM("short circuits")(
          Live.live(for {
            ref <- Ref.make[List[Int]](Nil)
            _ <- ZStream
                  .fromEffect(ref.update(1 :: _))
                  .repeat(Schedule.spaced(10.millis))
                  .take(2)
                  .runDrain
            result <- ref.get
          } yield assert(result)(equalTo(List(1, 1))))
        )
      ),
      suite("repeatEither")(
        testM("emits schedule output")(
          assertM(
            ZStream(1)
              .repeatEither(Schedule.recurs(4))
              .runCollect
          )(
            equalTo(
              List(
                Right(1),
                Right(1),
                Left(1),
                Right(1),
                Left(2),
                Right(1),
                Left(3),
                Right(1),
                Left(4)
              )
            )
          )
        ),
        testM("short circuits") {
          Live.live(for {
            ref <- Ref.make[List[Int]](Nil)
            _ <- ZStream
                  .fromEffect(ref.update(1 :: _))
                  .repeatEither(Schedule.spaced(10.millis))
                  .take(3) // take one schedule output
                  .runDrain
            result <- ref.get
          } yield assert(result)(equalTo(List(1, 1))))
        }
      ),
      suite("runHead")(
        testM("nonempty stream")(
          assertM(ZStream(1, 2, 3, 4).runHead)(equalTo(Some(1)))
        ),
        testM("empty stream")(
          assertM(ZStream.empty.runHead)(equalTo(None))
        )
      ),
      suite("runLast")(
        testM("nonempty stream")(
          assertM(ZStream(1, 2, 3, 4).runLast)(equalTo(Some(4)))
        ),
        testM("empty stream")(
          assertM(ZStream.empty.runLast)(equalTo(None))
        )
      ),
      suite("schedule")(
        testM("scheduleWith")(
          assertM(
            ZStream("A", "B", "C", "A", "B", "C")
              .scheduleWith(Schedule.recurs(2) *> Schedule.fromFunction((_) => "Done"))(_.toLowerCase, identity)
              .runCollect
          )(equalTo(List("a", "b", "c", "Done", "a", "b", "c", "Done")))
        ),
        testM("scheduleEither")(
          assertM(
            ZStream("A", "B", "C")
              .scheduleEither(Schedule.recurs(2) *> Schedule.fromFunction((_) => "!"))
              .runCollect
          )(equalTo(List(Right("A"), Right("B"), Right("C"), Left("!"))))
        )
      ),
      suite("scheduleElements")(
        testM("scheduleElementsWith")(
          assertM(
            ZStream("A", "B", "C")
              .scheduleElementsWith(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))(identity, _.toString)
              .runCollect
          )(equalTo(List("A", "123", "B", "123", "C", "123")))
        ),
        testM("scheduleElementsEither")(
          assertM(
            ZStream("A", "B", "C")
              .scheduleElementsEither(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))
              .runCollect
          )(equalTo(List(Right("A"), Left(123), Right("B"), Left(123), Right("C"), Left(123))))
        ),
        testM("repeated && assertspaced")(
          assertM(
            ZStream("A", "B", "C")
              .scheduleElements(Schedule.once)
              .runCollect
          )(equalTo(List("A", "A", "B", "B", "C", "C")))
        ),
        testM("short circuits in schedule")(
          assertM(
            ZStream("A", "B", "C")
              .scheduleElements(Schedule.once)
              .take(4)
              .runCollect
          )(equalTo(List("A", "A", "B", "B")))
        ),
        testM("short circuits after schedule")(
          assertM(
            ZStream("A", "B", "C")
              .scheduleElements(Schedule.once)
              .take(3)
              .runCollect
          )(equalTo(List("A", "A", "B")))
        )
      ),
      suite("toQueue")(
        testM("toQueue")(checkM(tinyChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
          val s = ZStream.fromChunk(c).flatMap(ZStream.succeed(_))
          assertM(
            s.toQueue(1000)
              .use(queue => queue.size.repeat(Schedule.doWhile(_ != c.size + 1)) *> queue.takeAll)
          )(
            equalTo(c.toSeq.toList.map(i => Exit.succeed(Chunk(i))) :+ Exit.fail(None))
          )
        }),
        testM("toQueueUnbounded")(checkM(tinyChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
          val s = ZStream.fromChunk(c).flatMap(ZStream.succeed(_))
          assertM(
            s.toQueueUnbounded.use(queue => queue.size.repeat(Schedule.doWhile(_ != c.size + 1)) *> queue.takeAll)
          )(
            equalTo(c.toSeq.toList.map(i => Exit.succeed(Chunk(i))) :+ Exit.fail(None))
          )
        })
      ),
      suite("zipWith")(
        testM("zip equivalence with Chunk#zipWith") {
          checkM(
            // We're using ZStream.fromChunks in the test, and that discards empty
            // chunks; so we're only testing for non-empty chunks here.
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0)),
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0))
          ) {
            (l, r) =>
              // zipWith pulls one last time after the last chunk,
              // so we take the smaller side + 1.
              val expected =
                if (l.size <= r.size)
                  Chunk.fromIterable(l).flatten.zipWith(Chunk.fromIterable(r.take(l.size + 1)).flatten)((_, _))
                else Chunk.fromIterable(l.take(r.size + 1)).flatten.zipWith(Chunk.fromIterable(r).flatten)((_, _))

              assertM(ZStream.fromChunks(l: _*).zip(ZStream.fromChunks(r: _*)).runCollect)(equalTo(expected.toList))
          }
        },
        testM("zipWith prioritizes failure") {
          assertM(
            ZStream.never
              .zipWith(ZStream.fail("Ouch"))((_, _) => None)
              .runCollect
              .either
          )(isLeft(equalTo("Ouch")))
        }
      ),
      suite("zipAllWith")(
        testM("zipAllWith") {
          checkM(
            // We're using ZStream.fromChunks in the test, and that discards empty
            // chunks; so we're only testing for non-empty chunks here.
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0)),
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0))
          ) { (l, r) =>
            val expected =
              Chunk
                .fromIterable(l)
                .flatten
                .zipAllWith(Chunk.fromIterable(r).flatten)(Some(_) -> None, None -> Some(_))(
                  Some(_) -> Some(_)
                )

            assertM(
              ZStream
                .fromChunks(l: _*)
                .map(Option(_))
                .zipAll(ZStream.fromChunks(r: _*).map(Option(_)))(None, None)
                .runCollect
            )(equalTo(expected.toList))
          }
        },
        testM("zipAllWith prioritizes failure") {
          assertM(
            ZStream.never
              .zipAll(ZStream.fail("Ouch"))(None, None)
              .runCollect
              .either
          )(isLeft(equalTo("Ouch")))
        }
      ),
      testM("zipWithIndex")(checkM(pureStreamOfBytes) { s =>
        for {
          res1 <- (s.zipWithIndex.runCollect)
          res2 <- (s.runCollect.map(_.zipWithIndex.map(t => (t._1, t._2.toLong))))
        } yield assert(res1)(equalTo(res2))
      }),
      testM("zipWithLatest") {
        import zio.test.environment.TestClock

        for {
          q  <- Queue.unbounded[(Int, Int)]
          s1 = ZStream.iterate(0)(_ + 1).fixed(100.millis)
          s2 = ZStream.iterate(0)(_ + 1).fixed(70.millis)
          s3 = s1.zipWithLatest(s2)((_, _))
          _  <- s3.foreach(q.offer).fork
          a  <- q.take
          _  <- TestClock.setTime(70.millis)
          b  <- q.take
          _  <- TestClock.setTime(100.millis)
          c  <- q.take
          _  <- TestClock.setTime(140.millis)
          d  <- q.take
          _  <- TestClock.setTime(210.millis)
        } yield assert(List(a, b, c, d))(equalTo(List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2)))
      }
    ),
    suite("Constructors")(
      testM("access") {
        for {
          result <- ZStream.access[String](identity).provide("test").runCollect.map(_.head)
        } yield assert(result)(equalTo("test"))
      },
      suite("accessM")(
        testM("accessM") {
          for {
            result <- ZStream.accessM[String](ZIO.succeedNow).provide("test").runCollect.map(_.head)
          } yield assert(result)(equalTo("test"))
        },
        testM("accessM fails") {
          for {
            result <- ZStream.accessM[Int](_ => ZIO.fail("fail")).provide(0).runCollect.run
          } yield assert(result)(fails(equalTo("fail")))
        }
      ),
      suite("accessStream")(
        testM("accessStream") {
          for {
            result <- ZStream.accessStream[String](ZStream.succeed(_)).provide("test").runCollect.map(_.head)
          } yield assert(result)(equalTo("test"))
        },
        testM("accessStream fails") {
          for {
            result <- ZStream.accessStream[Int](_ => ZStream.fail("fail")).provide(0).runCollect.run
          } yield assert(result)(fails(equalTo("fail")))
        }
      ),
      testM("environment") {
        for {
          result <- ZStream.environment[String].provide("test").runCollect.map(_.head)
        } yield assert(result)(equalTo("test"))
      },
      testM("fromChunk") {
        checkM(smallChunks(Gen.anyInt))(c => assertM(ZStream.fromChunk(c).runCollect)(equalTo(c.toList)))
      },
      suite("fromChunks")(
        testM("fromChunks") {
          checkM(Gen.listOf(smallChunks(Gen.anyInt))) { cs =>
            assertM(ZStream.fromChunks(cs: _*).runCollect)(equalTo(Chunk.fromIterable(cs).flatten.toList))
          }
        },
        testM("discards empty chunks") {
          ZStream.fromChunks(Chunk(1), Chunk.empty, Chunk(1)).process.use { pull =>
            assertM(nPulls(pull, 3))(equalTo(List(Right(Chunk(1)), Right(Chunk(1)), Left(None))))
          }
        }
      ),
      testM("fromInputStream") {
        import java.io.ByteArrayInputStream
        val chunkSize = ZStream.DefaultChunkSize
        val data      = Array.tabulate[Byte](chunkSize * 5 / 2)(_.toByte)
        def is        = new ByteArrayInputStream(data)
        ZStream.fromInputStream(is, chunkSize).runCollect map { bytes => assert(bytes.toArray)(equalTo(data)) }
      },
      suite("fromIteratorManaged")(
        testM("is safe to pull again after success") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .fromIteratorManaged(Managed.make(UIO.succeedNow(List(1, 2).iterator))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 4))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(
            equalTo(List(Right(Chunk(1)), Right(Chunk(2)), Left(None), Left(None)))
          )
        },
        testM("is safe to pull again after failed acquisition") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .fromIteratorManaged(Managed.make(IO.fail("Ouch"))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isFalse) && assert(pulls)(equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
        },
        testM("is safe to pull again after inner failure") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .fromIteratorManaged(Managed.make(UIO.succeedNow(List(1, 2).iterator))(_ => ref.set(true)))
                      .flatMap(n =>
                        ZStream.succeed((n * 2).toString) ++ ZStream.fail("Ouch") ++ ZStream.succeed(
                          (n * 3).toString
                        )
                      )
                      .process
                      .use(nPulls(_, 8))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(
            equalTo(
              List(
                Right(Chunk("2")),
                Left(Some("Ouch")),
                Right(Chunk("3")),
                Right(Chunk("4")),
                Left(Some("Ouch")),
                Right(Chunk("6")),
                Left(None),
                Left(None)
              )
            )
          )
        },
        testM("is safe to pull again from a failed Managed") {
          ZStream
            .fromIteratorManaged(Managed.fail("Ouch"))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Left(Some("Ouch")), Left(None), Left(None)))))
        }
      ),
      testM("concatAll") {
        checkM(Gen.listOf(smallChunks(Gen.anyInt))) { chunks =>
          assertM(ZStream.concatAll(Chunk.fromIterable(chunks.map(ZStream.fromChunk(_)))).runCollect)(
            equalTo(Chunk.fromIterable(chunks).flatten.toList)
          )
        }
      },
      testM("repeatEffectWith")(
        Live.live(for {
          ref <- Ref.make[List[Int]](Nil)
          _ <- ZStream
                .repeatEffectWith(ref.update(1 :: _), Schedule.spaced(10.millis))
                .take(2)
                .runDrain
          result <- ref.get
        } yield assert(result)(equalTo(List(1, 1))))
      )
    )
  )
}
