---
id: overview_basics
title: "Basics"
---

To build a description of a command:

```scala mdoc:invisible
import zio.process._
import java.nio.charset.StandardCharsets
```

```scala mdoc:silent
val command = Command("cat", "file.txt")
```

`command.run` will return a handle to the process as `ZIO[Blocking, CommandError, Process]`. Alternatively, instead of
flat-mapping and calling methods on `Process`, there are convenience methods on `Command` itself for some common operations:

## Transforming output

### List of lines

To obtain the output as a list of lines with the type `ZIO[Blocking, CommandError, Chunk[String]]`

```scala mdoc:silent
command.lines
```

### Stream of lines

To obtain the output as a stream of lines with the type `ZStream[Blocking, CommandError, String]`

```scala mdoc:silent
command.linesStream
```

This is particularly useful when dealing with large files and so on as to not use an unbounded amount of memory.

### String

If you don't need a structured type, you can return the entire output as a plain string:

```scala mdoc:silent
command.string
```

This defaults to UTF-8. To use a different encoding, specify the charset:

```scala mdoc:silent
command.string(StandardCharsets.UTF_16)
```

### Exit code

When you don't care about the output (or there is no output), you can return just the exit code.

```scala mdoc:silent
command.exitCode
```

Note that `Command#exitCode` will return the exit code in the ZIO's success channel whether it's 0 or not.
If you want non-zero exit codes to be considered an error, use `Command#successfulExitCode` instead. This will
return a `CommandError.NonZeroErrorCode` in ZIO's error channel when the exit code is not 0:

```scala mdoc:silent
for {
  exitCode  <- Command("java", "--non-existent-flag").successfulExitCode
  // Won't reach this 2nd command since the previous command failed with `CommandError.NonZeroErrorCode`:
  exitCode2 <- Command("java", "--non-existent-flag").successfulExitCode
} yield ()
```

### Kill a process

If you want to kill a process before it's done terminating, you can use `kill` (the Unix SIGTERM equivalent) or
`killForcibly` (the Unix SIGKILL equivalent):

```scala mdoc:silent
for {
  process <- Command("long-running-process").run
  _       <- ZIO.sleep(5.seconds)
  _       <- process.kill
} yield ()
```

### Stream of bytes

If you need lower-level access to the output's stream of bytes, you can access them directly like so:

```scala mdoc:silent
command.stream
```

### Access stdout and stderr separately

There are times when you need to process the output of stderr as well.

```scala mdoc:silent
for {
  process <- Command("./some-process").run
  stdout  <- process.stdout.string
  stderr  <- process.stderr.string
  // ...
} yield ()
```

## Error handling

Errors are represented as `CommandError` in the error channel instead of `IOException`. Since `CommandError` is an ADT,
you can pattern match on it and handle specific cases rather than trying to parse the guts of `IOException.getMessage`
yourself.

For example, if you want to fallback to running a different program if it doesn't exist on the host machine, you can
match on `CommandError.ProgramNotFound`:

```scala mdoc:silent
Command("some-program-that-may-not-exit").string.catchSome {
  case CommandError.ProgramNotFound(_) => Command("fallback-program").string
}
```