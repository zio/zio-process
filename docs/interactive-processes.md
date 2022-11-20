---
id: interactive-processes
title: "Interactive Processes"
---

Sometimes you want to interact with a process in a back-and-forth manner by sending requests to the process and receiving responses back. For example, interacting with a repl-like process like `node -i`, `python -i`, etc. or an ssh server.

Here is an example of communicating with an interactive NodeJS shell:

```scala mdoc:invisible
import zio._
import zio.process._
import java.nio.charset.StandardCharsets
```

```scala mdoc:silent
for {
  commandQueue <- Queue.unbounded[Chunk[Byte]]
  process      <- Command("node", "-i").stdin(ProcessInput.fromQueue(commandQueue)).run
  sep          <- System.lineSeparator
  fib          <- process.stdout.linesStream.foreach { line =>
                    ZIO.sleep(1.second) *> // sleep in order to simulate processing ...
                        ZIO.debug(s"Response from REPL: $line")
                  }.fork
  _            <- commandQueue.offer(Chunk.fromArray(s"1+1${sep}".getBytes(StandardCharsets.UTF_8)))
  _            <- commandQueue.offer(Chunk.fromArray(s"2**8${sep}".getBytes(StandardCharsets.UTF_8)))
  _            <- commandQueue.offer(Chunk.fromArray(s"process.exit(0)${sep}".getBytes(StandardCharsets.UTF_8)))  
  _            <- fib.join  
} yield ()
```

You would probably want to create a helper for the repeated code, but this just a minimal example to help get you started.