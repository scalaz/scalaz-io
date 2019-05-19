package scalaz.zio

class FiberRefSpec extends BaseCrossPlatformSpec {
  override def is = "FiberRefSpec".title ^ s2"""
   Create a new FiberRef with a specified value and check if:
      `get` returns the current value.                         $e1
      `get` returns the current value for a child.             $e2
      `set` updates the current value.                         $e3
      `set` by a child doesn't update parent's value.          $e4
      `locally` restores original value.                       $e5
      `locally` restores parent's value.                       $e6
      `locally` restores undefined value.                      $e7
      its value is inherited on join.                          $e8
      initial value is always available.                       $e9
      `update` changes value.                                  $e10
      `updateSome` changes value.                              $e11
      `updateSome` not changes value.                          $e12
      `modify` changes value.                                  $e13
      `modifySome` not changes value.                          $e14
    """

  val (initial, update) = ("initial", "update")

  def e1 =
    for {
      fiberRef <- FiberRef.make(initial)
      value    <- fiberRef.get
    } yield value must beTheSameAs(initial)

  def e2 =
    for {
      fiberRef <- FiberRef.make(initial)
      child    <- fiberRef.get.fork
      value    <- child.join
    } yield value must beTheSameAs(initial)

  def e3 =
    for {
      fiberRef <- FiberRef.make(initial)
      _        <- fiberRef.set(update)
      value    <- fiberRef.get
    } yield value must beTheSameAs(update)

  def e4 =
    for {
      fiberRef <- FiberRef.make(initial)
      promise  <- Promise.make[Nothing, Unit]
      _        <- (fiberRef.set(update) *> promise.succeed(())).fork
      _        <- promise.await
      value    <- fiberRef.get
    } yield value must beTheSameAs(initial)

  def e5 =
    for {
      fiberRef <- FiberRef.make(initial)
      local    <- fiberRef.locally(update)(fiberRef.get)
      value    <- fiberRef.get
    } yield (local must beTheSameAs(update)) and (value must beTheSameAs(initial))

  def e6 =
    for {
      fiberRef <- FiberRef.make(initial)
      child    <- fiberRef.locally(update)(fiberRef.get).fork
      local    <- child.join
      value    <- fiberRef.get
    } yield (local must beTheSameAs(update)) and (value must beTheSameAs(initial))

  def e7 =
    for {
      child <- FiberRef.make(initial).fork
      // Don't use join as it inherits values from child.
      fiberRef   <- child.await.flatMap(ZIO.done)
      localValue <- fiberRef.locally(update)(fiberRef.get)
      value      <- fiberRef.get
    } yield (localValue must beTheSameAs(update)) and (value must beTheSameAs(initial))

  def e8 =
    for {
      fiberRef <- FiberRef.make(initial)
      child    <- fiberRef.set(update).fork
      _        <- child.join
      value    <- fiberRef.get
    } yield value must beTheSameAs(update)

  def e9 =
    for {
      child    <- FiberRef.make(initial).fork
      fiberRef <- child.await.flatMap(ZIO.done)
      value    <- fiberRef.get
    } yield value must beTheSameAs(initial)

  def e10 =
    for {
      fiberRef <- FiberRef.make(initial)
      value1   <- fiberRef.update(_ => update)
      value2   <- fiberRef.get
    } yield (value1 must beTheSameAs(update)) and (value2 must beTheSameAs(update))

  def e11 =
    for {
      fiberRef <- FiberRef.make(initial)
      value1 <- fiberRef.updateSome {
                 case _ => update
               }
      value2 <- fiberRef.get
    } yield (value1 must beTheSameAs(update)) and (value2 must beTheSameAs(update))

  def e12 =
    for {
      fiberRef <- FiberRef.make(initial)
      value1 <- fiberRef.updateSome {
                 case _ if false => update
               }
      value2 <- fiberRef.get
    } yield (value1 must beTheSameAs(initial)) and (value2 must beTheSameAs(initial))

  def e13 =
    for {
      fiberRef <- FiberRef.make(initial)
      value1 <- fiberRef.modify {
                 case _ => (1, update)
               }
      value2 <- fiberRef.get
    } yield (value1 must beEqualTo(1)) and (value2 must beTheSameAs(update))

  def e14 =
    for {
      fiberRef <- FiberRef.make(initial)
      value1 <- fiberRef.modifySome(2) {
                 case _ if false => (1, update)
               }
      value2 <- fiberRef.get
    } yield (value1 must beEqualTo(2)) and (value2 must beTheSameAs(initial))
}
