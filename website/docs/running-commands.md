---
id: running-commands
title: "Running Commands and Reading Output"
---

ZIO Process runs external programs as ZIO effects. This page shows how to describe a command, run it, and read what it prints. Each section builds on the one before it.

## Describing a Command

A `Command` is a value that holds a program name, its arguments, and how it should run. Creating one does not start a process:

```scala
import zio._
import zio.process._
import zio.stream.ZStream

val listFiles = Command("ls", "-l", "/tmp")
```

`Command.apply` takes the program name first, then any number of arguments as separate strings. Because no shell is involved, each argument reaches the program exactly as written, so spaces and quotes need no escaping.

## Running a Command

`Command#run` starts the process and returns a `Process` handle as soon as the process has started. The handle gives access to the process output and to methods that control it:

```scala
val started: ZIO[Any, CommandError, Process] = Command("ls", "-l").run
```

Most of the time you do not need the handle. `Command` has shortcuts that run the process and read its output in one call. The [Process Control](./process-control.md) page covers the handle in detail.

## Reading Output

The output shortcuts decode standard output as UTF-8 by default. Each one runs the command when the effect runs, so the program only starts when the effect is executed.

### As a List of Lines

`Command#lines` collects every line into a `Chunk[String]`:

```scala
val lines: ZIO[Any, CommandError, Chunk[String]] = Command("ls", "-1", ".").lines
```

### As a Stream of Lines

`Command#linesStream` emits each line as it arrives, so memory use stays bounded even when the output is very large. Use it for long-running commands or large output:

```scala
val streamed: ZStream[Any, CommandError, String] = Command("ls", "-1", ".").linesStream
```

### As a String

`Command#string` returns all of standard output as one string. To read it in another encoding, pass a `Charset`:

```scala
import java.nio.charset.StandardCharsets

val text: ZIO[Any, CommandError, String]      = Command("ls", "-l").string
val utf16: ZIO[Any, CommandError, String]     = Command("ls", "-l").string(StandardCharsets.UTF_16)
```

### As Raw Bytes

`Command#stream` gives direct access to the output as a stream of bytes, without decoding:

```scala
val bytes: ZStream[Any, CommandError, Byte] = Command("ls", "-l").stream
```

## Reading Standard Output and Standard Error Separately

Standard output and standard error are separate streams. The shortcuts above read only standard output. To read both, run the command to get the `Process` handle and read each stream from it:

```scala
val both: ZIO[Any, CommandError, (String, String)] =
  for {
    process <- Command("ls", "-l").run
    results <- process.stdout.string.zipPar(process.stderr.string)
  } yield results
```

The two reads run in parallel. Reading one stream to the end before starting the other can deadlock when the program writes a lot to the stream that is not being read yet.

:::warning[Unread standard error]
When you use the shortcuts, standard error is captured but never read. A program that writes a lot of error output can fill that buffer and stall. Redirect it with `Command#stderr` and a `ProcessOutput` option, as described in [Input, Output and Redirects](./input-output.md), or run the command with `Command#run` and read the `Process` handle's `stderr` stream.
:::

## Getting the Exit Code

`Command#exitCode` runs the command and returns its exit code, even when the code is not zero:

```scala
val code: ZIO[Any, CommandError, ExitCode] = Command("ls", "-l").exitCode
```

`Command#successfulExitCode` fails with `CommandError.NonZeroErrorCode` instead of returning a non-zero code. The [Process Control](./process-control.md) page explains both in detail.

## Running Commands in a Program

A command is a ZIO effect, so it composes with the rest of a program. The following app lists the current directory and prints how many entries it found:

```scala
object ListEntries extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Unit] =
    for {
      entries <- Command("ls", "-1", ".").lines
      _       <- Console.printLine(s"Found ${entries.length} entries")
    } yield ()
}
```

Because `listFiles` is only a description, the same value can run many times, each time starting a new process.

## Next Steps

- [Input, Output and Redirects](./input-output.md) sets the input, environment, working directory, and output destinations.
- [Piping](./piping.md) connects one command's output to the next command's input.
- [Process Control](./process-control.md) covers exit codes, interruption, killing processes, and errors.
