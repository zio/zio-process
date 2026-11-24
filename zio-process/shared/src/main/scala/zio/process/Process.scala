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

/**
 * A program that has started. Obtained from [[Command.run]]. `stdout` and `stderr` give access to its output. On the
 * JVM and Scala Native, `exitCode`, `kill` and `killForcibly` wait for it to exit or stop it.
 */
final case class Process(private[process] val process: JProcess) extends ProcessPlatformSpecific { self =>

  /**
   * Access the standard output stream. On the JVM, it has no data when the command's standard output is not
   * [[ProcessOutput.Pipe]].
   */
  val stdout: ProcessStream = ProcessStream(getInputStream, get)

  /**
   * Access the standard error stream. On the JVM, it has no data when the command's standard error is not
   * [[ProcessOutput.Pipe]]. It also has no data when `redirectErrorStream(true)` merges it into the standard output.
   */
  val stderr: ProcessStream = ProcessStream(getErrorStream, get)

  /**
   * Access the standard output as a Java `InputStream`.
   */
  def stdoutJava: InputStream = getInputStream

  /**
   * Access the underlying platform process wrapped in a blocking ZIO. The type of the process is `java.lang.Process` on
   * the JVM and Scala Native, and `JS.ChildProcess` on Scala.js.
   */
  def execute[T](f: JProcess => T): ZIO[Any, CommandError, T] =
    attemptBlockingInterrupt(f(process)).refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Waits for the process to exit and returns its exit code. A non-zero exit code is returned, not failed. Interrupting
   * the wait destroys the process. On Scala.js, it does not wait: it reads the Node.js `exitCode`, which is null while
   * the process runs.
   */
  def exitCode: ZIO[Any, CommandError, ExitCode] =
    attemptBlockingCancelable(ExitCode(waitForUnsafe))(ZIO.succeed(destroyUnsafe())).refineOrDie {
      case CommandThrowable.IOError(e) => e
    }

  /**
   * Tests whether the process is still alive (not terminated or completed). On Scala.js, it returns false only after
   * `kill` or an interrupted wait has stopped the process; a process that exited on its own is still reported as alive.
   */
  def isAlive: UIO[Boolean] = ZIO.succeed(isAliveUnsafe)

  /**
   * Returns the native process ID of the process.
   *
   * On the JVM, this needs Java 9 or greater.
   */
  def pid: ZIO[Any, CommandError, Long] = attemptBlockingInterrupt(pidUnsafe).refineOrDie {
    case CommandThrowable.IOError(e) => e
  }

  /**
   * Kills the process and will wait until completed. Equivalent to SIGTERM on Unix platforms.
   */
  def kill: ZIO[Any, CommandError, Unit] =
    attemptBlockingInterrupt {
      destroyUnsafe()
      waitForUnsafe
      ()
    }.refineOrDie { case CommandThrowable.IOError(e) => e }

  /**
   * Kills the process. On the JVM, this waits until the process has exited and is equivalent to SIGKILL on Unix
   * platforms. On Scala.js, it sends SIGTERM, like `kill`.
   */
  def killForcibly: ZIO[Any, CommandError, Unit] =
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
