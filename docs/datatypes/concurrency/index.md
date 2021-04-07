---
id: index
title: "Summary"
---

 - **[Hub](hub.md)** - A `Hub` is an asynchronous message hub that allows publishers to efficiently broadcast values to many subscribers.
- **[Promise](promise.md)** — A `Promise` is a model of a variable that may be set a single time, and awaited on by many fibers.
- **[Semaphore](semaphore.md)** — A `Semaphore` is an asynchronous (non-blocking) semaphore that plays well with ZIO's interruption.
- **[ZRef](zref.md)** — A `ZRef[EA, EB, A, B]` is a polymorphic, purely functional description of a mutable reference. The fundamental operations of a `ZRef` are `set` and `get`.
  + **[Ref](ref.md)** — `Ref[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which fills the `Ref` with a new value, and `get`, which retrieves its current content. All operations on a `Ref` are atomic and thread-safe, providing a reliable foundation for synchronizing concurrent programs.
- **[ZRefM](zrefm.md)** — A `ZRefM[RA, RB, EA, EB, A, B]` is a polymorphic, purely functional description of a mutable reference. 
  + **[RefM](refm.md)** — `RefM[A]` models a **mutable reference** to a value of type `A` in which we can store **immutable** data, and update it atomically **and** effectfully.
- **[Queue](queue.md)** — A `Queue` is an asynchronous queue that never blocks, which is safe for multiple concurrent producers and consumers.
