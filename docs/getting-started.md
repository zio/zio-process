---
id: getting-started
title: "Getting Started"
---

This page adds ZIO Process to a project and runs a first program. ZIO Process works with Scala 2.12, 2.13 and 3, on the JVM and Scala.js. Scala Native is supported in the code, but its artifact is not published yet, as explained on [Platform Support](./platform-support.md).

## Installation

Add the library to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %%% "zio-process" % "@VERSION@"
```

The `%%%` operator selects the artifact for your platform. Use it even for JVM-only projects, so that the same line works on every platform the library supports.

## A First Program

To run an external program from a ZIO application, create a `Command`, run it, and print the result. The following app runs `echo` and prints its output:

```scala mdoc:compile-only
import zio._
import zio.process._

object HelloProcess extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Unit] =
    for {
      greeting <- Command("echo", "Hello from ZIO Process").string
      _        <- Console.print(greeting)
    } yield ()
}
```

`Command#string` runs the program and returns everything it wrote to standard output. Because the result is an ordinary ZIO effect, it can be combined with other effects, retried, or interrupted like any other.

## Where to Go Next

The guides cover each part of the library in order:

- [Running Commands and Reading Output](./running-commands.md) describes commands and the ways to read their output.
- [Input, Output and Redirects](./input-output.md) sets the environment, working directory, and standard streams.
- [Piping](./piping.md) connects commands together.
- [Interactive Processes](./interactive-processes.md) talks to programs that read requests and write responses.
- [Process Control](./process-control.md) stops, waits for, and inspects running processes.
- [Platform Support](./platform-support.md) lists the differences between platforms.

The [Reference](./reference.md) page lists every public type and method.
