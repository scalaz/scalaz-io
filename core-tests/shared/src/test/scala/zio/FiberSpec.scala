package zio

import zio.LatchOps._
import zio.duration._
import zio.internal.FiberRenderer
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object FiberSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec =
    suite("FiberSpec")(
      suite("Create a new Fiber and")(testM("lift it into Managed") {
        for {
          ref   <- Ref.make(false)
          fiber <- withLatch(release => (release *> IO.unit).bracket_(ref.set(true))(IO.never).fork)
          _     <- fiber.toManaged.use(_ => IO.unit)
          _     <- fiber.await
          value <- ref.get
        } yield assert(value)(isTrue)
      }),
      suite("`inheritLocals` works for Fiber created using:")(
        testM("`map`") {
          for {
            fiberRef <- FiberRef.make(initial)
            child    <- withLatch(release => (fiberRef.set(update) *> release).fork)
            _        <- child.map(_ => ()).inheritRefs
            value    <- fiberRef.get
          } yield assert(value)(equalTo(update))
        },
        testM("`orElse`") {
          for {
            fiberRef <- FiberRef.make(initial)
            latch1   <- Promise.make[Nothing, Unit]
            latch2   <- Promise.make[Nothing, Unit]
            child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
            child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
            _        <- latch1.await *> latch2.await
            _        <- child1.orElse(child2).inheritRefs
            value    <- fiberRef.get
          } yield assert(value)(equalTo("child1"))
        },
        testM("`zip`") {
          for {
            fiberRef <- FiberRef.make(initial)
            latch1   <- Promise.make[Nothing, Unit]
            latch2   <- Promise.make[Nothing, Unit]
            child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
            child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
            _        <- latch1.await *> latch2.await
            _        <- child1.zip(child2).inheritRefs
            value    <- fiberRef.get
          } yield assert(value)(equalTo("child1"))
        }
      ),
      suite("`Fiber.join` on interrupted Fiber")(
        testM("is inner interruption") {
          val fiberId = Fiber.Id(0L, 123L)

          for {
            exit <- Fiber.interruptAs(fiberId).join.run
          } yield assert(exit)(equalTo(Exit.interrupt(fiberId)))
        }
      ) @@ zioTag(interruption),
      suite("if one composed fiber fails then all must fail")(
        testM("`await`") {
          for {
            exit <- Fiber.fail("fail").zip(Fiber.never).await
          } yield assert(exit)(fails(equalTo("fail")))
        },
        testM("`join`") {
          for {
            exit <- Fiber.fail("fail").zip(Fiber.never).join.run
          } yield assert(exit)(fails(equalTo("fail")))
        },
        testM("`awaitAll`") {
          for {
            exit <- Fiber.awaitAll(Fiber.fail("fail") :: List.fill(100)(Fiber.never)).run
          } yield assert(exit)(succeeds(isUnit))
        },
        testM("`joinAll`") {
          for {
            exit <- Fiber.awaitAll(Fiber.fail("fail") :: List.fill(100)(Fiber.never)).run
          } yield assert(exit)(succeeds(isUnit))
        },
        testM("shard example") {
          def shard[R, E, A](queue: Queue[A], n: Int, worker: A => ZIO[R, E, Unit]): ZIO[R, E, Nothing] = {
            val worker1 = queue.take.flatMap(a => worker(a).uninterruptible).forever
            ZIO.forkAll(List.fill(n)(worker1)).flatMap(_.join) *> ZIO.never
          }
          for {
            queue  <- Queue.unbounded[Int]
            _      <- queue.offerAll(1 to 100)
            worker = (n: Int) => if (n == 100) ZIO.fail("fail") else queue.offer(n).unit
            exit   <- shard(queue, 4, worker).run
            _      <- queue.shutdown
          } yield assert(exit)(fails(equalTo("fail")))
        }
      ) @@ zioTag(errors),
      testM("grandparent interruption is propagated to grandchild despite parent termination") {
        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          c      = ZIO.never.interruptible.onInterrupt(latch2.succeed(()))
          a      = (latch1.succeed(()) *> c.fork.fork).uninterruptible *> ZIO.never
          fiber  <- a.fork
          _      <- latch1.await
          _      <- fiber.interrupt
          _      <- latch2.await
        } yield assertCompletes
      } @@ zioTag(interruption) @@ nonFlaky,
      suite("stack safety")(
        testM("awaitAll") {
          assertM(Fiber.awaitAll(fibers))(anything)
        },
        testM("joinAll") {
          assertM(Fiber.joinAll(fibers))(anything)
        },
        testM("collectAll") {
          assertM(Fiber.collectAll(fibers).join)(anything)
        }
      ) @@ sequential,
      suite("fiber dump tree")(
        zio.test.test("render fiber hierarchy tree") {
          def node(n: Long, children: Seq[Fiber.Dump]): Fiber.Dump =
            Fiber.Dump(Fiber.Id(n, n), Some(n.toString), Fiber.Status.Done, children, None)

          val tree1 =
            node(1, Seq(node(11, Seq(node(111, Nil), node(112, Nil))), node(12, Seq(node(121, Nil), node(122, Nil)))))
          val tree2 =
            node(2, Seq(node(21, Seq(node(211, Nil), node(212, Nil))), node(12, Seq(node(221, Nil), node(222, Nil)))))
          val expected = """#+---"1" #1 Status: Done
                         #|   +---"11" #11 Status: Done
                         #|   |   +---"111" #111 Status: Done
                         #|   |   +---"112" #112 Status: Done
                         #|   +---"12" #12 Status: Done
                         #|       +---"121" #121 Status: Done
                         #|       +---"122" #122 Status: Done
                         #+---"2" #2 Status: Done
                         #    +---"21" #21 Status: Done
                         #    |   +---"211" #211 Status: Done
                         #    |   +---"212" #212 Status: Done
                         #    +---"12" #12 Status: Done
                         #        +---"221" #221 Status: Done
                         #        +---"222" #222 Status: Done
                         #""".stripMargin('#')
          assert(FiberRenderer.renderHierarchy(Seq(tree1, tree2)))(equalTo(expected))
        }
      ),
      suite("track blockingOn")(
        testM("in await") {
          for {
            p    <- Promise.make[Nothing, Unit]
            f1   <- p.await.fork
            f1id <- f1.id
            f2   <- f1.await.fork
            blockingOn <- (ZIO.yieldNow *> f2.dump)
                           .map(_.status)
                           .repeat(
                             Schedule.doUntil[Fiber.Status, List[Fiber.Id]] {
                               case Fiber.Status.Suspended(_, _, _, blockingOn, _) => blockingOn
                             } <* Schedule.fixed(10.milli)
                           )
          } yield assert(blockingOn)(isSome(equalTo(List(f1id))))
        },
        testM("in race") {
          for {
            p <- Promise.make[Nothing, Unit]
            f <- p.await.race(p.await).fork
            blockingOn <- (ZIO.yieldNow *> f.dump)
                           .map(_.status)
                           .repeat(
                             Schedule.doUntil[Fiber.Status, List[Fiber.Id]] {
                               case Fiber.Status.Suspended(_, _, _, blockingOn, _) => blockingOn
                             } <* Schedule.fixed(10.milli)
                           )
          } yield assert(blockingOn)(isSome(hasSize(equalTo(2))))
        }
      )
    )

  val (initial, update) = ("initial", "update")
  val fibers            = List.fill(100000)(Fiber.unit)
}
