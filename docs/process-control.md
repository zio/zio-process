---
id: process-control
title: "Process Control"
---

Once a program is running, we may need to wait for it, stop it, check whether it is still alive, or find out why it failed. This page covers those tasks. The methods come in two places: the effects that `Command` provides, which manage the process for us, and the methods on the `Process` handle, which give us direct control.

```scala mdoc:silent
import zio._
import zio.process._

import zio.durationInt
```

## Stopping a Command with Interruption

When we run a command with `Command#exitCode` (or any other output method), the process belongs to the ZIO fiber that runs it. Interrupting that fiber stops the process, so we do not need to track the process ourselves:

```scala mdoc:compile-only
val interrupted: ZIO[Any, Nothing, Unit] =
  for {
    fiber <- Command("sleep", "20").exitCode.forkDaemon
    _     <- ZIO.sleep(5.seconds)
    _     <- fiber.interrupt
  } yield ()
```

Interrupting the fiber destroys the process it started, so the `sleep` process ends with it.

## Holding the Process Handle

Calling `Command#run` returns a `Process` handle as soon as the program starts. The handle is not tied to the fiber, so interrupting the fiber does not stop the program. Use the handle when you want to decide when to stop the program yourself:

```scala mdoc:compile-only
val killed: ZIO[Any, CommandError, Unit] =
  for {
    process <- Command("sleep", "20").run
    _       <- ZIO.sleep(5.seconds)
    _       <- process.kill
  } yield ()
```

`Process#kill` sends the terminate signal (SIGTERM on Unix) and waits until the program exits. `Process#killForcibly` is the forceful version, SIGKILL on Unix, and it also waits:

```scala mdoc:compile-only
val forced: ZIO[Any, CommandError, Unit] =
  for {
    process <- Command("sleep", "20").run
    _       <- process.killForcibly
  } yield ()
```

Use `kill` first, which lets the program clean up, and fall back to `killForcibly` only when a program does not stop.

## Checking Whether a Process Is Still Running

`Process#isAlive` returns `true` while the program has not exited:

```scala mdoc:compile-only
val running: ZIO[Any, CommandError, Boolean] =
  for {
    process <- Command("sleep", "20").run
    alive   <- process.isAlive
    _       <- process.kill
  } yield alive
```

On Scala.js, `isAlive` returns `true` until `kill` or `killForcibly` is called, even after the program has exited. The list in [Platform Support](#platform-support) below has the details.

## Reading the Process ID

`Process#pid` returns the operating system's process ID. Use it to log the process or to connect to it from another tool:

```scala mdoc:compile-only
val processId: ZIO[Any, CommandError, Long] =
  for {
    process <- Command("ls", "-l").run
    pid     <- process.pid
  } yield pid
```

## Stopping a Process Tree

A program often starts its own child processes. Stopping only the parent can leave those children running. On the JVM, `Process#killTree` stops the parent and all its descendants, and `Process#killTreeForcibly` does the same with SIGKILL. Both need JDK 9 or later and are not available on Scala.js or Scala Native:

```scala mdoc:compile-only
val treeStopped: ZIO[Any, CommandError, Unit] =
  for {
    process <- Command("sleep", "20").run
    _       <- process.killTree
  } yield ()
```

## Exit Codes

`Command#exitCode` and `Process#exitCode` return the exit code in the success channel, whether it is zero or not. This lets a program branch on the code without catching an error:

```scala mdoc:compile-only
val code: ZIO[Any, CommandError, ExitCode] = Command("ls", "--non-existent-flag").exitCode
```

`Command#successfulExitCode` and `Process#successfulExitCode` treat any non-zero code as an error. They fail with `CommandError.NonZeroErrorCode`, which carries the code:

```scala mdoc:compile-only
val checked: ZIO[Any, CommandError, ExitCode] =
  Command("ls", "--non-existent-flag").successfulExitCode.catchSome {
    case CommandError.NonZeroErrorCode(code) => ZIO.succeed(code)
  }
```

## Handling Errors

Every failure in the error channel is a `CommandError`, so we can match on the case we care about and handle the rest as usual. For example, to run a fallback program when the first one is not installed:

```scala mdoc:compile-only
val output: ZIO[Any, CommandError, String] =
  Command("some-program-that-may-not-exist").string.catchSome {
    case CommandError.ProgramNotFound(_) => Command("fallback-program").string
  }
```

`CommandError.WorkingDirectoryMissing` is the matching case for a working directory that does not exist. The [Reference](./reference.md) page lists every case.

:::warning[Timeouts]
Wrapping `Command#exitCode` in `ZIO#timeout` does not reliably stop the program when the timeout fires, so for a hard deadline, use `Command#run` and call `Process#kill` yourself.
:::

## Platform Support

The behavior of this page differs on each platform. Use the following list as a reminder, and see [Platform Support](./platform-support.md) for the full picture:

- `Process#killTree` and `Process#killTreeForcibly` exist only on the JVM.
- `CommandError.ProgramNotFound` and `CommandError.PermissionDenied` are not raised on Scala Native.
- On Scala.js, `Process#isAlive` reports `true` until `kill` or `killForcibly` is called, even after the program exits.
- On Scala.js, `Process#killForcibly` sends the same signal as `Process#kill`, not SIGKILL.
