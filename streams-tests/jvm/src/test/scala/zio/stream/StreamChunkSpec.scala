package zio.stream

import com.github.ghik.silencer.silent
import zio.random.Random
import zio.stream.StreamChunkUtils._
import zio.test.Assertion.{ equalTo, isFalse, succeeds }
import zio.test._
import zio._

import scala.{ Stream => _ }

object StreamChunkSpec
    extends ZIOBaseSpec(
      suite("StreamChunkSpec")(
        testM("StreamChunk.map") {
          checkM(chunksOfStrings, toBoolFn[Random with Sized, String]) { (s, f) =>
            for {
              res1 <- slurp(s.map(f))
              res2 <- slurp(s).map(_.map(f))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.filter") {
          checkM(chunksOfStrings, toBoolFn[Random with Sized, String]) { (s, p) =>
            for {
              res1 <- slurp(s.filter(p))
              res2 <- slurp(s).map(_.filter(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.filterNot") {
          checkM(chunksOfStrings, toBoolFn[Random with Sized, String]) { (s, p) =>
            for {
              res1 <- slurp(s.filterNot(p))
              res2 <- slurp(s).map(_.filterNot(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.mapConcatChunk") {
          val fn = Gen.function[Random with Sized, String, Chunk[Int]](smallChunks(intGen))
          checkM(pureStreamChunkGen(tinyChunks(stringGen)), fn) { (s, f) =>
            for {
              res1 <- slurp(s.mapConcatChunk(f))
              res2 <- slurp(s).map(_.flatMap(v => f(v).toSeq))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.mapConcat") {
          val fn = Gen.function[Random with Sized, String, Iterable[Int]](Gen.small(Gen.listOfN(_)(intGen)))
          checkM(pureStreamChunkGen(tinyChunks(stringGen)), fn) { (s, f) =>
            for {
              res1 <- slurp(s.mapConcat(f))
              res2 <- slurp(s).map(_.flatMap(v => f(v).toSeq))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.drop") {
          checkM(chunksOfStrings, intGen) { (s, n) =>
            for {
              res1 <- slurp(s.drop(n))
              res2 <- slurp(s).map(_.drop(n))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.take") {
          checkM(chunksOfStrings, intGen) { (s, n) =>
            for {
              res1 <- slurp(s.take(n))
              res2 <- slurp(s).map(_.take(n))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.dropWhile") {
          checkM(chunksOfStrings, toBoolFn[Random with Sized, String]) { (s, p) =>
            for {
              res1 <- slurp(s.dropWhile(p))
              res2 <- slurp(s).map(_.dropWhile(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.takeWhile") {
          checkM(chunksOfStrings, toBoolFn[Random with Sized, String]) { (s, p) =>
            for {
              res1 <- slurp(s.takeWhile(p))
              res2 <- slurp(s).map(_.takeWhile(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.mapAccum") {
          checkM(chunksOfInts) { s =>
            for {
              res1 <- slurp(s.mapAccum(0)((acc, el) => (acc + el, acc + el)))
              res2 <- slurp(s).map(_.scanLeft(0)((acc, el) => acc + el).drop(1))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.mapM") {
          checkM(chunksOfInts, Gen.function[Random, Int, Int](intGen)) { (s, f) =>
            for {
              res1 <- slurp(s.mapM(a => IO.succeed(f(a))))
              res2 <- slurp(s).map(_.map(f))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.++") {
          checkM(chunksOfStrings, chunksOfStrings) { (s1, s2) =>
            for {
              res1 <- slurp(s1).zipWith(slurp(s2))(_ ++ _)
              res2 <- slurp(s1 ++ s2)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.zipWithIndex") {
          checkM(chunksOfStrings) { s =>
            for {
              res1 <- slurp(s.zipWithIndex)
              res2 <- slurp(s).map(_.zipWithIndex)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.foreach0") {
          checkM(chunksOfInts, toBoolFn[Random, Int]) { (s, cont) =>
            for {
              acc <- Ref.make[List[Int]](Nil)
              res1 <- s.foreachWhile { a =>
                       if (cont(a))
                         acc.update(a :: _) *> IO.succeed(true)
                       else
                         IO.succeed(false)
                     }.flatMap(_ => acc.update(_.reverse))
              res2 <- slurp(s.takeWhile(cont)).map(_.toList)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.foreach") {
          checkM(chunksOfInts) { s =>
            for {
              acc  <- Ref.make[List[Int]](Nil)
              res1 <- s.foreach(a => acc.update(a :: _).unit).flatMap(_ => acc.update(_.reverse))
              res2 <- slurp(s).map(_.toList)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.monadLaw1") {
          val fn = Gen.function[Random with Sized, Int, StreamChunk[Nothing, Int]](chunksOfInts)
          checkM(intGen, fn) { (x, f) =>
            for {
              res1 <- slurp(ZStreamChunk.succeed(Chunk(x)).flatMap(f))
              res2 <- slurp(f(x))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.monadLaw2") {
          checkM(chunksOfInts) { m =>
            for {
              res1 <- slurp(m.flatMap(i => ZStreamChunk.succeed(Chunk(i))))
              res2 <- slurp(m)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.monadLaw3") {
          val otherInts1 = pureStreamChunkGen(tinyChunks(Gen.int(0, 100)))
          val otherInts2 = pureStreamChunkGen(tinyChunks(Gen.int(-100, -1)))
          val fn1        = Gen.function[Random with Sized, Int, StreamChunk[Nothing, Int]](otherInts1)
          val fn2        = Gen.function[Random with Sized, Int, StreamChunk[Nothing, Int]](otherInts2)
          checkSomeM(pureStreamChunkGen(tinyChunks(intGen)), fn1, fn2)(5) { (m, f, g) =>
            val leftStream: StreamChunk[Nothing, Int]  = m.flatMap(f).flatMap(g)
            val rightStream: StreamChunk[Nothing, Int] = m.flatMap(f(_).flatMap(g))

            for {
              res1 <- slurp(leftStream)
              res2 <- slurp(rightStream)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.tap") {
          checkM(chunksOfStrings) { s =>
            for {
              acc           <- Ref.make(List.empty[String])
              withoutEffect <- slurp(s).run
              tap           <- slurp(s.tap(a => acc.update(a :: _).unit)).run
              list          <- acc.get.run
            } yield {
              assert(withoutEffect, equalTo(tap)) && (assert(withoutEffect.succeeded, isFalse) || assert[
                Exit[Nothing, Seq[String]]
              ](withoutEffect, equalTo(list.map(_.reverse))))
            }
          }
        },
        testM("StreamChunk.fold") {
          checkM(chunksOfStrings, intGen, Gen.function[Random, (Int, String), Int](intGen)) { (s, zero, f0) =>
            val f = Function.untupled(f0)
            for {
              res1 <- s.fold(zero)(f)
              res2 <- slurp(s).map(_.foldLeft(zero)(f))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.foldWhileM") {
          checkM(
            chunksOfStrings,
            intGen,
            toBoolFn[Random, Int],
            Gen.function[Random, (Int, String), Int](intGen)
          ) { (s, zero, cont, f0) =>
            val f = Function.untupled(f0)
            for {
              res1 <- s.foldWhileM[Any, Nothing, String, Int](zero)(cont)((acc, a) => IO.succeed(f(acc, a)))
              res2 <- slurp(s).map(l => foldLazyList(l.toList, zero)(cont)(f))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.flattenChunks") {
          checkM(chunksOfStrings) { s =>
            for {
              res1 <- s.flattenChunks.fold[String, List[String]](Nil)((acc, a) => a :: acc).map(_.reverse)
              res2 <- slurp(s)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.collect") {
          checkM(
            pureStreamChunkGen(smallChunks(stringGen)),
            Gen.partialFunction[Random with Sized, String, String](Gen.anyString)
          ) { (s, p) =>
            for {
              res1 <- slurp(s.collect(p))
              res2 <- slurp(s).map(_.collect(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.toInputStream") {
          val orig1  = List(1, 2, 3).map(_.toByte)
          val orig2  = List(4).map(_.toByte)
          val stream = StreamChunk.fromChunks(Chunk.fromIterable(orig1), Chunk[Byte](), Chunk.fromIterable(orig2))
          @silent("Any")
          val inputStreamResult = stream.toInputStream.use { inputStream =>
            ZIO.succeed(
              Iterator
                .continually(inputStream.read)
                .takeWhile(_ != -1)
                .map(_.toByte)
                .toList
            )
          }
          assertM(inputStreamResult.run, succeeds(equalTo(orig1 ++ orig2)))
        }
      )
    )
