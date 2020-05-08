package zio.stream

import ZStreamGen._

import zio._
import zio.test.Assertion._
import zio.test._

object ZTransducerSpec extends ZIOBaseSpec {
  import ZIOTag._

  val initErrorParser = ZTransducer.fromEffect(IO.fail("Ouch"))

  def run[R, E, I, O](parser: ZTransducer[R, E, I, O], input: List[Chunk[I]]): ZIO[R, E, List[O]] =
    ZStream.fromChunks(input: _*).transduce(parser).runCollect

  def spec = suite("ZTransducerSpec")(
    suite("Combinators")(
      suite("contramap")(
        testM("happy path") {
          val parser = ZTransducer.identity[Int].contramap[String](_.toInt)
          assertM(run(parser, List(Chunk("1"))))(equalTo(List(1)))
        },
        testM("error") {
          val parser = initErrorParser.contramap[String](_.toInt)
          assertM(run(parser, List(Chunk("1"))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramapM")(
        testM("happy path") {
          val parser = ZTransducer.identity[Int].contramapM[Any, Unit, String](s => UIO.succeed(s.toInt))
          assertM(run(parser, List(Chunk("1"))))(equalTo(List(1)))
        },
        testM("error") {
          val parser = initErrorParser.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
          assertM(run(parser, List(Chunk("1"))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("map")(
        testM("happy path") {
          val parser = ZTransducer.identity[Int].map(_.toString)
          assertM(run(parser, List(Chunk(1))))(equalTo(List("1")))
        },
        testM("error") {
          val parser = initErrorParser.map(_.toString)
          assertM(run(parser, List(Chunk(1))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("mapError")(
        testM("error") {
          val parser = initErrorParser.mapError(_ => "Error")
          assertM(run(parser, List(Chunk(1))).either)(isLeft(equalTo("Error")))
        }
      ) @@ zioTag(errors),
      suite("mapM")(
        testM("happy path") {
          val parser = ZTransducer.identity[Int].mapM[Any, Unit, String](n => UIO.succeed(n.toString))
          assertM(run(parser, List(Chunk(1))))(equalTo(List("1")))
        },
        testM("error") {
          val parser = initErrorParser.mapM[Any, String, String](n => UIO.succeed(n.toString))
          assertM(run(parser, List(Chunk(1))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("race")(
        testM("coherence") {
          val t1 = ZTransducer.fold[Int, List[Int]](Nil)(lst => lst.sum % 3 == 0)(_ :+ _).map(_.mkString)
          val t2 = ZTransducer.fold[Int, List[Int]](Nil)(lst => lst.sum % 2 == 0)(_ :+ _).map(_.mkString)
          val t  = t1.race(t2)
          checkM(
            tinyListOf(Gen.chunkOf(Gen.anyInt))
          ) {
            chunks =>
              val stream = ZStream.fromChunks(chunks: _*)
              for {
                lefts   <- stream.transduce(t1).runCollect
                rights  <- stream.transduce(t2).runCollect
                winners <- stream.transduce(t).runCollect
              } yield {
                val sideBySide =
                  winners.zipAll(lefts, "dummy1", "dummy2").zipAll(rights, ("dummy3", "dummy4"), "dummy5")
                assert(sideBySide)(forall(Assertion.assertion("winner is left or right")() {
                  case ((winner, l), r) => winner == l || winner == r
                }))
              }
          }
        },
        raceChunkingDependenceSuite
      ),
      suite("choose") {
        testM("works") {
          val t1                                                                                 = ZTransducer.collectAllN[Int](4)
          val t2                                                                                 = ZTransducer.collectAllN[String](2)
          val input                                                                              = ZStream(Right("a"), Left(1), Right("b"), Right("c"), Left(2), Left(3), Left(4), Left(5), Left(6))
          val t: ZTransducer[Any, Nothing, Either[Int, String], Either[List[Int], List[String]]] = t1.choose(t2)
          assertM(input.chunkN(1).transduce(t).runCollect)(equalTo(List(Right(List("a", "b")), Left(List(5, 6)))))
        }
      }
    ),
    suite("Constructors")(
      suite("collectAllN")(
        testM("happy path") {
          val parser = ZTransducer.collectAllN[Int](3)
          assertM(run(parser, List(Chunk(1, 2, 3, 4))))(equalTo(List(List(1, 2, 3), List(4))))
        },
        testM("empty list") {
          val parser = ZTransducer.collectAllN[Int](0)
          assertM(run(parser, List()))(equalTo(List(List())))
        }
      ),
      suite("collectAllToMapN")(
        testM("stop collecting when map size exceeds limit")(
          assertM(
            run(
              ZTransducer.collectAllToMapN[Int, Int](2)(_ % 3)(_ + _),
              List(Chunk(0, 1, 2))
            )
          )(equalTo(List(Map(0 -> 0, 1 -> 1), Map(2 -> 2))))
        ),
        testM("keep collecting as long as map size does not exceed the limit")(
          assertM(
            run(
              ZTransducer.collectAllToMapN[Int, Int](3)(_ % 3)(_ + _),
              List(
                Chunk(0, 1, 2),
                Chunk(3, 4, 5),
                Chunk(6, 7, 8, 9)
              )
            )
          )(equalTo(List(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15))))
        )
      ),
      testM("collectAllToSetN")(
        assertM(
          run(ZTransducer.collectAllToSetN[Int](3), List(Chunk(1, 2, 1), Chunk(2, 3, 3, 4)))
        )(equalTo(List(Set(1, 2, 3), Set(4))))
      ),
      testM("collectAllWhile") {
        val parser = ZTransducer.collectAllWhile[Int](_ < 5)
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = run(parser, input)
        assertM(result)(equalTo(List(List(3, 4), List(2, 3, 4), List(4, 3, 2))))
      },
      suite("fold")(
        testM("empty")(
          assertM(
            ZStream.empty
              .aggregate(ZTransducer.fold[Int, Int](0)(_ => true)(_ + _))
              .runCollect
          )(equalTo(List(0)))
        ),
        testM("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                       .aggregate(ZTransducer.foldM(0)(_ => true) { (_, a) =>
                         effects.update(a :: _) *> UIO.succeed(30)
                       })
                       .runCollect
              result <- effects.get
            } yield (exit, result)).run

          (assertM(run(empty))(succeeds(equalTo((List(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((List(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((List(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map {
            case (((r1, r2), r3), r4) => r1 && r2 && r3 && r4
          }
        }
      ),
      suite("foldM")(
        testM("empty")(
          assertM(
            ZStream.empty
              .aggregate(
                ZTransducer.foldM(0)(_ => true)((x, y: Int) => ZIO.succeed(x + y))
              )
              .runCollect
          )(equalTo(List(0)))
        ),
        testM("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                       .aggregate(ZTransducer.foldM(0)(_ => true) { (_, a) =>
                         effects.update(a :: _) *> UIO.succeed(30)
                       })
                       .runCollect
              result <- effects.get
            } yield exit -> result).run

          (assertM(run(empty))(succeeds(equalTo((List(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((List(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((List(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map {
            case (((r1, r2), r3), r4) => r1 && r2 && r3 && r4
          }
        }
      ),
      suite("foldWeighted/foldUntil")(
        testM("foldWeighted")(
          assertM(
            ZStream[Long](1, 5, 2, 3)
              .aggregate(
                ZTransducer.foldWeighted(List[Long]())((_, x: Long) => x * 2, 12)((acc, el) => el :: acc).map(_.reverse)
              )
              .runCollect
          )(equalTo(List(List(1L, 5L), List(2L, 3L))))
        ),
        suite("foldWeightedDecompose")(
          testM("foldWeightedDecompose")(
            assertM(
              ZStream(1, 5, 1)
                .aggregate(
                  ZTransducer
                    .foldWeightedDecompose(List[Int]())(
                      (_, i: Int) => i.toLong,
                      4,
                      (i: Int) =>
                        if (i > 1) Chunk(i - 1, 1)
                        else Chunk(i)
                    )((acc, el) => el :: acc)
                    .map(_.reverse)
                )
                .runCollect
            )(equalTo(List(List(1, 3), List(1, 1, 1))))
          ),
          testM("empty")(
            assertM(
              ZStream.empty
                .aggregate(
                  ZTransducer.foldWeightedDecompose[Int, Int](0)((_, x) => x.toLong, 1000, Chunk.single(_))(_ + _)
                )
                .runCollect
            )(equalTo(List(0)))
          )
        ),
        testM("foldWeightedM")(
          assertM(
            ZStream[Long](1, 5, 2, 3)
              .aggregate(
                ZTransducer
                  .foldWeightedM(List.empty[Long])((_, a: Long) => UIO.succeedNow(a * 2), 12)((acc, el) =>
                    UIO.succeedNow(el :: acc)
                  )
                  .map(_.reverse)
              )
              .runCollect
          )(equalTo(List(List(1L, 5L), List(2L, 3L))))
        ),
        suite("foldWeightedDecomposeM")(
          testM("foldWeightedDecomposeM")(
            assertM(
              ZStream(1, 5, 1)
                .aggregate(
                  ZTransducer
                    .foldWeightedDecomposeM(List.empty[Int])(
                      (_, i: Int) => UIO.succeedNow(i.toLong),
                      4,
                      (i: Int) => UIO.succeedNow(if (i > 1) Chunk(i - 1, 1) else Chunk(i))
                    )((acc, el) => UIO.succeedNow(el :: acc))
                    .map(_.reverse)
                )
                .runCollect
            )(equalTo(List(List(1, 3), List(1, 1, 1))))
          ),
          testM("empty")(
            assertM(
              ZStream.empty
                .aggregate(
                  ZTransducer.foldWeightedDecomposeM[Any, Nothing, Int, Int](0)(
                    (_, x) => ZIO.succeed(x.toLong),
                    1000,
                    x => ZIO.succeed(Chunk.single(x))
                  )((x, y) => ZIO.succeed(x + y))
                )
                .runCollect
            )(equalTo(List(0)))
          )
        ),
        testM("foldUntil")(
          assertM(
            ZStream[Long](1, 1, 1, 1, 1, 1)
              .aggregate(ZTransducer.foldUntil(0L, 3)(_ + _))
              .runCollect
          )(equalTo(List(3L, 3L)))
        ),
        testM("foldUntilM")(
          assertM(
            ZStream[Long](1, 1, 1, 1, 1, 1)
              .aggregate(ZTransducer.foldUntilM(0L, 3)((s, a) => UIO.succeedNow(s + a)))
              .runCollect
          )(equalTo(List(3L, 3L)))
        )
      ),
      testM("dropWhile")(
        assertM(
          ZStream(1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
            .aggregate(ZTransducer.dropWhile(_ < 3))
            .runCollect
        )(equalTo(List(3, 4, 5, 1, 2, 3, 4, 5)))
      ),
      suite("dropWhileM")(
        testM("happy path")(
          assertM(
            ZStream(1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
              .aggregate(ZTransducer.dropWhileM(x => UIO(x < 3)))
              .runCollect
          )(equalTo(List(3, 4, 5, 1, 2, 3, 4, 5)))
        )
        // testM("error")(
        //   assertM {
        //     (ZStream(1,2,3) ++ ZStream.fail("Aie") ++ ZStream(5,1,2,3,4,5))
        //       .aggregate(ZTransducer.dropWhileM(x => UIO(x < 3)))
        //       .either
        //       .runCollect
        //   }(equalTo(List(Right(3),Left("Aie"),Right(5),Right(1),Right(2),Right(3),Right(4),Right(5))))
        // )
      ),
      testM("fromFunction")(
        assertM(
          ZStream(1, 2, 3, 4, 5)
            .aggregate(ZTransducer.fromFunction[Int, String](_.toString))
            .runCollect
        )(equalTo(List("1", "2", "3", "4", "5")))
      ),
      testM("fromFunctionM")(
        assertM(
          ZStream("1", "2", "3", "4", "5")
            .transduce(ZTransducer.fromFunctionM[Any, Throwable, String, Int](s => Task(s.toInt)))
            .runCollect
        )(equalTo(List(1, 2, 3, 4, 5)))
      ),
      suite("splitLines")(
        testM("preserves data")(
          checkM(weirdStringGenForSplitLines) { lines =>
            val data = lines.mkString("\n")

            ZTransducer.splitLines.push.use { push =>
              for {
                result   <- push(Some(Chunk(data)))
                leftover <- push(None)
              } yield assert((result ++ leftover).toArray[String].mkString("\n"))(equalTo(lines.mkString("\n")))

            }
          }
        ),
        testM("preserves data in chunks") {
          checkM(weirdStringGenForSplitLines) { xs =>
            val data = Chunk.fromIterable(xs.sliding(2, 2).toList.map(_.mkString("\n")))
            val ys   = xs.headOption.map(_ :: xs.drop(1).sliding(2, 2).toList.map(_.mkString)).getOrElse(Nil)

            ZTransducer.splitLines.push.use { push =>
              for {
                result   <- push(Some(data))
                leftover <- push(None)
              } yield assert((result ++ leftover).toArray[String].toList)(equalTo(ys))

            }
          }
        },
        testM("handles leftovers") {
          ZTransducer.splitLines.push.use { push =>
            for {
              result   <- push(Some(Chunk("abc\nbc")))
              leftover <- push(None)
            } yield assert(result.toArray[String].mkString("\n"))(equalTo("abc")) && assert(
              leftover.toArray[String].mkString
            )(equalTo("bc"))
          }
        },
        testM("aggregates chunks") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("abc", "\n", "bc", "\n", "bcd", "bcd")))
              part2 <- push(None)
            } yield assert(part1 ++ part2)(equalTo(Chunk("abc", "bc", "bcdbcd")))
          }
        },
        testM("single newline edgecase") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("\n")))
              part2 <- push(None)
            } yield assert(part1 ++ part2)(equalTo(Chunk("")))
          }
        },
        testM("no newlines in data") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("abc", "abc", "abc")))
              part2 <- push(None)
            } yield assert(part1 ++ part2)(equalTo(Chunk("abcabcabc")))
          }
        },
        testM("\\r\\n on the boundary") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("abc\r", "\nabc")))
              part2 <- push(None)
            } yield assert(part1 ++ part2)(equalTo(Chunk("abc", "abc")))
          }
        }
      ),
      suite("splitOn")(
        testM("preserves data")(checkM(Gen.listOf(Gen.anyString.filter(!_.contains("|")).filter(_.nonEmpty))) { lines =>
          val data   = lines.mkString("|")
          val parser = ZTransducer.splitOn("|")
          assertM(run(parser, List(Chunk.single(data))))(equalTo(lines))
        }),
        testM("handles leftovers") {
          val parser = ZTransducer.splitOn("\n")
          assertM(run(parser, List(Chunk("ab", "c\nb"), Chunk("c"))))(equalTo(List("abc", "bc")))
        },
        testM("aggregates") {
          assertM(
            Stream("abc", "delimiter", "bc", "delimiter", "bcd", "bcd")
              .aggregate(ZTransducer.splitOn("delimiter"))
              .runCollect
          )(equalTo(List("abc", "bc", "bcdbcd")))
        },
        testM("single newline edgecase") {
          assertM(
            Stream("test")
              .aggregate(ZTransducer.splitOn("test"))
              .runCollect
          )(equalTo(List("")))
        },
        testM("no delimiter in data") {
          assertM(
            Stream("abc", "abc", "abc")
              .aggregate(ZTransducer.splitOn("hello"))
              .runCollect
          )(equalTo(List("abcabcabc")))
        },
        testM("delimiter on the boundary") {
          assertM(
            Stream("abc<", ">abc")
              .aggregate(ZTransducer.splitOn("<>"))
              .runCollect
          )(equalTo(List("abc", "abc")))
        }
      ),
      suite("utf8DecodeChunk")(
        testM("regular strings")(checkM(Gen.anyString) { s =>
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk.fromArray(s.getBytes("UTF-8"))))
              part2 <- push(None)
            } yield assert((part1 ++ part2).mkString)(equalTo(s))
          }
        }),
        testM("incomplete chunk 1") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xC2.toByte)))
              part2 <- push(Some(Chunk(0xA2.toByte)))
              part3 <- push(None)
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xC2.toByte, 0xA2.toByte))
            )
          }
        },
        testM("incomplete chunk 2") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xE0.toByte, 0xA4.toByte)))
              part2 <- push(Some(Chunk(0xB9.toByte)))
              part3 <- push(None)
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xE0.toByte, 0xA4.toByte, 0xB9.toByte))
            )
          }
        },
        testM("incomplete chunk 3") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte)))
              part2 <- push(Some(Chunk(0x88.toByte)))
              part3 <- push(None)
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte))
            )
          }
        },
        testM("chunk with leftover") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              _     <- push(Some(Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte, 0xF0.toByte, 0x90.toByte)))
              part2 <- push(None)
            } yield assert(part2.mkString)(
              equalTo(new String(Array(0xF0.toByte, 0x90.toByte), "UTF-8"))
            )
          }
        }
      )
    )
  )

  val weirdStringGenForSplitLines = Gen
    .listOf(Gen.string(Gen.printableChar).map(_.filterNot(c => c == '\n' || c == '\r')))
    .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)

  private def raceChunkingDependenceSuite = {
    def findLetter(c: Char): ZTransducer[Any, Nothing, String, String] =
      ZTransducer.identity[String].filter(_.contains(c))

    val input: ZStream[Any, Nothing, String] = ZStream("b", "a", "a", "b", "b", "b", "a", "a", "a", "a")
    val tr                                   = findLetter('a').race(findLetter('b'))
    suite("Winner depends on chunking (just describing current behavior)")(
      testM("case 1") {
        assertM(input.chunkN(1).transduce(tr).runCollect)(equalTo(List("b", "a", "b", "b", "a", "a")))
      },
      testM("case 2") {
        assertM(input.chunkN(3).transduce(tr).runCollect)(equalTo(List("a", "a", "b", "b", "a", "a")))
      },
      testM("case 3") {
        assertM(input.chunkN(100).transduce(tr).runCollect)(equalTo(List("a", "a", "a", "a", "a", "a")))
      }
    )
  }
}
