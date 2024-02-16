/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.process

import FilePlatformSpecific._
import ProcessPlatformSpecific._
import zio.{ NonEmptyChunk, ZIO }
import scala.annotation.nowarn
import scala.scalajs.js
import js.JSConverters._
import java.io.InputStream
import zio.process.ProcessInput.JavaStream
import zio.process.ProcessInput.FromStream

private[process] trait CommandPlatformSpecific {

  @nowarn
  protected def checkDirectory(dir: File): Boolean = true

  private def adaptCommand(command: NonEmptyChunk[String]): (String, js.Array[String]) =
    (command.head, command.tail.toJSArray)

  protected def build(c: Command.Standard, piping: Option[InputStream]): ZIO[Any, Throwable, Process] = ZIO.scoped {
    for {
      stdinStream <- c.stdin match {
                       case FromStream(stream, _) =>
                         stream.toInputStream.map(Some(_))
                       case JavaStream(in, _)     => ZIO.succeed(Some(in))
                       case _                     => ZIO.none
                     }
      promise     <- ZIO.attempt {
                       val childProcess    = scalajs.js.Dynamic.global.require("child_process")
                       val (command, args) = adaptCommand(c.command)

                       val fs    = scalajs.js.Dynamic.global.require("fs")
                       val stdin = piping match {
                         case Some(_) => "pipe"
                         case _       =>
                           c.stdin match {
                             case ProcessInput.Inherit => "inherit"
                             case ProcessInput.Pipe    => "pipe"
                             case _                    => "pipe"
                           }
                       }

                       val stdout = c.stdout match {
                         case ProcessOutput.FileRedirect(file)       =>
                           fs.createWriteStream(file, js.Dynamic.literal("flags" -> "w"))
                         case ProcessOutput.FileAppendRedirect(file) =>
                           fs.createWriteStream(file, js.Dynamic.literal("flags" -> "a"))
                         case ProcessOutput.Inherit                  => "inherit"
                         case ProcessOutput.Pipe                     => "pipe"
                       }

                       val stderr = c.stderr match {
                         case ProcessOutput.FileRedirect(file)       =>
                           fs.createWriteStream(file, js.Dynamic.literal("flags" -> "w"))
                         case ProcessOutput.FileAppendRedirect(file) =>
                           fs.createWriteStream(file, js.Dynamic.literal("flags" -> "a"))
                         case ProcessOutput.Inherit                  => "inherit"
                         case ProcessOutput.Pipe                     => "pipe"
                       }

                       val stdio = js.Array(stdin, stdout, stderr)

                       val dir = c.workingDirectory
                       val cwd = dir.map(FilePlatformSpecific.getAbsolute).getOrElse(js.Any.fromUnit(()))

                       val env = c.env.toJSDictionary

                       val opts = js.Dynamic.literal("cwd" -> cwd, "env" -> env, "stdio" -> stdio)

                       new js.Promise[Process]((resolve, reject) => {
                         val process    = childProcess.spawn(command, args, opts).asInstanceOf[JS.ChildProcess]
                         val zioProcess = Process(process)

                         def connectStdin(in: InputStream) = {
                           val arr = Array.ofDim[Byte](4096)

                           def write(continue: Boolean): Unit = {
                             val read = in.read(arr)
                             if (read > 0 && continue)
                               write(
                                 process.stdin.write(new js.typedarray.Uint8Array(arr.take(read).map(_.toShort).toJSArray))
                               )
                             else if (read == -1)
                               if (process.stdin != null) if (!process.stdin.writableEnded) { process.stdin.end(); () }
                           }

                           write(true)
                         }

                         stdinStream match {
                           case Some(s @ JSInputStream(in, true))  =>
                             process.stdout.pause()
                             process.on(
                               "spawn",
                               () =>
                                 in.on(
                                   "end",
                                   { () =>
                                     connectStdin(s)
                                     if (process.stdin != null) if (!process.stdin.writableEnded) process.stdin.end()
                                     resolve(zioProcess)
                                   }
                                 )
                             )
                           case Some(s @ JSInputStream(in, false)) =>
                             process.on("spawn", () => connectStdin(s))
                             in.on(
                               "end",
                               () => if (process.stdin != null) if (!process.stdin.writableEnded) process.stdin.end()
                             )
                             val node = js.Dynamic.global.require("process")
                             process.on("spawn", () => node.nextTick(() => resolve(zioProcess)))
                           case Some(in)                           =>
                             process.on("spawn", () => resolve(zioProcess))
                             process.on("spawn", () => connectStdin(in))
                           case None                               => process.on("spawn", () => resolve(zioProcess))
                         }
                         process.on("error", (err: js.Dynamic) => reject(err))
                       })

                     }
      p           <- ZIO.fromPromiseJS(promise)
    } yield p
  }

  @nowarn
  protected def connectStdin(process: Process, stdin: ProcessInput): ZIO[Any, CommandError, Unit] = ZIO.unit

}
