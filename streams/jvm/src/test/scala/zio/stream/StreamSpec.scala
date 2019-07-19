package zio.stream

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck

import scala.{ Stream => _ }
import zio._
import zio.duration._
import zio.QueueSpec.waitForSize

class ZStreamSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with StreamTestUtils
    with GenIO
    with ScalaCheck {

  import ArbitraryChunk._
  import ArbitraryStream._
  import Exit.{ Cause => _, _ }
  import zio.Cause

  def is = "StreamSpec".title ^ s2"""
  Stream.aggregate
    aggregate                            $aggregate
    error propagation                    $aggregateErrorPropagation1
    error propagation                    $aggregateErrorPropagation2
    interruption propagation             $aggregateInterruptionPropagation
    interruption propagation             $aggregateInterruptionPropagation2

  Stream.aggregateWithin
    aggregateWithin                      $aggregateWithin
    error propagation                    $aggregateWithinErrorPropagation1
    error propagation                    $aggregateWithinErrorPropagation2
    interruption propagation             $aggregateWithinInterruptionPropagation
    interruption propagation             $aggregateWithinInterruptionPropagation2

  Stream.bracket
    bracket                              $bracket
    bracket short circuits               $bracketShortCircuits
    no acquisition when short circuiting $bracketNoAcquisition
    releases when there are defects      $bracketWithDefects

  Stream.buffer
    buffer the Stream                      $bufferStream
    buffer the Stream with Error           $bufferStreamError
    fast producer progress independently   $bufferFastProducerSlowConsumer

  Stream.collect            $collect
  Stream.collectWhile
    collectWhile                $collectWhile
    collectWhile short circuits $collectWhileShortCircuits

  Stream.concat
    concat                  $concat
    finalizer order         $concatFinalizerOrder

  Stream.drain              $drain

  Stream.dropWhile
    dropWhile         $dropWhile
    short circuits    $dropWhileShortCircuiting

  Stream.effectAsync
    effectAsync                 $effectAsync

  Stream.effectAsyncMaybe
    effectAsyncMaybe Some       $effectAsyncMaybeSome
    effectAsyncMaybe None       $effectAsyncMaybeNone

  Stream.effectAsyncM
    effectAsyncM                $effectAsyncM

  Stream.effectAsyncInterrupt
    effectAsyncInterrupt Left   $effectAsyncInterruptLeft
    effectAsyncInterrupt Right  $effectAsyncInterruptRight

  Stream.ensuring $ensuring

  Stream.finalizer $finalizer

  Stream.filter
    filter            $filter
    short circuits #1 $filterShortCircuiting1
    short circuits #2 $filterShortCircuiting2

  Stream.filterM
    filterM           $filterM
    short circuits #1 $filterMShortCircuiting1
    short circuits #2 $filterMShortCircuiting2

  Stream.flatMap
    deep flatMap stack safety $flatMapStackSafety
    left identity             $flatMapLeftIdentity
    right identity            $flatMapRightIdentity
    associativity             $flatMapAssociativity

  Stream.flatMapPar/flattenPar/mergeAll
    consistent with flatMap            $flatMapParConsistency
    short circuiting                   $flatMapParShortCircuiting
    interruption propagation           $flatMapParInterruptionPropagation
    inner errors interrupt all fibers  $flatMapParInnerErrorsInterruptAllFibers
    outer errors interrupt all fibers  $flatMapParOuterErrorsInterruptAllFibers
    inner defects interrupt all fibers $flatMapParInnerDefectsInterruptAllFibers
    outer defects interrupt all fibers $flatMapParOuterDefectsInterruptAllFibers
    finalizer ordering                 $flatMapParFinalizerOrdering

  Stream.foreach/foreachWhile
    foreach                     $foreach
    foreachWhile                $foreachWhile
    foreachWhile short circuits $foreachWhileShortCircuits

  Stream.forever            $forever
  Stream.fromChunk          $fromChunk
  Stream.fromInputStream    $fromInputStream
  Stream.fromIterable       $fromIterable
  Stream.fromQueue          $fromQueue
  Stream.map                $map
  Stream.mapAccum           $mapAccum
  Stream.mapAccumM          $mapAccumM
  Stream.mapConcat          $mapConcat
  Stream.mapM               $mapM

  Stream.repeatEffect       $repeatEffect
  Stream.repeatEffectWith   $repeatEffectWith

  Stream.mapMPar
    foreachParN equivalence       $mapMPar
    interruption propagation      $mapMParInterruptionPropagation

  Stream merging
    merge                         $merge
    mergeEither                   $mergeEither
    mergeWith                     $mergeWith
    mergeWith short circuit       $mergeWithShortCircuit
    mergeWith prioritizes failure $mergeWithPrioritizesFailure

  Stream.peel               $peel
  Stream.range              $range

  Stream.repeat
    repeat                  $repeat
    short circuits          $repeatShortCircuits

  Stream.spaced
    spaced                        $spaced
    spacedEither                  $spacedEither
    repeated and spaced           $repeatedAndSpaced
    short circuits in schedule    $spacedShortCircuitsWhileInSchedule
    short circuits after schedule $spacedShortCircuitsAfterScheduleFinished

  Stream.take
    take                     $take
    take short circuits      $takeShortCircuits
    take(0) short circuits   $take0ShortCircuitsStreamNever
    take(1) short circuits   $take1ShortCircuitsStreamNever
    takeWhile                $takeWhile
    takeWhile short circuits $takeWhileShortCircuits

  Stream.tap                $tap

  Stream.throttleEnforce
    free elements                   $throttleEnforceFreeElements
    no bandwidth                    $throttleEnforceNoBandwidth
    throttle enforce short circuits $throttleEnforceShortCircuits

  Stream.throttleShape
    free elements                 $throttleShapeFreeElements
    throttle shape short circuits $throttleShapeShortCircuits

  Stream.toQueue            $toQueue

  Stream.transduce
    transduce                            $transduce
    no remainder                         $transduceNoRemainder
    with remainder                       $transduceWithRemainder
    with a sink that always signals more $transduceSinkMore
    managed                              $transduceManaged
    propagate managed error              $transduceManagedError

  Stream.unfold             $unfold
  Stream.unfoldM            $unfoldM

  Stream.unTake
    unTake happy path       $unTake
    unTake with error       $unTakeError

  Stream zipping
    zipWith                     $zipWith
    zipWithIndex                $zipWithIndex
    zipWith ignore RHS          $zipWithIgnoreRhs
    zipWith prioritizes failure $zipWithPrioritizesFailure
  """

  def aggregate = unsafeRun {
    Stream(1, 1, 1, 1)
      .aggregate(ZSink.foldUntil(List[Int](), 3)((acc, el: Int) => el :: acc).map(_.reverse))
      .runCollect
      .map { result =>
        (result.flatten must_=== List(1, 1, 1, 1)) and
          (result.forall(_.length <= 3) must_=== true)
      }
  }

  def aggregateErrorPropagation1 =
    unsafeRun {
      val e    = new RuntimeException("Boom")
      val sink = ZSink.die(e)
      Stream(1, 1, 1, 1)
        .aggregate(sink)
        .runCollect
        .run
        .map(_ must_=== Exit.Failure(Cause.Die(e)))
    }

  def aggregateErrorPropagation2 = unsafeRun {
    val e = new RuntimeException("Boom")
    val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (_, _) =>
      ZIO.die(e)
    }

    Stream(1, 1)
      .aggregate(sink)
      .runCollect
      .run
      .map(_ must_=== Exit.Failure(Cause.Die(e)))
  }

  def aggregateInterruptionPropagation = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (acc, el) =>
        if (el == 1) UIO.succeed(ZSink.Step.more(el :: acc))
        else
          (latch.succeed(()) *> UIO.never)
            .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  def aggregateInterruptionPropagation2 = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.fromEffect {
        (latch.succeed(()) *> UIO.never)
          .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  def aggregateWithin = unsafeRun {
    for {
      result <- Stream(1, 1, 1, 1, 2)
                 .aggregateWithin(
                   Sink.fold(List[Int]())(
                     (acc, el: Int) =>
                       if (el == 1) ZSink.Step.more(el :: acc)
                       else if (el == 2 && acc.isEmpty) ZSink.Step.done(el :: acc, Chunk.empty)
                       else ZSink.Step.done(acc, Chunk.single(el))
                   ),
                   ZSchedule.spaced(30.minutes)
                 )
                 .runCollect
    } yield result must_=== List(Right(List(1, 1, 1, 1)), Right(List(2)))
  }

  private def aggregateWithinErrorPropagation1 =
    unsafeRun {
      val e    = new RuntimeException("Boom")
      val sink = ZSink.die(e)
      Stream(1, 1, 1, 1)
        .aggregateWithin(sink, Schedule.spaced(30.minutes))
        .runCollect
        .run
        .map(_ must_=== Exit.Failure(Cause.Die(e)))
    }

  private def aggregateWithinErrorPropagation2 = unsafeRun {
    val e = new RuntimeException("Boom")
    val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (_, _) =>
      ZIO.die(e)
    }

    Stream(1, 1)
      .aggregateWithin(sink, Schedule.spaced(30.minutes))
      .runCollect
      .run
      .map(_ must_=== Exit.Failure(Cause.Die(e)))
  }

  private def aggregateWithinInterruptionPropagation = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (acc, el) =>
        if (el == 1) UIO.succeed(ZSink.Step.more(el :: acc))
        else
          (latch.succeed(()) *> UIO.never)
            .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregateWithin(sink, Schedule.spaced(30.minutes)).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  private def aggregateWithinInterruptionPropagation2 = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.fromEffect {
        (latch.succeed(()) *> UIO.never)
          .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregateWithin(sink, Schedule.spaced(30.minutes)).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  private def bracket =
    unsafeRun(
      for {
        done           <- Ref.make(false)
        iteratorStream = Stream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(Stream.fromIterable)
        result         <- iteratorStream.run(Sink.collectAll[Int])
        released       <- done.get
      } yield (result must_=== List(0, 1, 2)) and (released must_=== true)
    )

  private def bracketShortCircuits =
    unsafeRun(
      for {
        done <- Ref.make(false)
        iteratorStream = Stream
          .bracket(UIO(0 to 3))(_ => done.set(true))
          .flatMap(Stream.fromIterable)
          .take(2)
        result   <- iteratorStream.run(Sink.collectAll[Int])
        released <- done.get
      } yield (result must_=== List(0, 1)) and (released must_=== true)
    )

  private def bracketNoAcquisition =
    unsafeRun(
      for {
        acquired       <- Ref.make(false)
        iteratorStream = (Stream(1) ++ Stream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
        _              <- iteratorStream.run(Sink.drain)
        result         <- acquired.get
      } yield result must_=== false
    )

  private def bracketWithDefects = unsafeRun {
    for {
      ref <- Ref.make(false)
      _ <- Stream
            .bracket(ZIO.unit)(_ => ref.set(true))
            .flatMap(_ => Stream.fromEffect(ZIO.dieMessage("boom")))
            .run(Sink.drain)
            .run
      released <- ref.get
    } yield released must_=== true
  }

  private def bufferStream = prop { list: List[Int] =>
    unsafeRunSync(
      Stream
        .fromIterable(list)
        .buffer(2)
        .run(Sink.collectAll[Int])
    ) must_== (Success(list))
  }

  private def bufferStreamError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e))
        .buffer(2)
        .run(Sink.collectAll[Int])
    ) must_== Failure(Cause.Fail(e))
  }

  private def bufferFastProducerSlowConsumer =
    unsafeRun(
      for {
        promise <- Promise.make[Nothing, Unit]
        ref     <- Ref.make(List[Int]())
        _ <- Stream
              .range(1, 5)
              .mapM(i => ref.update(i :: _) <* promise.succeed(()).when(i == 4))
              .buffer(2)
              .mapM(_ => IO.never)
              .runDrain
              .fork
        _    <- promise.await
        list <- ref.get
        // 1 element stuck in the second mapM, 2 elements buffered,
        // 1 element waiting to be enqueued to the buffer
      } yield list.reverse must_=== (1 to 4).toList
    )

  private def collect = {
    val s = Stream(Left(1), Right(2), Left(3)).collect {
      case Right(n) => n
    }

    slurp(s) must_=== Success(List(2)) and (slurp(s) must_=== Success(List(2)))
  }

  private def collectWhile = {
    val s = Stream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile { case Some(v) => v }
    slurp(s) must_=== Success(List(1, 2, 3))
  }

  private def collectWhileShortCircuits =
    unsafeRun {
      (Stream(Option(1)) ++ Stream.fail("Ouch")).collectWhile {
        case None => 1
      }.runDrain.either
        .map(_ must beRight(()))
    }

  private def concat =
    prop { (s1: Stream[String, Byte], s2: Stream[String, Byte]) =>
      val listConcat = (slurp(s1) zip slurp(s2)).map {
        case (left, right) => left ++ right
      }
      val streamConcat = slurp(s1 ++ s2)
      (streamConcat.succeeded && listConcat.succeeded) ==> (streamConcat must_=== listConcat)
    }

  private def concatFinalizerOrder =
    unsafeRun {
      for {
        log       <- Ref.make[List[String]](Nil)
        _         <- (Stream.finalizer(log.update("Second" :: _)) ++ Stream.finalizer(log.update("First" :: _))).runDrain
        execution <- log.get
      } yield execution must_=== List("Second", "First")
    }

  private def drain =
    unsafeRun(
      for {
        ref <- Ref.make(List[Int]())
        _   <- Stream.range(0, 10).mapM(i => ref.update(i :: _)).drain.run(Sink.drain)
        l   <- ref.get
      } yield l.reverse must_=== (0 to 10).toList
    )

  private def dropWhile =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      slurp(s.dropWhile(p)) must_=== slurp(s).map(_.dropWhile(p))
    }

  private def dropWhileShortCircuiting =
    unsafeRun {
      (Stream(1) ++ Stream.fail("Ouch"))
        .take(1)
        .dropWhile(_ => true)
        .runDrain
        .either
        .map(_ must beRight(()))
    }

  private def effectAsync =
    prop { list: List[Int] =>
      val s = Stream.effectAsync[Throwable, Int] { k =>
        list.foreach(a => k(Task.succeed(a)))
      }

      slurp(s.take(list.size)) must_=== Success(list)
    }

  private def effectAsyncM = {
    val list = List(1, 2, 3)
    unsafeRun {
      for {
        latch <- Promise.make[Nothing, Unit]
        fiber <- ZStream
                  .effectAsyncM[Any, Throwable, Int] { k =>
                    latch.succeed(()) *>
                      Task.succeedLazy {
                        list.foreach(a => k(Task.succeed(a)))
                      }
                  }
                  .take(list.size)
                  .run(Sink.collectAll[Int])
                  .fork
        _ <- latch.await
        s <- fiber.join
      } yield s must_=== list
    }
  }

  private def effectAsyncMaybeSome =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncMaybe[Throwable, Int] { _ =>
        Some(Stream.fromIterable(list))
      }

      slurp(s.take(list.size)) must_=== Success(list)
    }

  private def effectAsyncMaybeNone =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncMaybe[Throwable, Int] { k =>
        list.foreach(a => k(Task.succeed(a)))
        None
      }

      slurp(s.take(list.size)) must_=== Success(list)
    }

  private def effectAsyncInterruptLeft = unsafeRun {
    for {
      release <- zio.Promise.make[Nothing, Int]
      latch   = internal.OneShot.make[Unit]
      async = Stream
        .effectAsyncInterrupt[Nothing, Unit] { _ =>
          latch.set(()); Left(release.succeed(42).unit)
        }
        .run(Sink.collectAll[Unit])
      fiber  <- async.fork
      _      <- IO.effectTotal(latch.get(1000))
      _      <- fiber.interrupt.fork
      result <- release.await
    } yield result must_=== 42
  }

  private def effectAsyncInterruptRight =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncInterrupt[Throwable, Int] { _ =>
        Right(Stream.fromIterable(list))
      }

      slurp(s.take(list.size)) must_=== Success(list)
    }

  private def ensuring =
    unsafeRun {
      for {
        log <- Ref.make[List[String]](Nil)
        _ <- (for {
              _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
              _ <- Stream.fromEffect(log.update("Use" :: _))
            } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
        execution <- log.get
      } yield execution must_=== List("Ensuring", "Release", "Use", "Acquire")
    }

  private def finalizer =
    unsafeRun {
      for {
        log <- Ref.make[List[String]](Nil)
        _ <- (for {
              _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
              _ <- Stream.finalizer(log.update("Use" :: _))
            } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
        execution <- log.get
      } yield execution must_=== List("Ensuring", "Release", "Use", "Acquire")
    }

  private def filter =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      slurp(s.filter(p)) must_=== slurp(s).map(_.filter(p))
    }

  private def filterShortCircuiting1 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .filter(_ => true)
      .take(1)
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def filterShortCircuiting2 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .take(1)
      .filter(_ => true)
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def filterM =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      slurp(s.filterM(s => IO.succeed(p(s)))) must_=== slurp(s).map(_.filter(p))
    }

  private def filterMShortCircuiting1 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .take(1)
      .filterM(_ => UIO.succeed(true))
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def filterMShortCircuiting2 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .filterM(_ => UIO.succeed(true))
      .take(1)
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def flatMapStackSafety = {
    def fib(n: Int): Stream[Nothing, Int] =
      if (n <= 1) Stream.succeedLazy(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap { b =>
            Stream.succeedLazy(a + b)
          }
        }

    val stream   = fib(20)
    val expected = 6765

    slurp(stream).toEither must beRight(List(expected))
  }

  private def flatMapLeftIdentity =
    prop((x: Int, f: Int => Stream[String, Int]) => slurp(Stream(x).flatMap(f)) must_=== slurp(f(x)))

  private def flatMapRightIdentity =
    prop((m: Stream[String, Int]) => slurp(m.flatMap(i => Stream(i))) must_=== slurp(m))

  private def flatMapAssociativity =
    prop { (m: Stream[String, Int], f: Int => Stream[String, Int], g: Int => Stream[String, Int]) =>
      val leftStream  = m.flatMap(f).flatMap(g)
      val rightStream = m.flatMap(x => f(x).flatMap(g))
      slurp(leftStream) must_=== slurp(rightStream)
    }

  private def flatMapParConsistency = prop { (n: Long, m: List[Int]) =>
    val flatMap    = Stream.fromIterable(m).flatMap(i => Stream(i, i))
    val flatMapPar = Stream.fromIterable(m).flatMapPar(n)(i => Stream(i, i))

    (n > 0) ==> (slurp(flatMap).map(_.toSet) must_=== slurp(flatMapPar).map(_.toSet))
  }

  private def flatMapParShortCircuiting = unsafeRun {
    Stream
      .mergeAll(2)(
        Stream.never,
        Stream(1)
      )
      .take(1)
      .run(Sink.collectAll[Int])
      .map(_ must_=== List(1))
  }

  private def flatMapParInterruptionPropagation = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      fiber <- Stream(())
                .flatMapPar(1)(
                  _ => Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
                )
                .run(Sink.collectAll[Unit])
                .fork
      _         <- latch.await
      _         <- fiber.interrupt
      cancelled <- substreamCancelled.get
    } yield cancelled must_=== true
  }

  private def flatMapParInnerErrorsInterruptAllFibers = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- Stream(
                 Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
                 Stream.fromEffect(latch.await *> ZIO.fail("Ouch"))
               ).flatMapPar(2)(identity)
                 .run(Sink.drain)
                 .either
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must beLeft("Ouch"))
  }

  private def flatMapParFinalizerOrdering = unsafeRun {
    for {
      execution <- Ref.make[List[String]](Nil)
      inner = Stream
        .bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
      _ <- Stream
            .bracket(execution.update("OuterAcquire" :: _).const(inner))(_ => execution.update("OuterRelease" :: _))
            .flatMapPar(2)(identity)
            .runDrain
      results <- execution.get
    } yield results must_=== List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")
  }

  private def flatMapParOuterErrorsInterruptAllFibers = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.fail("Ouch")))
                 .flatMapPar(2) { _ =>
                   Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
                 }
                 .run(Sink.drain)
                 .either
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must beLeft("Ouch"))
  }

  private def flatMapParInnerDefectsInterruptAllFibers = unsafeRun {
    val ex = new RuntimeException("Ouch")

    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- Stream(
                 Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
                 Stream.fromEffect(latch.await *> ZIO.die(ex))
               ).flatMapPar(2)(identity)
                 .run(Sink.drain)
                 .run
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must_=== Exit.die(ex))
  }

  private def flatMapParOuterDefectsInterruptAllFibers = unsafeRun {
    val ex = new RuntimeException()

    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.die(ex)))
                 .flatMapPar(2) { _ =>
                   Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
                 }
                 .run(Sink.drain)
                 .run
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must_=== Exit.die(ex))
  }

  private def foreach = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1)

    unsafeRun(s.foreach[Any, Nothing](a => IO.effectTotal(sum += a)))
    sum must_=== 5
  }

  private def foreachWhile = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1, 1)

    unsafeRun(
      s.foreachWhile[Any, Nothing](
        a =>
          IO.effectTotal(
            if (sum >= 3) false
            else {
              sum += a;
              true
            }
          )
      )
    )
    sum must_=== 3
  }

  private def foreachWhileShortCircuits = unsafeRun {
    for {
      flag    <- Ref.make(true)
      _       <- (Stream(true, true, false) ++ Stream.fromEffect(flag.set(false)).drain).foreachWhile(ZIO.succeed)
      skipped <- flag.get
    } yield skipped must_=== true
  }

  private def forever = {
    var sum = 0
    val s = Stream(1).forever.foreachWhile[Any, Nothing](
      a =>
        IO.effectTotal {
          sum += a;
          if (sum >= 9) false else true
        }
    )

    unsafeRun(s)
    sum must_=== 9
  }

  private def fromChunk = prop { c: Chunk[Int] =>
    val s = Stream.fromChunk(c)
    (slurp(s) must_=== Success(c.toSeq.toList)) and (slurp(s) must_=== Success(c.toSeq.toList))
  }

  private def fromInputStream = unsafeRun {
    import java.io.ByteArrayInputStream
    val chunkSize = ZStreamChunk.DefaultChunkSize
    val data      = Array.tabulate[Byte](chunkSize * 5 / 2)(_.toByte)
    val is        = new ByteArrayInputStream(data)
    ZStream.fromInputStream(is, chunkSize).run(Sink.collectAll[Chunk[Byte]]) map { chunks =>
      chunks.flatMap(_.toArray[Byte]).toArray must_=== data
    }
  }

  private def fromIterable = prop { l: List[Int] =>
    val s = Stream.fromIterable(l)
    slurp(s) must_=== Success(l) and (slurp(s) must_=== Success(l))
  }

  private def fromQueue = prop { c: Chunk[Int] =>
    val result = unsafeRunSync {
      for {
        queue <- Queue.unbounded[Int]
        _     <- queue.offerAll(c.toSeq)
        fiber <- Stream
                  .fromQueue(queue)
                  .fold[Any, Nothing, Int, List[Int]]
                  .flatMap { fold =>
                    fold(List[Int](), _ => true, (acc, el) => IO.succeed(el :: acc))
                  }
                  .use(ZIO.succeed)
                  .map(_.reverse)
                  .fork
        _     <- waitForSize(queue, -1)
        _     <- queue.shutdown
        items <- fiber.join
      } yield items
    }
    result must_=== Success(c.toSeq.toList)
  }

  private def map =
    prop { (s: Stream[String, Byte], f: Byte => Int) =>
      slurp(s.map(f)) must_=== slurp(s).map(_.map(f))
    }

  private def mapAccum = {
    val stream = Stream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el))
    slurp(stream) must_=== Success(List(1, 2, 3))
  }

  private def mapAccumM = {
    val stream = Stream(1, 1, 1).mapAccumM[Any, Nothing, Int, Int](0)((acc, el) => IO.succeed((acc + el, acc + el)))
    (slurp(stream) must_=== Success(List(1, 2, 3))) and (slurp(stream) must_=== Success(List(1, 2, 3)))
  }

  private def mapConcat =
    prop { (s: Stream[String, Byte], f: Byte => Chunk[Int]) =>
      slurp(s.mapConcat(f)) must_=== slurp(s).map(_.flatMap(v => f(v).toSeq))
    }

  private def mapM = {
    implicit val arb: Arbitrary[IO[String, Byte]] = Arbitrary(genIO[String, Byte])

    prop { (data: List[Byte], f: Byte => IO[String, Byte]) =>
      unsafeRun {
        val s = Stream.fromIterable(data)

        for {
          l <- s.mapM(f).runCollect.either
          r <- IO.foreach(data)(f).either
        } yield l must_=== r
      }
    }
  }

  private def mapMPar = {
    implicit val arb: Arbitrary[IO[Unit, Byte]] = Arbitrary(genIO[Unit, Byte])

    prop { (data: List[Byte], f: Byte => IO[Unit, Byte]) =>
      unsafeRun {
        val s = Stream.fromIterable(data)

        for {
          l <- s.mapMPar(8)(f).runCollect.either
          r <- IO.foreachParN(8)(data)(f).either
        } yield l must_=== r
      }
    }
  }

  private def mapMParInterruptionPropagation = unsafeRun {
    for {
      interrupted <- Ref.make(false)
      latch       <- Promise.make[Nothing, Unit]
      fib <- Stream(())
              .mapMPar(1) { _ =>
                (latch.succeed(()) *> ZIO.never).onInterrupt(interrupted.set(true))
              }
              .runDrain
              .fork
      _      <- latch.await
      _      <- fib.interrupt
      result <- interrupted.get
    } yield result must_=== true
  }

  private def merge =
    prop { (s1: Stream[String, Int], s2: Stream[String, Int]) =>
      val mergedStream = slurp(s1 merge s2).map(_.toSet)
      val mergedLists  = (slurp(s1) zip slurp(s2)).map { case (left, right) => left ++ right }.map(_.toSet)
      (!mergedStream.succeeded && !mergedLists.succeeded) || (mergedStream must_=== mergedLists)
    }

  private def mergeEither = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeEither(s2)
    val list: List[Either[Int, Int]] = slurp(merge).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List(Left(1), Left(2), Right(1), Right(2)))
  }

  private def mergeWith = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)
    val list: List[String] = slurp(merge).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List("1", "2", "1", "2"))
  }

  private def mergeWithShortCircuit = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)
    val list: List[String] = slurp0(merge)(_ => false).toEither.fold(
      _ => List("9"),
      identity
    )

    list must_=== List()
  }

  private def mergeWithPrioritizesFailure = unsafeRun {
    val s1 = Stream.never
    val s2 = Stream.fail("Ouch")

    s1.mergeWith(s2)(_ => (), _ => ())
      .runCollect
      .either
      .map(_ must_=== Left("Ouch"))
  }

  private def peel = {
    val s      = Stream('1', '2', ',', '3', '4')
    val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink.collectAllWhile[Char](_ == ',')
    val peeled = s.peel(parser).use[Any, Int, (Int, Exit[Nothing, List[Char]])] {
      case (n, rest) =>
        IO.succeed((n, slurp(rest)))
    }

    unsafeRun(peeled) must_=== ((12, Success(List('3', '4'))))
  }

  private def range = {
    val s = Stream.range(0, 9)
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def repeat =
    unsafeRun(
      Stream(1)
        .repeat(Schedule.recurs(4))
        .run(Sink.collectAll[Int])
        .map(_ must_=== List(1, 1, 1, 1, 1))
    )

  private def repeatShortCircuits =
    unsafeRun(
      for {
        ref <- Ref.make[List[Int]](Nil)
        _ <- Stream
              .fromEffect(ref.update(1 :: _))
              .repeat(Schedule.spaced(10.millis))
              .take(2)
              .run(Sink.drain)
        result <- ref.get
      } yield result must_=== List(1, 1)
    )

  private def repeatEffect =
    unsafeRun(
      Stream
        .repeatEffect(IO.succeed(1))
        .take(2)
        .run(Sink.collectAll[Int])
        .map(_ must_=== List(1, 1))
    )

  private def repeatEffectWith =
    unsafeRun(
      for {
        ref <- Ref.make[List[Int]](Nil)
        _ <- Stream
              .repeatEffectWith(ref.update(1 :: _), Schedule.spaced(10.millis))
              .take(2)
              .run(Sink.drain)
        result <- ref.get
      } yield result must_=== List(1, 1)
    )

  private def spaced =
    unsafeRun(
      Stream("A", "B", "C")
        .spaced(Schedule.recurs(0) *> Schedule.fromFunction((_) => "!"))
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "!", "B", "!", "C", "!"))
    )

  private def spacedEither =
    unsafeRun(
      Stream("A", "B", "C")
        .spacedEither(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))
        .run(Sink.collectAll[Either[Int, String]])
        .map(_ must_=== List(Right("A"), Left(123), Right("B"), Left(123), Right("C"), Left(123)))
    )

  private def repeatedAndSpaced =
    unsafeRun(
      Stream("A", "B", "C")
        .spaced(Schedule.recurs(1) *> Schedule.fromFunction((_) => "!"))
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "A", "!", "B", "B", "!", "C", "C", "!"))
    )

  private def spacedShortCircuitsAfterScheduleFinished =
    unsafeRun(
      Stream("A", "B", "C")
        .spaced(Schedule.recurs(1) *> Schedule.fromFunction((_) => "!"))
        .take(3)
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "A", "!"))
    )

  private def spacedShortCircuitsWhileInSchedule =
    unsafeRun(
      Stream("A", "B", "C")
        .spaced(Schedule.recurs(1) *> Schedule.fromFunction((_) => "!"))
        .take(4)
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "A", "!", "B"))
    )

  private def take =
    prop { (s: Stream[String, Byte], n: Int) =>
      val takeStreamesult = slurp(s.take(n))
      val takeListResult  = slurp(s).map(_.take(n))
      (takeListResult.succeeded ==> (takeStreamesult must_=== takeListResult))
    }

  private def takeShortCircuits =
    unsafeRun(
      for {
        ran    <- Ref.make(false)
        stream = (Stream(1) ++ Stream.fromEffect(ran.set(true)).drain).take(0)
        _      <- stream.run(Sink.drain)
        result <- ran.get
      } yield result must_=== false
    )

  private def take0ShortCircuitsStreamNever =
    unsafeRun(
      for {
        units <- Stream.never.take(0).run(Sink.collectAll[Unit])
      } yield units must_=== List()
    )

  private def take1ShortCircuitsStreamNever =
    unsafeRun(
      for {
        ints <- (Stream(1) ++ Stream.never).take(1).run(Sink.collectAll[Int])
      } yield ints must_=== List(1)
    )

  private def takeWhile =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      val streamTakeWhile = slurp(s.takeWhile(p))
      val listTakeWhile   = slurp(s).map(_.takeWhile(p))
      listTakeWhile.succeeded ==> (streamTakeWhile must_=== listTakeWhile)
    }

  private def takeWhileShortCircuits =
    unsafeRun(
      (Stream(1) ++ Stream.fail("Ouch"))
        .takeWhile(_ => false)
        .runDrain
        .either
        .map(_ must beRight(()))
    )

  private def tap = {
    var sum     = 0
    val s       = Stream(1, 1).tap[Any, Nothing](a => IO.effectTotal(sum += a))
    val slurped = slurp(s)

    (slurped must_=== Success(List(1, 1))) and (sum must_=== 2)
  }

  private def throttleEnforceFreeElements = unsafeRun {
    Stream(1, 2, 3, 4)
      .throttleEnforce(0, Duration.Infinity)(_ => 0)
      .runCollect must_=== List(1, 2, 3, 4)
  }

  private def throttleEnforceNoBandwidth = unsafeRun {
    Stream(1, 2, 3, 4)
      .throttleEnforce(0, Duration.Infinity)(_ => 1)
      .runCollect must_=== List()
  }

  private def throttleEnforceShortCircuits = {
    def delay(n: Int) = ZIO.sleep(5.milliseconds) *> UIO.succeed(n)

    unsafeRun {
      Stream(1, 2, 3, 4, 5)
        .mapM(delay)
        .throttleEnforce(2, Duration.Infinity)(_ => 1)
        .take(2)
        .runCollect must_=== List(1, 2)
    }
  }

  private def throttleShapeFreeElements = unsafeRun {
    Stream(1, 2, 3, 4)
      .throttleShape(1, Duration.Infinity)(_ => 0)
      .runCollect must_=== List(1, 2, 3, 4)
  }

  private def throttleShapeShortCircuits = unsafeRun {
    Stream(1, 2, 3, 4, 5)
      .throttleShape(2, Duration.Infinity)(_ => 1)
      .take(2)
      .runCollect must_=== List(1, 2)
  }

  private def toQueue = prop { c: Chunk[Int] =>
    val s = Stream.fromChunk(c)
    val result = unsafeRunSync {
      s.toQueue(1000).use { queue: Queue[Take[Nothing, Int]] =>
        waitForSize(queue, c.length + 1) *> queue.takeAll
      }
    }
    result must_=== Success(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End)
  }

  private def transduce = {
    val s          = Stream('1', '2', ',', '3', '4')
    val parser     = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink.collectAllWhile[Char](_ == ',')
    val transduced = s.transduce(parser)

    slurp(transduced) must_=== Success(List(12, 34))
  }

  private def transduceNoRemainder = {
    val sink = Sink.fold(100) { (s, a: Int) =>
      if (a % 2 == 0)
        ZSink.Step.more(s + a)
      else
        ZSink.Step.done(s + a, Chunk.empty)
    }
    val transduced = ZStream(1, 2, 3, 4).transduce(sink)

    slurp(transduced) must_=== Success(List(101, 105, 104))
  }

  private def transduceWithRemainder = {
    val sink = Sink.fold(0) { (s, a: Int) =>
      a match {
        case 1 => ZSink.Step.more(s + 100)
        case 2 => ZSink.Step.more(s + 100)
        case 3 => ZSink.Step.done(s + 3, Chunk(a + 1))
        case _ => ZSink.Step.done(s + 4, Chunk.empty)
      }
    }
    val transduced = ZStream(1, 2, 3).transduce(sink)

    slurp(transduced) must_=== Success(List(203, 4))
  }

  private def transduceSinkMore = {
    val sink = Sink.fold(0) { (s, a: Int) =>
      ZSink.Step.more(s + a)
    }
    val transduced = ZStream(1, 2, 3).transduce(sink)

    slurp(transduced) must_=== Success(List(1 + 2 + 3))
  }

  private def transduceManaged = {
    final class TestSink(ref: Ref[Int]) extends ZSink[Any, Throwable, Int, Int, List[Int]] {
      override type State = List[Int]

      override def extract(state: List[Int]): ZIO[Any, Throwable, List[Int]] = ZIO.succeed(state)

      override def initial: ZIO[Any, Throwable, ZSink.Step[List[Int], Nothing]] = ZIO.succeed(ZSink.Step.more(Nil))

      override def step(state: List[Int], a: Int): ZIO[Any, Throwable, ZSink.Step[List[Int], Int]] =
        for {
          i <- ref.get
          _ <- if (i != 1000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
        } yield ZSink.Step.done(List(a, a), Chunk.empty)
    }

    val stream = ZStream(1, 2, 3, 4)
    val test = for {
      resource <- Ref.make(0)
      sink     = ZManaged.make(resource.set(1000).const(new TestSink(resource)))(_ => resource.set(2000))
      result   <- stream.transduceManaged(sink).runCollect
      i        <- resource.get
      _        <- if (i != 2000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
    } yield result
    unsafeRunSync(test) must_=== Success(List(List(1, 1), List(2, 2), List(3, 3), List(4, 4)))
  }

  private def transduceManagedError = unsafeRun {
    val fail = "I'm such a failure!"
    val sink = ZManaged.fail(fail)
    ZStream(1, 2, 3).transduceManaged(sink).runCollect.either.map(_ must beLeft(fail))
  }

  private def unfold = {
    val s = Stream.unfold(0)(i => if (i < 10) Some((i, i + 1)) else None)
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def unfoldM = {
    val s = Stream.unfoldM(0)(i => if (i < 10) IO.succeed(Some((i, i + 1))) else IO.succeed(None))
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def unTake =
    unsafeRun(
      Stream
        .range(0, 10)
        .toQueue[Nothing, Int](1)
        .use { q =>
          Stream.fromQueue(q).unTake.run(Sink.collectAll[Int])
        }
        .map(_ must_=== (0 to 10).toList)
    )

  private def unTakeError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e))
        .toQueue[Throwable, Int](1)
        .use { q =>
          Stream.fromQueue(q).unTake.run(Sink.collectAll[Int])
        }
    ) must_== Failure(Cause.fail(e))
  }

  private def zipWith = {
    val s1     = Stream(1, 2, 3)
    val s2     = Stream(1, 2)
    val zipped = s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _)))

    slurp(zipped) must_=== Success(List(2, 4))
  }

  private def zipWithIndex =
    prop((s: Stream[String, Byte]) => slurp(s.zipWithIndex) must_=== slurp(s).map(_.zipWithIndex))

  private def zipWithIgnoreRhs = {
    val s1     = Stream(1, 2, 3)
    val s2     = Stream(1, 2)
    val zipped = s1.zipWith(s2)((a, _) => a)

    slurp(zipped) must_=== Success(List(1, 2, 3))
  }

  private def zipWithPrioritizesFailure = unsafeRun {
    Stream.never
      .zipWith(Stream.fail("Ouch"))((_, _) => None)
      .runCollect
      .either
      .map(_ must_=== Left("Ouch"))
  }
}
