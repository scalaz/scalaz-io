package scalaz.zio

import org.scalacheck._
import org.specs2.ScalaCheck
import scalaz.zio.syntax._

class IOCreationEagerSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends AbstractRTSSpec
    with GenIO
    with ScalaCheck {
  import Prop.forAll

  def is = "IOEagerSyntaxSpec".title ^ s2"""
   Generate a String:
      `.succeed` extension method returns the same IO[Nothing, String] as `IO.succeed` does. $t1
   Generate a String:
      `.fail` extension method returns the same IO[String, Nothing] as `IO.fail` does. $t2
   Generate a String:
      `.ensure` extension method returns the same IO[E, Option[A]] => IO[E, A] as `IO.ensure` does. $t3
    """

  def t1 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.succeed
      b <- IO.succeed(str)
    } yield a must ===(b))
  }

  def t2 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.fail.attempt
      b <- IO.fail(str).attempt
    } yield a must ===(b))
  }

  def t3 = forAll(Gen.alphaStr) { str =>
    val ioSome = IO.succeed(Some(42))
    unsafeRun(for {
      a <- str.require(ioSome)
      b <- IO.require(str)(ioSome)
    } yield a must ===(b))
  }

}

class IOCreationLazySyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends AbstractRTSSpec
    with GenIO
    with ScalaCheck {
  import Prop.forAll

  def is = "IOLazySyntaxSpec".title ^ s2"""
   Generate a String:
      `.point` extension method returns the same IO[Nothing, String] as `IO.point` does. $t1
   Generate a String:
      `.sync` extension method returns the same IO[Nothing, String] as `IO.sync` does. $t2
   Generate a String:
      `.syncException` extension method returns the same IO[Exception, String] as `IO.syncException` does. $t3
   Generate a String:
      `.syncThrowable` extension method returns the same IO[Throwable, String] as `IO.syncThrowable` does. $t4
   Generate a String:
      `.syncCatch` extension method returns the same PartialFunction[Throwable, E] => IO[E, A] as `IO.syncThrowable` does. $t5
    """

  def t1 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.succeedLazy
      b <- IO.succeedLazy(lazyStr)
    } yield a must ===(b))
  }

  def t2 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.sync
      b <- IO.sync(lazyStr)
    } yield a must ===(b))
  }

  def t3 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.syncException
      b <- IO.syncException(lazyStr)
    } yield a must ===(b))
  }

  def t4 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.syncThrowable
      b <- IO.syncThrowable(lazyStr)
    } yield a must ===(b))
  }

  def t5 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    val partial: PartialFunction[Throwable, Int] = { case _: Throwable => 42 }
    unsafeRun(for {
      a <- lazyStr.syncCatch[Int](partial)
      b <- IO.syncCatch(lazyStr)(partial)
    } yield a must ===(b))
  }

}

class IOFlattenSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends AbstractRTSSpec
    with GenIO
    with ScalaCheck {
  import Prop.forAll

  def is = "IOFlattenSyntaxSpec".title ^ s2"""
   Generate a String:
      `.flatten` extension method on IO[E, IO[E, String] returns the same IO[E, String] as `IO.flatten` does. $t1
    """

  def t1 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      flatten1 <- IO.succeedLazy(IO.succeedLazy(str)).flatten
      flatten2 <- IO.flatten(IO.succeedLazy(IO.succeedLazy(str)))
    } yield flatten1 must ===(flatten2))
  }
}

class IOAbsolveSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends AbstractRTSSpec
    with GenIO
    with ScalaCheck {
  import Prop.forAll

  def is = "IOAbsolveSyntaxSpec".title ^ s2"""
   Generate a String:
      `.absolve` extension method on IO[E, Either[E, A]] returns the same IO[E, Either[E, String]] as `IO.absolve` does. $t1
    """

  def t1 = forAll(Gen.alphaStr) { str =>
    val ioEither: IO[Nothing, Either[Nothing, String]] = IO.succeed(Right(str))
    unsafeRun(for {
      abs1 <- ioEither.absolve
      abs2 <- IO.absolve(ioEither)
    } yield abs1 must ===(abs2))
  }
}

class IOUnsandboxSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends AbstractRTSSpec
    with GenIO
    with ScalaCheck {
  import Prop.forAll

  def is = "IOUnsandboxedSyntaxSpec".title ^ s2"""
   Generate a String:
      `.unsandboxed` extension method on IO[Either[List[Throwable], E], A] returns the same IO[E, A] as `IO.unsandbox` does. $t1
    """

  def t1 = forAll(Gen.alphaStr) { str =>
    val io = IO.sync(str).sandbox
    unsafeRun(for {
      unsandbox1 <- io.unsandbox
      unsandbox2 <- IO.unsandbox(io)
    } yield unsandbox1 must ===(unsandbox2))
  }
}

class IOIterableSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends AbstractRTSSpec
    with GenIO
    with ScalaCheck {
  def is       = "IOUnsandboxedSyntaxSpec".title ^ s2"""
   Generate an Iterable of Char:
      `.mergeAll` extension method returns the same IO[E, B] as `IO.mergeAll` does. $t1
    Generate an Iterable of Char:
      `.parAll` extension method returns the same IO[E, List[A]] as `IO.parAll` does. $t2
    Generate an Iterable of Char:
      `.forkAll` extension method returns the same IO[Nothing, Fiber[E, List[A]]] as `IO.forkAll` does. $t3
    Generate an Iterable of Char:
      `.sequence` extension method returns the same IO[E, List[A]] as `IO.sequence` does. $t4
    """
  val TestData = "supercalifragilisticexpialadocious".toList

  def t1 = {
    val ios                          = TestData.map(IO.succeed)
    val zero                         = List.empty[Char]
    def merger[A](as: List[A], a: A) = a :: as
    unsafeRun(for {
      merged1 <- ios.mergeAll(zero)(merger)
      merged2 <- IO.mergeAll(ios)(zero)(merger)
    } yield merged1 must ===(merged2))
  }

  def t2 = {
    val ios = TestData.map(IO.sync(_))
    unsafeRun(for {
      parAll1 <- ios.collectAllPar
      parAll2 <- IO.collectAllPar(ios)
    } yield parAll1 must ===(parAll2))
  }

  def t3 = {
    val ios: Iterable[IO[String, Char]] = TestData.map(IO.sync(_))
    unsafeRun(for {
      f1       <- ios.forkAll
      forkAll1 <- f1.join
      f2       <- IO.forkAll(ios)
      forkAll2 <- f2.join
    } yield forkAll1 must ===(forkAll2))
  }

  def t4 = {
    val ios = TestData.map(IO.sync(_))
    unsafeRun(for {
      sequence1 <- ios.collectAll
      sequence2 <- IO.collectAll(ios)
    } yield sequence1 must ===(sequence2))
  }
}

class IOSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "IOSyntaxSpec".title ^ s2"""
   Generate a String:
      `.raceAll` extension method returns the same IO[E, A] as `IO.raceAll` does. $t1
   Generate a String:
      `.supervice` extension method returns the same IO[E, A] as `IO.supervise` does. $t2
    """

  val TestData = "supercalifragilisticexpialadocious"

  def t1 = {
    val io  = IO.sync(TestData)
    val ios = List.empty[IO[Nothing, String]]
    unsafeRun(for {
      race1 <- io.raceAll(ios)
      race2 <- IO.raceAll(io, ios)
    } yield race1 must ===(race2))
  }

  def t2 = {
    val io = IO.sync(TestData)
    unsafeRun(for {
      supervise1 <- io.supervise
      supervise2 <- IO.supervise(io)
    } yield supervise1 must ===(supervise2))
  }
}

class IOTuplesSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "IOTupleSpec".title ^ s2"""
   Generate a Tuple2 of (Int, String):
      `.map2` extension method should combine them to an IO[E, Z] with a function (A, B) => Z. $t1
   Generate a Tuple3 of (Int, String, String):
      `.map3` extension method should combine them to an IO[E, Z] with a function (A, B, C) => Z. $t2
   Generate a Tuple4 of (Int, String, String, String):
      `.map4` extension method should combine them to an IO[E, C] with a function (A, B, C, D) => Z. $t3
    """

  def t1 = forAll(Gen.posNum[Int], Gen.alphaStr) { (int: Int, str: String) =>
    def f(i: Int, s: String): String = i.toString + s
    val ios                          = (IO.succeed(int), IO.succeed(str))
    unsafeRun(for {
      map2 <- ios.map2[String](f)
    } yield map2 must ===(f(int, str)))
  }

  def t2 = forAll(Gen.posNum[Int], Gen.alphaStr, Gen.alphaStr) { (int: Int, str1: String, str2: String) =>
    def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
    val ios                                       = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2))
    unsafeRun(for {
      map3 <- ios.map3[String](f)
    } yield map3 must ===(f(int, str1, str2)))
  }

  def t3 = forAll(Gen.posNum[Int], Gen.alphaStr, Gen.alphaStr, Gen.alphaStr) {
    (int: Int, str1: String, str2: String, str3: String) =>
      def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
      val ios                                                   = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2), IO.succeed(str3))
      unsafeRun(for {
        map4 <- ios.map4[String](f)
      } yield map4 must ===(f(int, str1, str2, str3)))
  }

}
