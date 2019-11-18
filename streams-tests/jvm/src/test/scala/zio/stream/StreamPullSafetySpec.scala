package zio.stream

import zio._
import zio.test._
import zio.test.Assertion.{ equalTo, isFalse, isTrue }
import ZStream.Pull
import StreamUtils.nPulls

object StreamPullSafetySpec extends ZIOBaseSpec {

  def spec = suite("StreamPullSafetySpec")(
    suite("Stream.bracket")(
      testM("is safe to pull again after success") {
        for {
          ref   <- Ref.make(false)
          pulls <- Stream.bracket(UIO.succeed(5))(_ => ref.set(true)).process.use(nPulls(_, 3))
          fin   <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(5), Left(None), Left(None))))
      },
      testM("is safe to pull again after failed acqusition") {
        for {
          ref   <- Ref.make(false)
          pulls <- Stream.bracket(IO.fail("Ouch"))(_ => ref.set(true)).process.use(nPulls(_, 3))
          fin   <- ref.get
        } yield assert(fin, isFalse) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
      },
      testM("is safe to pull again after inner failure") {
        for {
          ref <- Ref.make(false)
          pulls <- Stream
                    .bracket(UIO.succeed(5))(_ => ref.set(true))
                    .flatMap(_ => Stream.fail("Ouch"))
                    .process
                    .use(nPulls(_, 3))
          fin <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
      }
    ),
    suite("Stream.bracketExit")(
      testM("is safe to pull again after success") {
        for {
          ref   <- Ref.make(false)
          pulls <- Stream.bracketExit(UIO.succeed(5))((_, _) => ref.set(true)).process.use(nPulls(_, 3))
          fin   <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(5), Left(None), Left(None))))
      },
      testM("is safe to pull again after failed acquisition") {
        for {
          ref   <- Ref.make(false)
          pulls <- Stream.bracketExit(IO.fail("Ouch"))((_, _) => ref.set(true)).process.use(nPulls(_, 3))
          fin   <- ref.get
        } yield assert(fin, isFalse) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
      },
      testM("is safe to pull again after inner failure") {
        for {
          ref <- Ref.make(false)
          pulls <- Stream
                    .bracketExit(UIO.succeed(5))((_, _) => ref.set(true))
                    .flatMap(_ => Stream.fail("Ouch"))
                    .process
                    .use(nPulls(_, 3))
          fin <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
      }
    ),
    testM("Stream.empty is safe to pull again") {
      Stream.empty.process
        .use(nPulls(_, 3))
        .map(assert(_, equalTo(List(Left(None), Left(None), Left(None)))))
    },
    suite("Stream.effectAsync")(
      testM("is safe to pull again after error") {
        Stream
          .effectAsync[String, Int] { k =>
            List(1, 2, 3).foreach { n =>
              k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
            }
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(1), Left(Some("Ouch")), Right(3)))))
      },
      testM("is safe to pull again after end") {
        Stream
          .effectAsync[String, Int] { k =>
            k(Pull.emit(1))
            k(Pull.end)
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
      }
    ),
    suite("Stream.effectAsyncM")(
      testM("is safe to pull again after error") {
        Stream
          .effectAsyncM[String, Int] { k =>
            List(1, 2, 3).foreach { n =>
              k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
            }
            UIO.unit
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(1), Left(Some("Ouch")), Right(3)))))
      },
      testM("is safe to pull again after end") {
        Stream
          .effectAsyncM[String, Int] { k =>
            k(Pull.emit(1))
            k(Pull.end)
            UIO.unit
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
      }
    ),
    suite("Stream.effectAsyncMaybe")(
      testM("is safe to pull again after error async case") {
        Stream
          .effectAsyncMaybe[String, Int] { k =>
            List(1, 2, 3).foreach { n =>
              k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
            }
            None
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(1), Left(Some("Ouch")), Right(3)))))
      },
      testM("is safe to pull again after error sync case") {
        Stream
          .effectAsyncMaybe[String, Int] { k =>
            k(Pull.fail("Ouch async"))
            Some(Stream.fail("Ouch sync"))
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Left(Some("Ouch sync")), Left(None), Left(None)))))
      },
      testM("is safe to pull again after end async case") {
        Stream
          .effectAsyncMaybe[String, Int] { k =>
            k(Pull.emit(1))
            k(Pull.end)
            None
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
      },
      testM("is safe to pull again after end sync case") {
        Stream
          .effectAsyncMaybe[String, Int] { k =>
            k(Pull.fail("Ouch async"))
            Some(Stream.empty)
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Left(None), Left(None), Left(None)))))
      }
    ),
    suite("Stream.effectAsyncInterrupt")(
      testM("is safe to pull again after error async case") {
        for {
          ref <- Ref.make(false)
          pulls <- Stream
                    .effectAsyncInterrupt[String, Int] { k =>
                      List(1, 2, 3).foreach { n =>
                        k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
                      }
                      Left(ref.set(true))
                    }
                    .process
                    .use(nPulls(_, 3))
          fin <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(1), Left(Some("Ouch")), Right(3))))
      },
      testM("is safe to pull again after error sync case") {
        Stream
          .effectAsyncInterrupt[String, Int] { k =>
            k(IO.fail(Some("Ouch async")))
            Right(Stream.fail("Ouch sync"))
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Left(Some("Ouch sync")), Left(None), Left(None)))))
      },
      testM("is safe to pull again after end async case") {
        for {
          ref <- Ref.make(false)
          pulls <- Stream
                    .effectAsyncInterrupt[String, Int] { k =>
                      k(Pull.emit(1))
                      k(Pull.end)
                      Left(ref.set(true))
                    }
                    .process
                    .use(nPulls(_, 3))
          fin <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(1), Left(None), Left(None))))
      },
      testM("is safe to pull again after end sync case") {
        Stream
          .effectAsyncInterrupt[String, Int] { k =>
            k(IO.fail(Some("Ouch async")))
            Right(Stream.empty)
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Left(None), Left(None), Left(None)))))
      }
    ),
    testM("Stream.fail is safe to pull again") {
      Stream
        .fail("Ouch")
        .process
        .use(nPulls(_, 3))
        .map(assert(_, equalTo(List(Left(Some("Ouch")), Left(None), Left(None)))))
    },
    testM("Stream.finalizer is safe to pull again") {
      for {
        ref   <- Ref.make(0)
        pulls <- Stream.finalizer(ref.update(_ + 1)).process.use(nPulls(_, 3))
        fin   <- ref.get
      } yield assert(fin, equalTo(1)) && assert(pulls, equalTo(List(Left(None), Left(None), Left(None))))
    },
    testM("Stream.fromChunk is safe to pull again") {
      Stream
        .fromChunk(Chunk(1))
        .process
        .use(nPulls(_, 3))
        .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
    },
    suite("Stream.fromEffect")(
      testM("is safe to pull again after success") {
        Stream
          .fromEffect(UIO.succeed(5))
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(5), Left(None), Left(None)))))
      },
      testM("is safe to pull again after failure") {
        Stream
          .fromEffect(IO.fail("Ouch"))
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Left(Some("Ouch")), Left(None), Left(None)))))
      }
    ),
    testM("Stream.fromInputStream is safe to pull again") {
      val error = new java.io.IOException()
      val is = new java.io.InputStream {
        var state = 0

        def read(): Int = {
          state += 1
          if (state == 2) throw error
          else if (state == 4) -1
          else state
        }
      }

      Stream
        .fromInputStream(is, 1)
        .process
        .use(nPulls(_, 5))
        .map(assert(_, equalTo(List(Right(1), Left(Some(error)), Right(3), Left(None), Left(None)))))
    },
    testM("Stream.fromIterable is safe to pull again") {
      Stream
        .fromIterable(List(1))
        .process
        .use(nPulls(_, 3))
        .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
    },
    testM("Stream.fromQueue is safe to pull again") {
      for {
        queue <- Queue.bounded[Int](1)
        pulls <- Stream
                  .fromQueue(queue)
                  .process
                  .use { pull =>
                    for {
                      _  <- queue.offer(1)
                      e1 <- pull.either
                      _  <- queue.offer(2)
                      e2 <- pull.either
                      _  <- queue.shutdown
                      e3 <- pull.either
                      e4 <- pull.either
                    } yield List(e1, e2, e3, e4)
                  }
      } yield assert(pulls, equalTo(List(Right(1), Right(2), Left(None), Left(None))))
    },
    testM("Stream.fromQueueWithShutdown is safe to pull again") {
      for {
        queue <- Queue.bounded[Int](1)
        pulls <- Stream
                  .fromQueueWithShutdown(queue)
                  .process
                  .use { pull =>
                    for {
                      _  <- queue.offer(1)
                      e1 <- pull.either
                      _  <- queue.offer(2)
                      e2 <- pull.either
                    } yield List(e1, e2)
                  }
        fin <- queue.isShutdown
      } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(1), Right(2))))
    },
    suite("Stream.managed")(
      testM("is safe to pull again after success") {
        for {
          ref   <- Ref.make(false)
          pulls <- Stream.managed(Managed.make(UIO.succeed(5))(_ => ref.set(true))).process.use(nPulls(_, 3))
          fin   <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(5), Left(None), Left(None))))
      },
      testM("is safe to pull again after failed acquisition") {
        for {
          ref   <- Ref.make(false)
          pulls <- Stream.managed(Managed.make(IO.fail("Ouch"))(_ => ref.set(true))).process.use(nPulls(_, 3))
          fin   <- ref.get
        } yield assert(fin, isFalse) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
      },
      testM("is safe to pull again after inner failure") {
        for {
          ref <- Ref.make(false)
          pulls <- Stream
                    .managed(Managed.make(UIO.succeed(5))(_ => ref.set(true)))
                    .flatMap(_ => Stream.fail("Ouch"))
                    .process
                    .use(nPulls(_, 3))
          fin <- ref.get
        } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
      }
    ),
    suite("Stream.paginate")(
      testM("is safe to pull again after success") {
        Stream
          .paginate(0)(n => UIO.succeed((n, None)))
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(0), Left(None), Left(None)))))
      },
      testM("is safe to pull again after failure") {
        for {
          ref <- Ref.make(false)
          pulls <- Stream
                    .paginate(1) { n =>
                      ref.get.flatMap { done =>
                        if (n == 2 && !done) ref.set(true) *> IO.fail("Ouch")
                        else UIO.succeed((n, Some(n + 1)))
                      }
                    }
                    .process
                    .use(nPulls(_, 3))
        } yield assert(pulls, equalTo(List(Right(1), Left(Some("Ouch")), Right(2))))
      }
    ),
    testM("Stream.unfold is safe to pull again") {
      Stream
        .unfold(0) { n =>
          if (n > 2) None
          else Some((n, n + 1))
        }
        .process
        .use(nPulls(_, 5))
        .map(assert(_, equalTo(List(Right(0), Right(1), Right(2), Left(None), Left(None)))))
    },
    suite("Stream.unfoldM")(
      testM("is safe to pull again after success") {
        Stream
          .unfoldM(0) { n =>
            if (n == 1) UIO.succeed(None)
            else UIO.succeed(Some((n, n + 1)))
          }
          .process
          .use(nPulls(_, 3))
          .map(assert(_, equalTo(List(Right(0), Left(None), Left(None)))))
      },
      testM("is safe to pull again after failure") {
        for {
          ref <- Ref.make(false)
          pulls <- Stream
                    .unfoldM(1) { n =>
                      ref.get.flatMap { done =>
                        if (n == 2 && !done) ref.set(true) *> IO.fail("Ouch")
                        else if (n > 2) UIO.succeed(None)
                        else UIO.succeed(Some((n, n + 1)))
                      }
                    }
                    .process
                    .use(nPulls(_, 5))
        } yield assert(pulls, equalTo(List(Right(1), Left(Some("Ouch")), Right(2), Left(None), Left(None))))
      }
    )
  )
}
