---
id: piping
title: "Piping"
---

Piping connects the standard output of one command to the standard input of the next, in the same way that `ps -ef | grep java` works in a shell. ZIO Process builds the pipeline as a value, so nothing starts until you run it. There is no shell involved, so each command in the pipeline is a program with its own arguments.

```scala
import zio._
import zio.process._

import java.io.File
```

## Building a Pipeline

`Command#|` joins two commands, and `Command#pipe` is the same operation with a name. To return the process IDs of all running Java processes, we pipe three commands together:

```scala
val javaProcessIds: ZIO[Any, CommandError, Chunk[String]] =
  (Command("ps", "-ef") | Command("grep", "java") | Command("awk", "{print $2}")).lines
```

The pipeline behaves as one command. Its methods, such as `lines`, `string` and `exitCode`, read from the rightmost command, and the output of each command feeds the next.

## Connecting Commands Manually

Under the hood, a pipe connects the stream of one command to the input of the next. You can make the same connection yourself with `ProcessInput.fromStream`, which is useful when the stream comes from somewhere other than a command:

```scala
val processes = Command("ps", "-ef").stream
val javaProcesses = Command("grep", "java").stdin(ProcessInput.fromStream(processes)).stream
val processIds = Command("awk", "{print $2}").stdin(ProcessInput.fromStream(javaProcesses)).lines
```

The operator form in the previous section does the same work and is easier to read.

## How Settings Apply to a Pipeline

A pipeline is a single `Command` value, so you can call the usual setting methods on it. Each setting reaches only the commands that need it:

| Setting               | Applies to                                                    |
| --------------------- | ------------------------------------------------------------- |
| `env`                 | Every command in the pipeline                                 |
| `workingDirectory`    | Every command in the pipeline                                 |
| `stdin`               | The leftmost command, because the others read from the pipe  |
| `stdout`              | The rightmost command, which produces the result              |
| `stderr`              | The rightmost command                                         |
| `redirectErrorStream` | The rightmost command                                         |

For example, `stdin` on a pipeline feeds the leftmost command, and the rightmost command's output is the result:

```scala
val javaLineCount: ZIO[Any, CommandError, String] =
  (Command("grep", "java") | Command("wc", "-l"))
    .stdin(ProcessInput.fromUTF8String("java\nrust\njava\n"))
    .string
```

Redirecting the output of a pipeline writes the rightmost command's output to the file:

```scala
val javaFile: ZIO[Any, CommandError, ExitCode] =
  (Command("ps", "-ef") | Command("grep", "java")).stdout(ProcessOutput.FileRedirect(new File("java-processes.txt"))).exitCode
```

For more on redirects, see [Input, Output and Redirects](./input-output.md).

## Platform Support

Three of the piping tests are skipped on Scala Native: the basic pipe, pipe associativity, and `stdin` on a piped command. The tests for how settings apply to a pipeline run on all three platforms. On Scala.js, `redirectErrorStream` is ignored, as described on [Input, Output and Redirects](./input-output.md).
