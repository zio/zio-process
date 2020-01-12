---
id: about_index
title:  "About ZIO Process"
---

_Purely functional command and process library based on ZIO._

ZIO Process provides a principled way to call out to external programs from within a ZIO application
while leveraging ZIO's capabilities like interruption and offloading blocking operations to a
separate thread pool. You don't need to worry about avoiding these common pitfalls as you would if
you were to use Java's `ProcessBuilder` or the `scala.sys.process` API since it already taken care
of for you.
                
ZIO Process is backed by ZIO Streams, enabling you to work with processes that output gigabytes of
data without worrying about exceeding memory constraints.
