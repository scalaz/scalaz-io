package zio

import zio.test._
import zio.test.Assertion._
import zio.RefMSpecUtils._

object RefMSpec
    extends ZIOBaseSpec(
      suite("RefMSpec")(
        testM("get") {
          for {
            refM  <- RefM.make(current)
            value <- refM.get
          } yield assert(value, equalTo(current))
        },
        testM("set") {
          for {
            refM  <- RefM.make(current)
            _     <- refM.set(update)
            value <- refM.get
          } yield assert(value, equalTo(update))
        },
        testM("update") {
          for {
            refM  <- RefM.make(current)
            value <- refM.update(_ => IO.effectTotal(update))
          } yield assert(value, equalTo(update))
        },
        testM("update with failure") {
          for {
            refM  <- RefM.make[String](current)
            value <- refM.update(_ => IO.fail(failure)).run
          } yield assert(value, fails(equalTo(failure)))
        },
        testM("updateSome") {
          for {
            refM  <- RefM.make[State](Active)
            value <- refM.updateSome { case Closed => IO.succeed(Active) }
          } yield assert(value, equalTo[State](Active))
        },
        testM("updateSome twice") {
          for {
            refM   <- RefM.make[State](Active)
            value1 <- refM.updateSome { case Active => IO.succeed(Changed) }
            value2 <- refM.updateSome {
                       case Active  => IO.succeed(Changed)
                       case Changed => IO.succeed(Closed)
                     }
          } yield assert(value1, equalTo[State](Changed)) && assert(value2, equalTo[State](Closed))
        },
        testM("updateSome with failure") {
          for {
            refM  <- RefM.make[State](Active)
            value <- refM.updateSome { case Active => IO.fail(failure) }.run
          } yield assert(value, fails(equalTo(failure)))
        },
        testM("modify") {
          for {
            refM  <- RefM.make(current)
            r     <- refM.modify(_ => IO.effectTotal(("hello", update)))
            value <- refM.get
          } yield assert(r, equalTo("hello")) && assert(value, equalTo(update))
        },
        testM("modify with failure") {
          for {
            refM <- RefM.make[String](current)
            r    <- refM.modify(_ => IO.fail(failure)).run
          } yield assert(r, fails(equalTo(failure)))
        },
        testM("modify twice") {
          for {
            refM   <- RefM.make[State](Active)
            r1     <- refM.modifySome("doesn't change the state") { case Active => IO.succeed("changed" -> Changed) }
            value1 <- refM.get
            r2 <- refM.modifySome("doesn't change the state") {
                   case Active  => IO.succeed("changed" -> Changed)
                   case Changed => IO.succeed("closed"  -> Closed)
                 }
            value2 <- refM.get
          } yield assert(r1, equalTo("changed")) &&
            assert(value1, equalTo[State](Changed)) &&
            assert(r2, equalTo("closed")) &&
            assert(value2, equalTo[State](Closed))
        },
        testM("modifySome") {
          for {
            refM  <- RefM.make[State](Active)
            r     <- refM.modifySome("State doesn't change") { case Closed => IO.succeed("active" -> Active) }
            value <- refM.get
          } yield assert(r, equalTo("State doesn't change")) && assert(value, equalTo[State](Active))
        },
        testM("modifySome with failure not triggered") {
          for {
            refM  <- RefM.make[State](Active)
            r     <- refM.modifySome("State doesn't change") { case Closed => IO.fail(failure) }.orDieWith(new Exception(_))
            value <- refM.get
          } yield assert(r, equalTo("State doesn't change")) && assert(value, equalTo[State](Active))
        },
        testM("modifySome with failure") {
          for {
            refM  <- RefM.make[State](Active)
            value <- refM.modifySome("State doesn't change") { case Active => IO.fail(failure) }.run
          } yield assert(value, fails(equalTo(failure)))
        }
      )
    )

object RefMSpecUtils {

  val (current, update) = ("value", "new value")
  val failure           = "failure"

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State

}
