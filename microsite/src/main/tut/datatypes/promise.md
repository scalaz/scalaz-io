---
layout: docs
section: datatypes
title:  "Promise"
---

# {{page.title}}

A `Promise` is a variable that can be set exactly once. The type signature of Promise is `Promise[E, A]`, where `E` is
used to indicate an error and `A` is used to indicate that a value has been successfully set.

Promises are used to build higher level concurrency primitives, and are often used in situations where multiple `Fiber`s
need to coordinate passing values to each other.

## Creation

Promises can be created using `Promise.make[E, A]`, which returns `UIO[Promise[E, A]]`. This is a description of creating a promise, but not the actual promise. Promises cannot be created outside of IO, because creating them involves allocating mutable memory, which is an effect and must be safely captured in IO.

## Operations

You can complete a `Promise[Exception, String]` named `p` successfully with a value using `p.succeed(...)`.
For example, `p.succeed("I'm done!")`. The act of completing a Promise results in an `UIO[Boolean]`, where
the `Boolean` represents whether the promise value has been set (`true`) or whether it was already set (`false`).
This is demonstrated below:

```tut:silent
import scalaz.zio._
import scalaz.zio.syntax._
```

```tut:silent
val ioPromise: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioBoolean: UIO[Boolean] = ioPromise.flatMap(promise => promise.succeed("I'm done"))
```

You can also signal failure using `fail(...)`. For example,

```tut:silent
val ioPromise: UIO[Promise[Exception, Nothing]] = Promise.make[Exception, Nothing]
val ioBoolean: UIO[Boolean] = ioPromise.flatMap(promise => promise.fail(new Exception("boom")))
```

To re-iterate, the `Boolean` tells us whether or not the operation took place successfully (`true`) i.e. the Promise
was set with the value or the error.

As an alternative to using `succeed(...)` or `fail(...)` you can also use `succeed(...)` with an `Exit[E, A]` where
`E` signals an error and `A` signals a successful value.

You can get a value from a Promise using `await`

```tut:silent
val ioPromise: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioGet: IO[Exception, String] = ioPromise.flatMap(promise => promise.await)
```

The computation will suspend (in a non-blocking fashion) until the Promise is completed with a value or an error.
If you don't want to suspend and you only want to query the state of whether or not the Promise has been completed,
you can use `poll`:

```tut:silent
val ioPromise: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioIsItDone: UIO[Option[IO[Exception, String]]] = ioPromise.flatMap(p => p.poll)
val ioIsItDone2: IO[Unit, IO[Exception, String]] = ioPromise.flatMap(p => p.poll.get)
```

If the Promise was not completed when you called `poll` then the IO will fail with the `Unit` value otherwise,
you obtain an `IO[E, A]`, where `E` represents if the Promise completed with an error and `A` indicates
that the Promise successfully completed with an `A` value.

## Example Usage
Here is a scenario where we use a `Promise` to hand-off a value between two `Fiber`s

```tut:silent
import java.io.IOException
import scalaz.zio.console._
import scalaz.zio.duration._
import scalaz.zio.clock._

val program: ZIO[Console with Clock, IOException, Unit] = 
  for {
    promise         <-  Promise.make[Nothing, String]
    sendHelloWorld  =   (IO.succeed("hello world") <* sleep(1.second)).flatMap(promise.succeed)
    getAndPrint     =   promise.await.flatMap(putStrLn)
    fiberA          <-  sendHelloWorld.fork
    fiberB          <-  getAndPrint.fork
    _               <-  (fiberA zip fiberB).join
    } yield ()
```

In the example above, we create a Promise and have a Fiber (`fiberA`) complete that promise after 1 second and a second
Fiber (`fiberB`) will call `await` on that Promise to obtain a `String` and then print it to screen. The example prints
`hello world` to the screen after 1 second. Remember, this is just a description of the program and not the execution
itself.
