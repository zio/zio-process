---
id: overview_index
title: "Contents"
---

ZIO Process - a purely functional command and process library based on ZIO.
Here's list of contents available:

 - **[Basics](basics.md)** — Creating a description of a command and transforming its output
 - **[Piping](piping.md)** — Creating a pipeline of commands
 - **[Other](other.md)** — Miscellaneous operations such as settings the working direction, inheriting I/O, etc.

## Installation

Include ZIO Process in your project by adding the following to your `build.sbt`:

```scala mdoc:passthrough
println(s"""```""")
if (zio.process.BuildInfo.isSnapshot)
  println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %% "zio-process" % "${zio.process.BuildInfo.version}"""")
println(s"""```""")
```
