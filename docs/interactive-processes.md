---
id: interactive-processes
title: "Interactive Processes"
---

Sometimes you want to interact with a process in a back-and-forth manner by sending requests to the process and receiving responses back. For example, interacting with a repl-like process like `node -i`, `python -i`, etc. or an ssh server.

Here is an example of communicating with an interactive Python shell:

```scala mdoc:invisible
import zio._
import zio.process._
import java.nio.charset.StandardCharsets
```

```scala mdoc:silent
for {
  commandQueue <- Queue.unbounded[Chunk[Byte]]
  process      <- Command("python", "-qi").stdin(ProcessInput.fromQueue(commandQueue)).run
  _            <- process.stdout.linesStream.foreach { response =>
                    ZIO.debug(s"Response from REPL: $response")
                  }.forkDaemon
  _            <- commandQueue.offer(Chunk.fromArray("1+1\n".getBytes(StandardCharsets.UTF_8)))
  _            <- commandQueue.offer(Chunk.fromArray("2**8\n".getBytes(StandardCharsets.UTF_8)))
  _            <- commandQueue.offer(Chunk.fromArray("import random\nrandom.randint(1, 100)\n".getBytes(StandardCharsets.UTF_8)))
  _            <- commandQueue.offer(Chunk.fromArray("exit()\n".getBytes(StandardCharsets.UTF_8)))
} yield ()
```

You would probably want to create a helper for the repeated code, but this just a minimal example to help get you started.