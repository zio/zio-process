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

import zio.ZIO.{ attemptBlockingCancelable, attemptBlockingInterrupt }
import zio.{ ExitCode, UIO, ZIO }

import java.lang.{ Process => JProcess }
import java.io.InputStream
import java.io.PushbackInputStream

final case class Process(private[process] val process: JProcess) extends ProcessPlatformSpecific { self =>

  /**
   * Access the standard output as an Java `InputStream`.
   */
  val stdoutJava: InputStream = process.getInputStream()

  private[process] def closeStdIn = ZIO.attempt {
    val out = process.getOutputStream()
    out.flush()
    out.close()
  }.mapError(CommandThrowable.classify)

  /**
   * Access the underlying Java Process wrapped in a blocking ZIO.
   */
  def execute[T](f: JProcess => T): ZIO[Any, CommandError, T] =
    attemptBlockingInterrupt(f(process)).refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Return the exit code of this process.
   */
  def exitCode: ZIO[Any, CommandError, ExitCode]              =
    attemptBlockingCancelable(ExitCode(process.waitFor()))(ZIO.succeed(process.destroy())).refineOrDie {
      case CommandThrowable.IOError(e) => e
    }

  /**
   * Tests whether the process is still alive (not terminated or completed).
   */
  def isAlive: UIO[Boolean] = ZIO.succeed(process.isAlive)

  /**
   * Kills the process and will wait until completed. Equivalent to SIGTERM on Unix platforms.
   */
  def kill: ZIO[Any, CommandError, Unit] =
    execute { process =>
      process.destroy()
      process.waitFor()
      ()
    }

  /**
   * Kills the process and will wait until completed. Equivalent to SIGKILL on Unix platforms.
   */
  def killForcibly: ZIO[Any, CommandError, Unit] =
    execute { process =>
      process.destroyForcibly()
      process.waitFor()
      ()
    }

  /**
   * Return the exit code of this process if it is zero. If non-zero, it will fail with `CommandError.NonZeroErrorCode`.
   */
  def successfulExitCode: ZIO[Any, CommandError, ExitCode] =
    attemptBlockingCancelable(ExitCode(process.waitFor()))(ZIO.succeed(process.destroy())).refineOrDie {
      case CommandThrowable.IOError(e) => e: CommandError
    }.filterOrElseWith(_ == ExitCode.success)(exitCode => ZIO.fail(CommandError.NonZeroErrorCode(exitCode)))

  /**
   * Connect a Java `InputStream` to the standard input of the process.
   */
  private[process] def connectJavaStream(stream: InputStream, flush: Boolean): ZIO[Any, CommandError, Unit] =
    for {
      outputStream <- self.execute(_.getOutputStream())
      s             = new PushbackInputStream(stream)
      _            <- loop {
                        val r = s.read()
                        if (r > 0) {
                          s.unread(r)
                          true
                        } else false
                      }(
                        for {
                          _ <- ZIO.attemptBlockingInterrupt {
                                 s.transferTo(outputStream)
                                 if (flush) outputStream.flush()
                               }.mapError(CommandThrowable.classify)
                        } yield ()
                      ).ensuring(ZIO.succeed {
                        s.close()
                        outputStream.close()
                      }).orDie
      _            <- ZIO.succeed(s.close())
      _            <- ZIO.succeed(stream.close())
      _            <- ZIO.succeed(outputStream.close())
    } yield ()

  private def loop[A](cond: => Boolean, i: Int = 0)(z: ZIO[Any, CommandError, A]): ZIO[Any, CommandError, Unit] =
    for {
      bool <- ZIO.attemptBlockingInterrupt(cond).mapError(CommandThrowable.classify)
      _    <- if (bool) z *> loop(cond, i + 1)(z) else ZIO.unit
    } yield ()
}
