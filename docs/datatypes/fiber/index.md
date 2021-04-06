---
id: index
title: "Summary"
---

- **[Fiber](fiber.md)** — A fiber value models an `IO` value that has started running, and is the moral equivalent of a green thread.
- **[FiberRef](fiberref.md)** — `FiberRef[A]` models a mutable reference to a value of type `A`. As opposed to `Ref[A]`, a value is bound to an executing `Fiber` only.  You can think of it as Java's `ThreadLocal` on steroids.
- **[Fiber.Status](fiberstatus.md)** — `Fiber.Status` describe the current status of a Fiber.
- **[Fiber.Id](fiberid.md)** — `Fiber.Id` describe the unique identity of a Fiber.