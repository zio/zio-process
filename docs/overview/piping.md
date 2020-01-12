---
id: overview_piping
title: "Piping"
---

You can pipe the output of one process as the input to another. For example, if you want to return a list of all running
Java process IDs, you can do the following:

```scala mdoc:invisible
import zio.process._
```

### Manually

```scala mdoc:silent
for {
  processes     <- Command("ps", "-ef").stream
  javaProcesses <- Command("grep", "java").stdin(ProcessInput.fromStreamChunk(processes)).stream
  processIds    <- Command("awk", "{print $2}").stdin(ProcessInput.fromStreamChunk(javaProcesses)).lines
} yield processIds
```

### Using the pipe operator

Rather than connecting the outputs and inputs manually, you can use the `|` operator
(or its named equivalent, `pipe`) like so:

```scala mdoc:silent
(Command("ps", "-ef") | Command("grep", "java") | Command("awk", "{print $2}")).lines
```
