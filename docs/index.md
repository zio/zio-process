---
id: index
title:  "Introduction to ZIO Process"
sidebar_label: "ZIO Process"
---

[ZIO Process](https://zio.dev/zio-process) is a simple ZIO library for interacting with external processes and command-line programs.

@PROJECT_BADGES@

## Introduction

ZIO Process is backed by ZIO Streams, enabling you to work with processes that output gigabytes of data without worrying about exceeding memory constraints.

ZIO Process provides a principled way to call out to external programs from within a ZIO application while leveraging ZIO's capabilities like interruptions and offloading blocking operations to a separate thread pool. We don't need to worry about avoiding these common pitfalls as we would if we were to use Java's `ProcessBuilder` or the `scala.sys.process` API since it is already taken care of for you.

Key features of ZIO Process:
- **Deep ZIO Integration** — Leverages ZIO to handle interruption and offload blocking operations.
- **ZIO Streams** — Command output is available as streams of bytes or lines, so you can work with processes that output gigabytes of data without exceeding memory constraints.
- **Descriptive Errors** — Failures are reported as `CommandError` cases, such as a missing program or a non-zero exit code.
- **Piping** — A simple DSL pipes the output of one command into the input of another.
- **Interactive Processes** — Send requests to a running process and read its responses as they arrive.

## Installation

To use the library, add the following line to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %%% "zio-process" % "@VERSION@"
```

The [Getting Started](./getting-started.md) page walks through a first program, and [Platform Support](./platform-support.md) explains the differences between the JVM, Scala Native and Scala.js.

## Guides

- **[Running Commands and Reading Output](./running-commands.md)** — Describe a command, run it, and read its output.
- **[Input, Output and Redirects](./input-output.md)** — Set the environment, the working directory, and the streams.
- **[Piping](./piping.md)** — Connect one command's output to the next command's input.
- **[Interactive Processes](./interactive-processes.md)** — Talk to a process that reads requests and writes responses.
- **[Process Control](./process-control.md)** — Wait for, stop, and inspect running processes, and handle errors.
- **[Platform Support](./platform-support.md)** — Use the library on the JVM, Scala Native and Scala.js.

The [Reference](./reference.md) page lists the public types and methods.

## Example

Here is a simple example of using ZIO Process:

```scala mdoc:compile-only
import zio._
import zio.process.Command

import java.io.File

object ZIOProcessExample extends ZIOAppDefault {
  val myApp = for {
    fiber <- Command("dmesg", "--follow").linesStream
      .foreach(Console.printLine(_))
      .fork
    cpuModel <- (Command("cat", "/proc/cpuinfo") |
      Command("grep", "model name") |
      Command("head", "-n", "1") |
      Command("cut", "-d", ":", "-f", "2")).string
    _ <- Console.printLine(s"CPU Model: $cpuModel")
    _ <- (Command("pg_dump", "my_database") > new File("dump.sql")).exitCode
    _ <- fiber.join
  } yield ()

  override def run = myApp
}
```

This example runs on Linux, because it uses `dmesg` and `/proc/cpuinfo`.
