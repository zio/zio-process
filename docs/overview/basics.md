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

`command.run` will return a handle to the process as `RIO[Blocking, Process]`. Alternatively, instead of flat-mapping
and calling methods on `Process`, there are convenience methods on `Command` itself for some common operations:

## Transforming output

### List of lines

To obtain the output as a list of lines with the type `RIO[Blocking, List[String]]`

```scala mdoc:silent
command.lines
```

### Stream of lines

To obtain the output as a stream of lines with the type `ZStream[Blocking, Throwable, String]`

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

### Stream of bytes (chunked)

If you need lower-level access to the output's stream of bytes, you can access them directly like so:

```scala mdoc:silent
command.stream
```

The bytes are chunked for performance in the form of `StreamChunk[Throwable, Byte]`