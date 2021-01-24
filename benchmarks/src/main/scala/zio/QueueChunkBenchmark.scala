package zio

import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
/**
 * This benchmark offers and takes a number of items in parallel, without back pressure.
 */
class QueueChunkBenchmark {

  val totalSize   = 100000
  val chunkSize   = 1000
  val parallelism = 5

  var zioQ: Queue[Int] = _
  val chunk: List[Int] = List.fill(chunkSize)(0)

  @Setup(Level.Trial)
  def createQueues(): Unit =
    zioQ = unsafeRun(Queue.bounded[Int](totalSize))

  @Benchmark
  def zioQueueSequential(): Int = {

    def repeat(task: UIO[Unit], max: Int): UIO[Unit] =
      if (max < 1) IO.unit
      else task.flatMap(_ => repeat(task, max - 1))

    val io = for {
      _ <- repeat(zioQ.offerAll(chunk).unit, totalSize / chunkSize)
      _ <- repeat(zioQ.takeUpTo(chunkSize).unit, totalSize / chunkSize)
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def zioQueueParallel(): Int = {

    val io = for {
      offers <- IO.forkAll(
                  List.fill(parallelism)(repeat(totalSize * 1 / chunkSize * 1 / parallelism)(zioQ.offerAll(chunk).unit))
                )
      takes <- IO.forkAll(
                 List.fill(parallelism)(
                   repeat(totalSize * 1 / chunkSize * 1 / parallelism)(zioQ.takeBetween(chunkSize, chunkSize).unit)
                 )
               )
      _ <- offers.join
      _ <- takes.join
    } yield 0

    unsafeRun(io)
  }
}
