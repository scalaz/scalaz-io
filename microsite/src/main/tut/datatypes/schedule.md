---
layout: docs
section: datatypes
title:  "Schedule"
---

# {{page.title}}

```tut:silent
import scalaz.zio._
```

Schedules allow you to define and compose flexible recurrence schedules, which can be used to repeat actions, or retry actions in the event of errors. Schedules are used in the following functions:

 * **Repetition**
   * `IO#repeat` — Repeats an effect until the schedule is done.
   * `IO#repeatOrElse` — Repeats an effect until the schedule is done, with a fallback for errors.
   * `IO#repeatOrElse0` — Repeats an effect until the schedule is done, with a more powerful fallback for errors.
 * **Retries**
   * `IO#retry` – Retries an effect until it succeeds.
   * `IO#retryOrElse` — Retries an effect until it succeeds, with a fallback for errors.
   * `IO#retryOrElse0` — Retries an effect until it succeeds, with a more powerful fallback for errors.

Schedules define stateful, possibly effectful, recurring schedules of events, and compose in a variety of ways.

A `Schedule[A, B]` consumes input values of type `A` (errors in the case of `retry`, or values in the case of `repeat`), and based on these values and internal state, decides whether to recur or conclude. Every decision is accompanied by a (possibly zero) delay, indicating how much time before the next recurrence, and an output value of type `B`.

# Base Schedules

```tut:invisible
import scalaz.zio.duration._
```

A schedule that always recurs:

```tut:silent
val forever = Schedule.forever
```

A schedule that never executes:

```tut:silent
val never = Schedule.never
```

A schedule that recurs 10 times:

```tut:silent
val upTo10 = Schedule.recurs(10)
```

A schedule that recurs every 10 milliseconds:

```tut:silent
val spaced = Schedule.spaced(10.milliseconds)
```

A schedule that recurs using exponential backoff:

```tut:silent
val exponential = Schedule.exponential(10.milliseconds)
```

A schedule that recurs using fibonacci backoff:

```tut:silent
val fibonacci = Schedule.fibonacci(10.milliseconds)
```

# Schedule Combinators

Applying random jitter to a schedule:

```tut:silent
val jitteredExp = Schedule.exponential(10.milliseconds).jittered
```

Modifies the delay of a schedule:

```tut:silent
val boosted = Schedule.spaced(1.second).delayed(_ + 100.milliseconds)
```

Combines two schedules sequentially, by following the first policy until it ends, and then following the second policy:

```tut:silent
val sequential = Schedule.recurs(10) andThen Schedule.spaced(1.second)
```

Combines two schedules through intersection, by recurring only if both schedules want to recur, using the maximum of the two delays between recurrences:

```tut:silent
val expUpTo10 = Schedule.exponential(1.second) && Schedule.recurs(10)
```

Combines two schedules through union, by recurring if either schedule wants to
recur, using the minimum of the two delays between recurrences:

```tut:silent
val expCapped = Schedule.exponential(100.milliseconds) || Schedule.spaced(1.second)
```
