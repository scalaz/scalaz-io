// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio {
  private[zio] type Callback[E, A] = ExitResult[E, A] => Unit

  type Canceler = ZIO[Any, Nothing, _]
  type FiberId  = Long

  type IO[E, A] = ZIO[Any, E, A]
  type Task[A]  = ZIO[Any, Throwable, A]
  type UIO[A]   = ZIO[Any, Nothing, A]

  object IO extends ZIOFunctions {
    type UpperE = Any
    type LowerR = Any
  }
  object Task extends ZIO_E_Throwable {
//    type UpperE = Throwable
    type LowerR = Any
  }
  object UIO extends ZIOFunctions {
    type UpperE = Nothing
    type LowerR = Any
  }
}
