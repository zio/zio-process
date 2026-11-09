---
id: input-output
title: "Input, Output and Redirects"
---

Every command has three standard streams: standard input (what the program reads), standard output, and standard error. This page covers how to set the environment and working directory, how to feed input to a program, and where its output goes. Each setting is a method on `Command` that returns a new command, so the original value is never changed.

```scala mdoc:silent
import zio._
import zio.process._
import zio.stream.ZStream

import java.io.File
import java.nio.charset.StandardCharsets
```

## Setting the Environment

`Command#env` takes a `Map` of environment variables to pass to the program:

```scala mdoc:compile-only
val withJavaHome: Command = Command("java", "-version").env(Map("JAVA_HOME" -> "/usr/lib/jvm/default"))
```

On the JVM and Native, these variables are added to the environment that the current program inherits. Calling `env` again replaces the earlier map, so chain your variables into one call.

On Scala.js, the map replaces the whole environment of the child process. A command that never calls `env` runs with an empty environment, with no `PATH`, so set every variable it needs explicitly on that platform.

## Setting the Working Directory

`Command#workingDirectory` sets the directory the program starts in. If the directory does not exist, the command fails with `CommandError.WorkingDirectoryMissing` before the program starts:

```scala mdoc:compile-only
val listRoot: ZIO[Any, CommandError, Chunk[String]] = Command("ls").workingDirectory(new File("/")).lines
```

On Scala.js, the `File` type is a `String` path, so the same call takes `"/"` instead of `new File("/")`.

## Feeding Standard Input

By default, a command inherits standard input from the current program. To send your own input, set `Command#stdin` to a `ProcessInput`. Each constructor on `ProcessInput` creates a different source.

### From a String

`Command#<<` sends a UTF-8 string to standard input. It is shorthand for `stdin(ProcessInput.fromUTF8String(...))`:

```scala mdoc:compile-only
val echoed: ZIO[Any, CommandError, String] = (Command("cat") << "hello from ZIO Process").string
```

To use another encoding, build the input with `ProcessInput.fromString`:

```scala mdoc:compile-only
val utf16Input: ProcessInput = ProcessInput.fromString("hello", StandardCharsets.UTF_16)
```

### From Bytes

`ProcessInput.fromByteArray` sends an array of bytes:

```scala mdoc:compile-only
val fromBytes: ZIO[Any, CommandError, String] =
  Command("cat").stdin(ProcessInput.fromByteArray("bytes".getBytes(StandardCharsets.UTF_8))).string
```

### From a File or Path

`ProcessInput.fromFile` streams a file into standard input without loading it into memory. `ProcessInput.fromPath` does the same with a `java.nio.file.Path`:

```scala mdoc:compile-only
val fromFile: ZIO[Any, CommandError, Chunk[String]] =
  Command("cat").stdin(ProcessInput.fromFile(new File("input.txt"))).lines
```

The `fromFile` method takes an optional chunk size on the JVM and Scala.js. On Scala Native it does not take one.

### From a Stream

`ProcessInput.fromStream` pipes a `ZStream` of bytes into standard input. This is how the output of one stream becomes the input of a process. Set `flushChunksEagerly` to `true` when the program reads its input one line at a time:

```scala mdoc:compile-only
val source: ZStream[Any, CommandError, Byte] = ZStream.fromChunk(Chunk.fromArray("streamed input".getBytes(StandardCharsets.UTF_8)))

val streamed: ZIO[Any, CommandError, String] =
  Command("cat").stdin(ProcessInput.fromStream(source, flushChunksEagerly = false)).string
```

Streaming standard input is tested on the JVM only, and the Native documentation warns that it can block the process. Scala Native users should prefer `ProcessInput.fromByteArray` or `ProcessInput.fromFile`. For a process that reads commands interactively, see [Interactive Processes](./interactive-processes.md).

## Choosing Where Standard Output Goes

By default, standard output is captured so the output methods can read it. `ProcessOutput` changes that. `ProcessOutput.Pipe` is the default, `ProcessOutput.Inherit` sends the output to the current program's terminal, and the two file options write it to a file.

### Redirecting to a File

`Command#>` overwrites the file, and `Command#>>` appends to it:

```scala mdoc:compile-only
val dump: ZIO[Any, CommandError, ExitCode] =
  (Command("ls", "-l") > new File("listing.txt")).exitCode

val append: ZIO[Any, CommandError, ExitCode] =
  (Command("ls", "-l") >> new File("listing.txt")).exitCode
```

These are shorthand for `Command#stdout` with `ProcessOutput.FileRedirect` or `ProcessOutput.FileAppendRedirect`. Write the full form when you need to redirect a stream other than standard output:

```scala mdoc:compile-only
val explicit: ZIO[Any, CommandError, ExitCode] =
  Command("ls", "-l").stdout(ProcessOutput.FileRedirect(new File("listing.txt"))).exitCode
```

With a file redirect, the output is not captured, so output methods such as `Command#string` have nothing to read on the JVM. Scala.js does not support reading a redirected stream either, so read the file after the command finishes instead.

## Choosing Where Standard Error Goes

`Command#stderr` sets the destination for standard error. It accepts the same `ProcessOutput` options as `Command#stdout`:

```scala mdoc:compile-only
val errorsToFile: ZIO[Any, CommandError, ExitCode] =
  Command("ls", "--non-existent-flag").stderr(ProcessOutput.FileRedirect(new File("errors.txt"))).exitCode
```

Use `ProcessOutput.Inherit` to print errors to the current terminal, which is useful for diagnostics that should be visible to the user.

## Merging Standard Error into Standard Output

`Command#redirectErrorStream` sends standard error into standard output, so one stream carries both. This is useful when a program writes its messages to both streams and you want them in order:

```scala mdoc:compile-only
val merged: ZIO[Any, CommandError, String] =
  Command("ls", "--non-existent-flag").redirectErrorStream(true).string
```

This setting is ignored on Scala.js. The Scala.js build does not read it, so standard error stays separate there.

## Inheriting All Standard Streams

`Command#inheritIO` sends standard input, standard output, and standard error to the current program's terminal, as if the command were typed in a shell. Use it for interactive programs that the user drives directly:

```scala mdoc:compile-only
val repl: ZIO[Any, CommandError, ExitCode] = Command("scala").inheritIO.exitCode
```

## Next Steps

- [Piping](./piping.md) connects the output of one command to the input of the next.
- [Process Control](./process-control.md) explains how to wait for, stop, and inspect a running process.
- [Reference](./reference.md) lists every `CommandError` case and the input and output types.
