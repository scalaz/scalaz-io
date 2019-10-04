package zio.stream

import zio.Chunk
import zio.ZIOBaseSpec
import zio.random.Random
import zio.test._
import zio.test.Assertion.equalTo
import ChunkUtils._

case class Value(i: Int) extends AnyVal

object ChunkSpec
    extends ZIOBaseSpec(
      suite("ChunkSpec")(
        testM("apply") {
          check(chunkWithIndex(Gen.unit)) {
            case (chunk, i) =>
              assert(chunk.apply(i), equalTo(chunk.toSeq.apply(i)))
          }
        },
        testM("length") {
          check(largeChunks(intGen)) { chunk =>
            assert(chunk.length, equalTo(chunk.toSeq.length))
          }
        },
        testM("equality") {
          check(mediumChunks(intGen), mediumChunks(intGen)) { (c1, c2) =>
            assert(c1.equals(c2), equalTo(c1.toSeq.equals(c2.toSeq)))
          }
        },
        test("inequality") {
          assert(Chunk(1, 2, 3, 4, 5), Assertion.not(equalTo(Chunk(1, 2, 3, 4, 5, 6))))
        },
        testM("materialize") {
          check(mediumChunks(intGen)) { c =>
            assert(c.materialize.toSeq, equalTo(c.toSeq))
          }
        },
        testM("foldLeft") {
          val fn = Gen.function[Random with Sized, (String, Int), String](stringGen)
          check(stringGen, fn, smallChunks(intGen)) { (s0, f, c) =>
            assert(c.fold(s0)(Function.untupled(f)), equalTo(c.toArray.foldLeft(s0)(Function.untupled(f))))
          }
        },
        testM("map") {
          val fn = Gen.function[Random with Sized, Int, String](stringGen)
          check(smallChunks(intGen), fn) { (c, f) =>
            assert(c.map(f).toSeq, equalTo(c.toSeq.map(f)))
          }
        },
        testM("flatMap") {
          val fn = Gen.function[Random with Sized, Int, Chunk[Int]](smallChunks(intGen))
          check(smallChunks(intGen), fn) { (c, f) =>
            assert(c.flatMap(f).toSeq, equalTo(c.toSeq.flatMap(f.andThen(_.toSeq))))
          }
        },
        testM("filter") {
          val fn = Gen.function[Random with Sized, String, Boolean](Gen.boolean)
          check(mediumChunks(stringGen), fn) { (chunk, p) =>
            assert(chunk.filter(p).toSeq, equalTo(chunk.toSeq.filter(p)))
          }
        },
        testM("drop chunk") {
          check(largeChunks(intGen), intGen) { (chunk, n) =>
            assert(chunk.drop(n).toSeq, equalTo(chunk.toSeq.drop(n)))
          }
        },
        testM("take chunk") {
          check(chunkWithIndex(Gen.unit)) {
            case (c, n) =>
              assert(c.take(n).toSeq, equalTo(c.toSeq.take(n)))
          }
        },
        testM("dropWhile chunk") {
          check(mediumChunks(intGen), toBoolFn[Random, Int]) { (c, p) =>
            assert(c.dropWhile(p).toSeq, equalTo(c.toSeq.dropWhile(p)))
          }
        },
        testM("takeWhile chunk") {
          check(mediumChunks(intGen), toBoolFn[Random, Int]) { (c, p) =>
            assert(c.takeWhile(p).toSeq, equalTo(c.toSeq.takeWhile(p)))
          }
        },
        testM("toArray") {
          check(mediumChunks(intGen)) { c =>
            assert(c.toArray.toSeq, equalTo(c.toSeq))
          }
        },
        test("toArray for an empty Chunk of type String") {
          assert(Chunk.empty.toArray[String], equalTo(Array.empty[String]))
        },
        test("to Array for an empty Chunk using filter") {
          assert(Chunk(1).filter(_ == 2).map(_.toString).toArray[String], equalTo(Array.empty[String]))
        },
        testM("toArray with elements of type String") {
          check(mediumChunks(stringGen)) { c =>
            assert(c.toArray.toSeq, equalTo(c.toSeq))
          }
        },
        test("toArray for a Chunk of any type") {
          val v: Vector[Any] = Vector("String", 1, Value(2))
          assert(Chunk.fromIterable(v).toArray.toVector, equalTo(v))
        },
        test("collect for empty Chunk") {
          assert(Chunk.empty.collect { case _ => 1 } == Chunk.empty, Assertion.isTrue)
        },
        testM("foreach") {
          check(mediumChunks(intGen)) { c =>
            var sum = 0
            c.foreach(sum += _)

            assert(sum, equalTo(c.toSeq.sum))
          }
        },
        testM("concat chunk") {
          check(smallChunks(intGen), smallChunks(intGen)) { (c1, c2) =>
            assert((c1 ++ c2).toSeq, equalTo(c1.toSeq ++ c2.toSeq))
          }
        },
        test("chunk transitivity") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          val c3 = Chunk(1, 2, 3)
          assert(c1 == c2 && c2 == c3 && c1 == c3, Assertion.isTrue)
        },
        test("chunk symmetry") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 == c2 && c2 == c1, Assertion.isTrue)
        },
        test("chunk reflexivity") {
          val c1 = Chunk(1, 2, 3)
          assert(c1 == c1, Assertion.isTrue)
        },
        test("chunk negation") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 != c2 == !(c1 == c2), Assertion.isTrue)
        },
        test("chunk substitutivity") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 == c2 && c1.toString == c2.toString, Assertion.isTrue)
        },
        test("chunk consistency") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 == c2 && c1.hashCode == c2.hashCode, Assertion.isTrue)
        },
        test("nullArrayBug") {
          val c = Chunk.fromArray(Array(1, 2, 3, 4, 5))

          // foreach should not throw
          c.foreach(_ => ())

          assert(c.filter(_ => false).map(_ * 2).length, equalTo(0))
        },
        test("toArrayOnConcatOfSlice") {
          val onlyOdd: Int => Boolean = _ % 2 != 0
          val concat = Chunk(1, 1, 1).filter(onlyOdd) ++
            Chunk(2, 2, 2).filter(onlyOdd) ++
            Chunk(3, 3, 3).filter(onlyOdd)

          val array = concat.toArray

          assert(array, equalTo(Array(1, 1, 1, 3, 3, 3)))
        },
        test("toArrayOnConcatOfEmptyAndInts") {
          assert(Chunk.empty ++ Chunk.fromArray(Array(1, 2, 3)), equalTo(Chunk(1, 2, 3)))
        },
        test("filterConstFalseResultsInEmptyChunk") {
          assert(Chunk.fromArray(Array(1, 2, 3)).filter(_ => false), equalTo[Chunk[Int]](Chunk.empty))
        },
        test("def testzipAllWith") {
          assert(Chunk(1, 2, 3).zipAllWith(Chunk(3, 2, 1))(_ => 0, _ => 0)(_ + _), equalTo(Chunk(4, 4, 4))) &&
          assert(Chunk(1, 2, 3).zipAllWith(Chunk(3, 2))(_ => 0, _ => 0)(_ + _), equalTo(Chunk(4, 4, 0))) &&
          assert(Chunk(1, 2).zipAllWith(Chunk(3, 2, 1))(_ => 0, _ => 0)(_ + _), equalTo(Chunk(4, 4, 0)))
        }
      )
    )
