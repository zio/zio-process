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

sealed abstract class CommandError(cause: Throwable) extends Exception(cause) with Serializable

object CommandError extends CommandErrorPlatformSpecific {

  class NotStarted(cause: Throwable)                               extends CommandError(cause)
  final case class ProgramNotFound(cause: IOException)             extends NotStarted(cause)
  final case class PermissionDenied(cause: IOException)            extends NotStarted(cause)
  final case class WorkingDirectoryMissing(workingDirectory: File) extends NotStarted(null)
  final case class NonZeroErrorCode(exitCode: ExitCode)            extends CommandError(null)
  final case class IOError(cause: java.io.IOException)             extends CommandError(cause)
  final case class Error(cause: Throwable)                         extends CommandError(cause)
}

private[process] object CommandThrowable extends CommandErrorPlatformSpecific {

  def classify(throwable: Throwable): CommandError =
    throwable match {
      case c: CommandError                                           => c
      case e: IOException if e.getMessage.contains(notFound)         => CommandError.ProgramNotFound(e)
      case e: IOException if e.getMessage.contains(permissionDenied) => CommandError.PermissionDenied(e)
      case e: java.io.IOException                                    => CommandError.IOError(e)
      case e                                                         => CommandError.Error(e)
    }

  object ProgramNotFound {
    def unapply(throwable: Throwable): Option[CommandError.ProgramNotFound] =
      throwable match {
        case e: IOException =>
          if (e.getMessage.contains(notFound)) {
            Some(CommandError.ProgramNotFound(e))
          } else None

        case _ => None
      }
  }

  object PermissionDenied {
    def unapply(throwable: Throwable): Option[CommandError.PermissionDenied] =
      throwable match {
        case e: IOException =>
          if (e.getMessage.contains(permissionDenied)) Some(CommandError.PermissionDenied(e))
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
