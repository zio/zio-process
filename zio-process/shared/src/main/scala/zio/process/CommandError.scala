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
import zio.ExitCode

/**
 * A failure of a [[Command]] or a [[Process]]. Each case is an `Exception`. Match on the case to handle each kind of
 * failure separately.
 */
sealed abstract class CommandError(cause: Throwable) extends Exception(cause) with Serializable

/**
 * The cases of [[CommandError]].
 */
object CommandError extends CommandErrorPlatformSpecific {

  /**
   * The program did not start. Its subclasses are [[ProgramNotFound]], [[PermissionDenied]] and
   * [[WorkingDirectoryMissing]].
   */
  class NotStarted(cause: Throwable) extends CommandError(cause)

  /**
   * The program could not be found, for example because its name is misspelled. `cause` is the exception that the
   * platform reported.
   */
  final case class ProgramNotFound(cause: IOException) extends NotStarted(cause)

  /**
   * The program was found, but the current user may not run it, for example because the file is not executable. `cause`
   * is the exception that the platform reported.
   */
  final case class PermissionDenied(cause: IOException) extends NotStarted(cause)

  /**
   * The working directory set with [[Command.workingDirectory]] does not exist. Raised before the program starts.
   */
  final case class WorkingDirectoryMissing(workingDirectory: File) extends NotStarted(null)

  /**
   * The program ran and exited with a non-zero exit code. Raised by [[Command.successfulExitCode]] and
   * [[Process.successfulExitCode]] only: `run`, `exitCode` and `string` return the exit code or the output without
   * failing.
   */
  final case class NonZeroErrorCode(exitCode: ExitCode) extends CommandError(null)

  /**
   * An input or output failure that is not a missing or non-executable program. For example, a redirect to a file in a
   * directory that does not exist fails with this case.
   */
  final case class IOError(cause: java.io.IOException) extends CommandError(cause)

  /**
   * Any other failure. `cause` is the exception that was thrown.
   */
  final case class Error(cause: Throwable) extends CommandError(cause)
}

private[process] object CommandThrowable extends CommandErrorPlatformSpecific {

  def classify(throwable: Throwable): CommandError =
    throwable match {
      case c: CommandError                                    => c
      case e: IOException if isNotFound(e.getMessage)         => CommandError.ProgramNotFound(e)
      case e: IOException if isPermissionDenied(e.getMessage) => CommandError.PermissionDenied(e)
      case e: java.io.IOException                             => CommandError.IOError(e)
      case e                                                  => CommandError.Error(e)
    }

  object ProgramNotFound {
    def unapply(throwable: Throwable): Option[CommandError.ProgramNotFound] =
      throwable match {
        case e: IOException =>
          if (isNotFound(e.getMessage)) {
            Some(CommandError.ProgramNotFound(e))
          } else None

        case _ => None
      }
  }

  object PermissionDenied {
    def unapply(throwable: Throwable): Option[CommandError.PermissionDenied] =
      throwable match {
        case e: IOException =>
          if (isPermissionDenied(e.getMessage)) Some(CommandError.PermissionDenied(e))
          else None

        case _ => None
      }
  }

  object IOError {
    def unapply(throwable: Throwable): Option[CommandError.IOError] =
      throwable match {
        case e: java.io.IOException => Some(CommandError.IOError(e))
        case _                      => None
      }
  }

  object Error {
    def unapply(throwable: Throwable): Option[CommandError.Error] =
      Some(CommandError.Error(throwable))
  }
}
