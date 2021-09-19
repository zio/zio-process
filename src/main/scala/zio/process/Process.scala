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

import zio.blocking._
import zio.{ExitCode, UIO, ZIO}

import java.lang.{Process => JProcess}

final case class Process(private val process: JProcess) {

  /**
   * Access the standard output stream.
   */
  val stdout: ProcessStream = ProcessStream(process.getInputStream())

  /**
   * Access the standard error stream.
   */
  val stderr: ProcessStream = ProcessStream(process.getErrorStream())

  /**
   * Access the underlying Java Process wrapped in a blocking ZIO.
   */
  def execute[T](f: JProcess => T): ZIO[Blocking, CommandError, T] =
    effectBlockingInterrupt(f(process)).refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Return the exit code of this process.
   */
  def exitCode: ZIO[Blocking, CommandError, ExitCode] =
    effectBlockingCancelable(ExitCode(process.waitFor()))(UIO(process.destroy())).refineOrDie {
      case CommandThrowable.IOError(e) => e
    }

  /**
   * Tests whether the process is still alive (not terminated or completed).
   */
  def isAlive: UIO[Boolean] = UIO(process.isAlive)

  /**
   * Kills the process and will wait until completed. Equivalent to SIGTERM on Unix platforms.
   */
  def kill: ZIO[Blocking, CommandError, Unit] =
    execute { process =>
      process.destroy()
      process.waitFor()
      ()
    }

  /**
   * Kills the process and will wait until completed. Equivalent to SIGKILL on Unix platforms.
   */
  def killForcibly: ZIO[Blocking, CommandError, Unit] =
    execute { process =>
      process.destroyForcibly()
      process.waitFor()
      ()
    }

  /**
   * Return the exit code of this process if it is zero. If non-zero, it will fail with `CommandError.NonZeroErrorCode`.
   */
  def successfulExitCode: ZIO[Blocking, CommandError, ExitCode] =
    effectBlockingCancelable(ExitCode(process.waitFor()))(UIO(process.destroy())).refineOrDie {
      case CommandThrowable.IOError(e) => e: CommandError
    }.filterOrElse(_ == ExitCode.success)(exitCode => ZIO.fail(CommandError.NonZeroErrorCode(exitCode)))

}
