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

import java.io.InputStream
import ProcessPlatformSpecific._

final case class Process(private[process] val process: JProcess) extends ProcessPlatformSpecific { self =>

  /**
   * Access the standard output stream.
   */
  val stdout: ProcessStream = ProcessStream(getInputStream, get)

  /**
   * Access the standard error stream.
   */
  val stderr: ProcessStream = ProcessStream(getErrorStream, get)

  /**
   * Access the standard output as an Java `InputStream`.
   */
  def stdoutJava: InputStream = getInputStream

  /**
   * Access the underlying Java Process wrapped in a blocking ZIO.
   */
  def execute[T](f: JProcess => T): ZIO[Any, CommandError, T] =
    attemptBlockingInterrupt(f(process)).refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Return the exit code of this process.
   */
  def exitCode: ZIO[Any, CommandError, ExitCode]              =
    attemptBlockingCancelable(ExitCode(waitForUnsafe))(ZIO.succeed(destroyUnsafe())).refineOrDie {
      case CommandThrowable.IOError(e) => e
    }

  /**
   * Tests whether the process is still alive (not terminated or completed).
   */
  def isAlive: UIO[Boolean] = ZIO.succeed(isAliveUnsafe)

  /**
   * Returns the native process ID of the process.
   *
   * This method requires JDK +9.
   */
  def pid: ZIO[Any, CommandError, Long] = attemptBlockingInterrupt(pidUnsafe).refineOrDie {
    case CommandThrowable.IOError(e) => e
  }

  /**
   * Kills the process and will wait until completed. Equivalent to SIGTERM on Unix platforms.
   */
  def kill: ZIO[Any, CommandError, Unit]                   =
    attemptBlockingInterrupt {
      destroyUnsafe()
      waitForUnsafe
      ()
    }.refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Kills the process and will wait until completed. Equivalent to SIGKILL on Unix platforms.
   */
  def killForcibly: ZIO[Any, CommandError, Unit]           =
    attemptBlockingInterrupt {
      destroyForciblyUnsafe
      waitForUnsafe
      ()
    }.refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Return the exit code of this process if it is zero. If non-zero, it will fail with `CommandError.NonZeroErrorCode`.
   */
  def successfulExitCode: ZIO[Any, CommandError, ExitCode] =
    attemptBlockingCancelable(ExitCode(waitForUnsafe))(ZIO.succeed(destroyUnsafe())).refineOrDie {
      case CommandThrowable.IOError(e) => e: CommandError
    }.filterOrElseWith(_ == ExitCode.success)(exitCode => ZIO.fail(CommandError.NonZeroErrorCode(exitCode)))

}
