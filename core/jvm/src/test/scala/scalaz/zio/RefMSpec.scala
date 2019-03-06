package scalaz.zio

class RefMSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "RefMSpec".title ^ s2"""
   Create a new RefM with a specified value and check if:
      `read` returns the current value.                                                                         $e1
      `write` puts the new value correctly.                                                                     $e2
      `update` changes the value and returns the updated value.                                                 $e3
      `updateSome` changes a given type State in some cases and returns the updated value.                      $e4
      `updateSome` returns the old value for an undefined State.                                                $e5
      `modify` changes the value and returns another value computed from the modification.                      $e6
      `modifySome` changes a given type State in some cases and returns a value computed from the modification. $e7
      `modifySome` returns a default value without modifying the State.                                         $e8
    """

  val (current, update) = ("value", "new value")

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State

  def e1 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        value <- refM.get
      } yield value must beTheSameAs(current)
    )

  def e2 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        _     <- refM.set(update)
        value <- refM.get
      } yield value must beTheSameAs(update)
    )

  def e3 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        value <- refM.update(_ => IO.effectTotal(update))
      } yield value must beTheSameAs(update)
    )

  def e4 =
    unsafeRun(
      for {
        refM   <- RefM.make[State](Active)
        value1 <- refM.updateSome { case Active => IO.succeed(Changed) }
        value2 <- refM.updateSome {
                   case Active  => IO.succeed(Changed)
                   case Changed => IO.succeed(Closed)
                 }
      } yield (value1 must beTheSameAs(Changed)) and (value2 must beTheSameAs(Closed))
    )

  def e5 =
    unsafeRun(
      for {
        refM  <- RefM.make[State](Active)
        value <- refM.updateSome { case Closed => IO.succeed(Active) }
      } yield value must beTheSameAs(Active)
    )

  def e6 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        r     <- refM.modify(_ => IO.effectTotal(("hello", update)))
        value <- refM.get
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )

  def e7 =
    unsafeRun(
      for {
        refM   <- RefM.make[State](Active)
        r1     <- refM.modifySome("doesn't change the state") { case Active => IO.succeed("changed" -> Changed) }
        value1 <- refM.get
        r2 <- refM.modifySome("doesn't change the state") {
               case Active  => IO.succeed("changed" -> Changed)
               case Changed => IO.succeed("closed"  -> Closed)
             }
        value2 <- refM.get
      } yield
        (r1 must beTheSameAs("changed")) and (value1 must beTheSameAs(Changed)) and (r2 must beTheSameAs("closed")) and (value2 must beTheSameAs(
          Closed
        ))
    )

  def e8 =
    unsafeRun(
      for {
        refM  <- RefM.make[State](Active)
        r     <- refM.modifySome("State doesn't change") { case Closed => IO.succeed("active" -> Active) }
        value <- refM.get
      } yield (r must beTheSameAs("State doesn't change")) and (value must beTheSameAs(Active))
    )
}
