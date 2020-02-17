package zio.test.mock

import zio.test.Assertion._
import zio.test._
import zio.{ Has, Layer, Ref, UIO, ZIO, ZLayer }

object SpySpec extends DefaultRunnableSpec {

  type Counter = Has[Counter.Service]

  object Counter {

    trait Service {
      def increment: UIO[Unit]
      def decrement: UIO[Unit]
      def get: UIO[Int]
      def reset: UIO[Unit]
    }

    final case class Live(counterState: Ref[Int]) extends Service {
      def increment: UIO[Unit] = counterState.update(_ + 1).unit
      def decrement: UIO[Unit] = counterState.update(_ - 1).unit
      def get: UIO[Int]        = counterState.get
      def reset: UIO[Unit]     = counterState.set(0)
    }

    val live: Layer[Nothing, Counter] =
      ZLayer.fromEffect(Ref.make(0).map(ref => Live(ref)))
  }

  sealed trait Command[A] extends Method[Counter, Unit, A]

  object Command {
    case object Increment extends Command[Unit]
    case object Decrement extends Command[Unit]
    case object Get       extends Command[Int]
    case object Reset     extends Command[Unit]

    val commands = Gen.elements(Increment, Decrement, Get, Reset)
  }

  implicit val spyableCounter: Spyable[Counter] =
    new Spyable[Counter] {
      def environment(mock: Mock): Counter =
        Has {
          new Counter.Service {
            def increment: UIO[Unit] = mock(Command.Increment)
            def decrement: UIO[Unit] = mock(Command.Decrement)
            def get: UIO[Int]        = mock(Command.Get)
            def reset: UIO[Unit]     = mock(Command.Reset)
          }
        }
      def mock(environment: Counter): Mock =
        new Mock {
          def invoke[RIn <: Has[_], ROut, I, E, A](method: Method[RIn, I, A], input: I): ZIO[ROut, E, A] =
            (method match {
              case Command.Increment => environment.get.increment
              case Command.Decrement => environment.get.decrement
              case Command.Get       => environment.get.get
              case Command.Reset     => environment.get.reset
            }).asInstanceOf[ZIO[ROut, E, A]]
        }
    }

  def testCounter(invocations: Iterable[Invocation[Counter, _, _]]): TestResult =
    invocations
      .foldLeft((0, assertCompletes)) {
        case ((state, assertion), Invocation(method, _, output)) =>
          method match {
            case Command.Increment => (state + 1, assertion)
            case Command.Decrement => (state - 1, assertion)
            case Command.Get       => (state, assertion && assert(output)(equalTo(state)))
            case Command.Reset     => (0, assertion)
          }
      }
      ._2

  def spec = suite("SpySpec") {
    testM("counter example") {
      checkM((Gen.listOf(Command.commands))) { commands =>
        Spyable.spyWithRef(Counter.live).flatMap {
          case (ref, layer) =>
            layer.build.use { counter =>
              for {
                _ <- ZIO.foreach(commands) {
                      case Command.Increment => counter.get.increment
                      case Command.Decrement => counter.get.decrement
                      case Command.Get       => counter.get.get
                      case Command.Reset     => counter.get.reset
                    }
                invocations <- ref.get
              } yield testCounter(invocations)
            }
        }
      }
    }
  }
}
