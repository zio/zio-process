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


import scala.annotation.nowarn
import FilePlatformSpecific._
import zio.{Chunk, Exit, NonEmptyChunk, Ref, UIO, Unsafe, ZIO}

import java.lang.ProcessBuilder.Redirect
import scala.jdk.CollectionConverters._
import java.io.OutputStream
import zio.stream.ZSink

private[process] trait CommandPlatformSpecific {

  type JProcess = java.lang.Process

  @nowarn
  protected def checkDirectory(dir: File): Boolean = true

  private def adaptCommand(command: NonEmptyChunk[String]): NonEmptyChunk[String] = command

  @nowarn
  protected def build(c: Command.Standard, piping: Option[java.io.InputStream]): ZIO[Any, Throwable, Process] =
    ZIO.asyncInterrupt { cb =>
      val ref: Ref.Atomic[JProcess] = Ref.unsafe.make[JProcess](null)(Unsafe.unsafe)

      def unsafeRunCmd: Process = {
        val builder = new ProcessBuilder(adaptCommand(c.command): _*)
        builder.redirectErrorStream(c.redirectErrorStream)
        c.workingDirectory.foreach { dir =>
          if (!checkDirectory(dir)) throw CommandError.WorkingDirectoryMissing(dir)
          builder.directory(dir)
        }

        if (c.env.nonEmpty) {
          builder.environment().putAll(c.env.asJava)
        }

        c.stdin match {
          case ProcessInput.Inherit => builder.redirectInput(Redirect.INHERIT)
          case ProcessInput.Pipe    => builder.redirectInput(Redirect.PIPE)
          case _                    => ()
        }

        c.stdout match {
          case ProcessOutput.FileRedirect(file)       => builder.redirectOutput(Redirect.to(file))
          case ProcessOutput.FileAppendRedirect(file) => builder.redirectOutput(Redirect.appendTo(file))
          case ProcessOutput.Inherit                  => builder.redirectOutput(Redirect.INHERIT)
          case ProcessOutput.Pipe                     => builder.redirectOutput(Redirect.PIPE)
        }

        c.stderr match {
          case ProcessOutput.FileRedirect(file)       => builder.redirectError(Redirect.to(file))
          case ProcessOutput.FileAppendRedirect(file) => builder.redirectError(Redirect.appendTo(file))
          case ProcessOutput.Inherit                  => builder.redirectError(Redirect.INHERIT)
          case ProcessOutput.Pipe                     => builder.redirectError(Redirect.PIPE)
        }

        val jProcess = builder.start()
        ref.unsafe.set(jProcess)(Unsafe.unsafe)
        Process(jProcess)
      }

      val canceler: UIO[Unit] =
        ref.get.flatMap {
          case null    => Exit.unit
          case process => ZIO.attempt(process.destroy()).ignoreLogged
        }

      cb(ZIO.attempt(unsafeRunCmd))

      Left(canceler)
    }

  protected def connectStdin(process: Process, stdin: ProcessInput): ZIO[Any, CommandError, Unit] =
    stdin match {
      case ProcessInput.Inherit | ProcessInput.Pipe    => ZIO.unit
      case ProcessInput.JavaStream(input, flushChunks) =>
        ProcessPlatformSpecific.connectJavaStream(process, input, flushChunks)
      case ProcessInput.FromStream(input, flushChunks) =>
        for {
          outputStream <- ZIO.succeedBlocking(process.getOutputStream)
          sink          = if (flushChunks) fromOutputStreamFlushChunksEagerly(outputStream)
                          else Constructors.zsink(outputStream)
          _            <- input
                            .run(sink)
                            .ensuring(ZIO.succeed(outputStream.close()))
                            .forkDaemon
        } yield ()
    }

  private def fromOutputStreamFlushChunksEagerly(os: OutputStream): ZSink[Any, Throwable, Byte, Nothing, Unit] =
    ZSink.foreachChunk { (chunk: Chunk[Byte]) =>
      ZIO.attemptBlockingInterrupt {
        os.write(chunk.toArray)
        os.flush()
      }
    }

}
