package zio

import zio.SerializableSpecHelpers._
import zio.internal.stacktracer.ZTraceElement
import zio.test.Assertion._
import zio.test.TestAspect.scala2Only
import zio.test.environment.Live
import zio.test.{test => testSync, _}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

object SerializableSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("SerializableSpec")(
    testM("Semaphore is serializable") {
      val n = 20L
      for {
        semaphore   <- Semaphore.make(n)
        count       <- semaphore.available.commit
        returnSem   <- serializeAndBack(semaphore)
        returnCount <- returnSem.available.commit
      } yield assert(returnCount)(equalTo(count))
    },
    testM("Clock is serializable") {
      for {
        clock       <- Live.live(ZIO.service[Clock])
        time1       <- Clock.nanoTime
        returnClock <- serializeAndBack(clock)
        time2       <- returnClock.nanoTime
      } yield assert(time1)(isLessThanEqualTo(time2))
    },
    testM("Queue is serializable") {
      for {
        queue       <- Queue.bounded[Int](100)
        _           <- queue.offer(10)
        returnQueue <- serializeAndBack(queue)
        v1          <- returnQueue.take
        _           <- returnQueue.offer(20)
        v2          <- returnQueue.take
      } yield assert(v1)(equalTo(10)) &&
        assert(v2)(equalTo(20))
    },
    testM("Hub is serializable") {
      for {
        hub                     <- Hub.bounded[Int](100)
        queue                   <- hub.subscribe.reserve.flatMap(_.acquire)
        _                       <- hub.publish(10)
        tuple                   <- serializeAndBack((hub, queue))
        (returnHub, returnQueue) = tuple
        v1                      <- returnQueue.take
        _                       <- returnHub.publish(20)
        v2                      <- returnQueue.take
      } yield assert(v1)(equalTo(10)) &&
        assert(v2)(equalTo(20))
    },
    testM("Ref is serializable") {
      val current = "This is some value"
      for {
        ref       <- Ref.make(current)
        returnRef <- serializeAndBack(ref)
        value     <- returnRef.get
      } yield assert(value)(equalTo(current))
    },
    testM("IO is serializable") {
      val list = List("1", "2", "3")
      val io   = IO.succeed(list)
      for {
        returnIO <- serializeAndBack(io)
        result   <- returnIO
      } yield assert(result)(equalTo(list))
    },
    testM("ZIO is serializable") {
      val v = ZIO.fromFunction[Int, Int](_ + 1)
      for {
        returnZIO <- serializeAndBack(v)
        computeV  <- returnZIO.provide(9)
      } yield assert(computeV)(equalTo(10))
    },
    testM("FiberStatus is serializable") {
      val list = List("1", "2", "3")
      val io   = IO.succeed(list)
      for {
        fiber          <- io.fork
        status         <- fiber.await
        returnedStatus <- serializeAndBack(status)
      } yield {
        assert(returnedStatus.getOrElse(_ => List.empty))(equalTo(list))
      }
    },
    testM("Duration is serializable") {
      val duration = Duration.fromNanos(1)
      for {
        returnDuration <- serializeAndBack(duration)
      } yield assert(returnDuration)(equalTo(duration))
    },
    testSync("Cause.die is serializable") {
      val cause = Cause.die(TestException("test"))
      assert(serializeAndDeserialize(cause))(equalTo(cause))
    },
    testSync("Cause.fail is serializable") {
      val cause = Cause.fail("test")
      assert(serializeAndDeserialize(cause))(equalTo(cause))
    },
    testSync("Cause.traced is serializable") {
      val fiberId = Fiber.Id(0L, 0L)
      val cause   = Cause.traced(Cause.fail("test"), ZTrace(fiberId, List.empty, List.empty, None))
      assert(serializeAndDeserialize(cause))(equalTo(cause))
    },
    testSync("Cause.&& is serializable") {
      val cause = Cause.fail("test") && Cause.fail("Another test")
      assert(serializeAndDeserialize(cause))(equalTo(cause))
    },
    testSync("Cause.++ is serializable") {
      val cause = Cause.fail("test") ++ Cause.fail("Another test")
      assert(serializeAndDeserialize(cause))(equalTo(cause))
    },
    testSync("Exit.succeed is serializable") {
      val exit = Exit.succeed("test")
      assert(serializeAndDeserialize(exit))(equalTo(exit))
    },
    testSync("Exit.fail is serializable") {
      val exit = Exit.fail("test")
      assert(serializeAndDeserialize(exit))(equalTo(exit))
    },
    testSync("Exit.die is serializable") {
      val exit = Exit.die(TestException("test"))
      assert(serializeAndDeserialize(exit))(equalTo(exit))
    },
    testSync("FiberFailure is serializable") {
      val failure = FiberFailure(Cause.fail("Uh oh"))
      assert(serializeAndDeserialize(failure))(equalTo(failure))
    },
    testSync("InterruptStatus.interruptible is serializable") {
      val interruptStatus = InterruptStatus.interruptible
      assert(serializeAndDeserialize(interruptStatus))(equalTo(interruptStatus))
    },
    testSync("InterruptStatus.uninterruptible is serializable") {
      val interruptStatus = InterruptStatus.uninterruptible
      assert(serializeAndDeserialize(interruptStatus))(equalTo(interruptStatus))
    },
    testM("Promise is serializable") {
      for {
        promise           <- Promise.make[Nothing, String]
        _                 <- promise.succeed("test")
        value             <- promise.await
        deserialized      <- serializeAndBack(promise)
        deserializedValue <- deserialized.await
      } yield assert(deserializedValue)(equalTo(value))
    },
    testM("Schedule is serializable") {
      val schedule = Schedule.recurs(5)
      for {
        out1 <- ZIO.unit.repeat(schedule)
        out2 <- ZIO.unit.repeat(serializeAndDeserialize(schedule))
      } yield assert(out2)(equalTo(out1))
    } @@ scala2Only,
    testM("Chunk.single is serializable") {
      val chunk = Chunk.single(1)
      for {
        deserialized <- serializeAndBack(chunk)
      } yield assert(deserialized)(equalTo(chunk))
    },
    testM("Chunk.fromArray is serializable") {
      val chunk = Chunk.fromArray(Array(1, 2, 3))
      for {
        deserialized <- serializeAndBack(chunk)
      } yield assert(deserialized)(equalTo(chunk))
    },
    testM("Chunk.++ is serializable") {
      val chunk = Chunk.single(1) ++ Chunk.single(2)
      for {
        deserialized <- serializeAndBack(chunk)
      } yield assert(deserialized)(equalTo(chunk))
    },
    testM("Chunk slice is serializable") {
      val chunk = Chunk.fromArray((1 to 100).toArray).take(10)
      for {
        deserialized <- serializeAndBack(chunk)
      } yield assert(deserialized)(equalTo(chunk))
    },
    testM("Chunk.empty is serializable") {
      val chunk = Chunk.empty
      for {
        deserialized <- serializeAndBack(chunk)
      } yield assert(deserialized)(equalTo(chunk))
    } @@ scala2Only,
    testM("Chunk.fromIterable is serializable") {
      val chunk = Chunk.fromIterable(Vector(1, 2, 3))
      for {
        deserialized <- serializeAndBack(chunk)
      } yield assert(deserialized)(equalTo(chunk))
    },
    testM("FiberRef is serializable") {
      val value = 10
      val init  = FiberRef(value)

      for {
        ref    <- serializeAndBack(init)
        result <- ref.get
      } yield assert(result)(equalTo(value))
    },
    testM("ZManaged is serializable") {
      for {
        managed <- serializeAndBack(ZManaged.acquireReleaseWith(UIO.unit)(_ => UIO.unit))
        result  <- managed.use(_ => UIO.unit)
      } yield assert(result)(equalTo(()))
    },
    testSync("ZTrace is serializable") {
      val trace = ZTrace(
        Fiber.Id(0L, 0L),
        List(ZTraceElement.NoLocation("test")),
        List(ZTraceElement.SourceLocation("file.scala", "Class", "method", 123)),
        None
      )

      assert(serializeAndDeserialize(trace))(equalTo(trace))
    },
    testM("TracingStatus.Traced is serializable") {
      val traced = TracingStatus.Traced
      for {
        result <- serializeAndBack(traced)
      } yield assert(result)(equalTo(traced))
    },
    testM("TracingStatus.Untraced is serializable") {
      val traced = TracingStatus.Untraced
      for {
        result <- serializeAndBack(traced)
      } yield assert(result)(equalTo(traced))
    },
    testM("TracingStatus.Untraced is serializable") {
      Live.live(for {
        system <- ZIO.serviceWith[System](serializeAndBack(_))
        result <- system.property("notpresent")
      } yield assert(result)(equalTo(Option.empty[String])))
    }
  )
}

object SerializableSpecHelpers {
  def serializeAndBack[T](a: T): IO[Any, T] =
    for {
      obj       <- IO.succeed(serializeToBytes(a))
      returnObj <- IO.succeed(getObjFromBytes[T](obj))
    } yield returnObj

  def serializeToBytes[T](a: T): Array[Byte] = {
    val bf  = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(bf)
    oos.writeObject(a)
    oos.close()
    bf.toByteArray
  }

  def getObjFromBytes[T](bytes: Array[Byte]): T = {
    val ios = new ObjectInputStream(new ByteArrayInputStream(bytes))
    ios.readObject().asInstanceOf[T]
  }

  def serializeAndDeserialize[T](a: T): T = getObjFromBytes(serializeToBytes(a))

  case class TestException(msg: String) extends RuntimeException(msg)

}
