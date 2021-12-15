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
  def execute[T](f: JProcess => T): ZIO[Any, CommandError, T] =
    attemptBlockingInterrupt(f(process)).refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Return the exit code of this process.
   */
  def exitCode: ZIO[Any, CommandError, ExitCode]              =
    attemptBlockingCancelable(ExitCode(process.waitFor()))(UIO(process.destroy())).refineOrDie {
      case CommandThrowable.IOError(e) => e
    }

  /**
   * Tests whether the process is still alive (not terminated or completed).
   */
  def isAlive: UIO[Boolean] = UIO(process.isAlive)

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
   * Kills the entire process tree and will wait until completed. Equivalent to SIGTERM on Unix platforms.
   *
   * Note: This method requires JDK 9+
   */
  def killTree: ZIO[Any, CommandError, Unit] =
    execute { process =>
      process.destroy()
      process.waitFor()

      process.descendants().forEach { p =>
        p.destroy()

        if (p.isAlive) {
          p.onExit().get // `ProcessHandle` doesn't have waitFor
          ()
        }
      }
    }

  /**
   * Kills the entire process tree and will wait until completed. Equivalent to SIGKILL on Unix platforms.
   *
   * Note: This method requires JDK 9+
   */
  def killTreeForcibly: ZIO[Any, CommandError, Unit] =
    execute { process =>
      process.destroyForcibly()
      process.waitFor()

      process.descendants().forEach { p =>
        p.destroyForcibly()

        if (p.isAlive) {
          p.onExit().get // `ProcessHandle` doesn't have waitFor
          ()
        }
      }
    }

  /**
   * Return the exit code of this process if it is zero. If non-zero, it will fail with `CommandError.NonZeroErrorCode`.
   */
  def successfulExitCode: ZIO[Any, CommandError, ExitCode] =
    attemptBlockingCancelable(ExitCode(process.waitFor()))(UIO(process.destroy())).refineOrDie {
      case CommandThrowable.IOError(e) => e: CommandError
    }.filterOrElseWith(_ == ExitCode.success)(exitCode => ZIO.fail(CommandError.NonZeroErrorCode(exitCode)))

}
