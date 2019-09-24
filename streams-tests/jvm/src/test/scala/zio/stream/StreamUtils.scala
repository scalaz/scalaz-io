package zio.stream

import zio.test.{ Gen, Sized }
import zio.random.Random
import zio._

trait StreamUtils extends ChunkUtils {
  def streamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    Gen.oneOf(failingStreamGen(a), pureStreamGen(a))

  def pureStreamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[Nothing, A]] =
    Gen.oneOf(
      Gen.const(Stream.empty),
      Gen.medium(Gen.listOfN(_)(a).map(Stream.fromIterable), 1)
    )

  def failingStreamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    Gen.medium(
      n =>
        for {
          i  <- Gen.int(0, n - 1)
          it <- Gen.listOfN(n)(a)
        } yield ZStream.unfoldM((i, it)) {
          case (_, Nil) | (0, _) =>
            IO.fail("fail-case")
          case (n, head :: rest) => IO.succeed(Some((head, (n - 1, rest))))
        },
      1
    )

  def pureStreamEffectGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, StreamEffect[Any, Nothing, A]] =
    Gen.small(Gen.listOfN(_)(a)).map(StreamEffect.fromIterable)

  def failingStreamEffectGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, StreamEffect[Any, String, A]] =
    for {
      n  <- Gen.int(1, 20)
      it <- Gen.listOfN(n)(a)
    } yield StreamEffect.unfold((n, it)) {
      case (_, Nil) | (0, _) => None
      case (n, head :: rest) => Some((head, (n - 1, rest)))
    }

}

object StreamUtils extends StreamUtils with GenUtils {
  val streamOfBytes   = pureStreamGen(Gen.anyByte)
  val streamOfInts    = pureStreamGen(intGen)
  val streamOfStrings = pureStreamGen(stringGen)

  val listOfInts = Gen.listOf(intGen)

  val pureStreamOfInts    = pureStreamGen(intGen)
  val pureStreamOfStrings = pureStreamGen(stringGen)
}
