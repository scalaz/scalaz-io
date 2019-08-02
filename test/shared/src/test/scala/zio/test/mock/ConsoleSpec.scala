package zio.test.mock

import java.io.{ ByteArrayOutputStream, PrintStream }

import scala.concurrent.{ ExecutionContext, Future }

import zio._
import zio.test.mock.MockConsole.Data
import zio.test.TestUtils.label

object ConsoleSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(emptyOutput, "MockConsole outputs nothing"),
    label(putStr, "MockConsole writes to output"),
    label(putStrLn, "MockConsole writes line to output"),
    label(getStr1, "MockConsole reads from input"),
    label(getStr2, "MockConsole fails on empty input"),
    label(feedLine, "MockConsole feeds lines to input"),
    label(clearInput, "MockConsole clears lines from input"),
    label(clearOutput, "MockConsole clears lines from output")
  )

  def stream(): PrintStream = new PrintStream(new ByteArrayOutputStream())

  def emptyOutput =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        output      <- mockConsole.output
      } yield output.isEmpty
    )

  def putStr =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.putStr("First line")
        _           <- mockConsole.putStr("Second line")
        output      <- mockConsole.output
      } yield output == Vector("First line", "Second line")
    )

  def putStrLn =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.putStrLn("First line")
        _           <- mockConsole.putStrLn("Second line")
        output      <- mockConsole.output
      } yield output == Vector("First line\n", "Second line\n")
    )

  def getStr1 =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data(List("Input 1", "Input 2"), Vector.empty))
        input1      <- mockConsole.getStrLn
        input2      <- mockConsole.getStrLn
      } yield (input1 == "Input 1") && (input2 == "Input 2")
    )

  def getStr2 =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        failed      <- mockConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed.isLeft) && (message == "There is no more input left to read")
    )

  def feedLine =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.feedLines("Input 1", "Input 2")
        input1      <- mockConsole.getStrLn
        input2      <- mockConsole.getStrLn
      } yield (input1 == "Input 1") && (input2 == "Input 2")
    )

  def clearInput =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data(List("Input 1", "Input 2"), Vector.empty))
        _           <- mockConsole.clearInput
        failed      <- mockConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed.isLeft) && (message == "There is no more input left to read")
    )

  def clearOutput =
    unsafeRunToFuture(
      for {
        mockConsole <- MockConsole.makeMock(Data(List.empty, Vector("First line", "Second line")))
        _           <- mockConsole.clearOutput
        output      <- mockConsole.output
      } yield output == Vector.empty
    )
}
