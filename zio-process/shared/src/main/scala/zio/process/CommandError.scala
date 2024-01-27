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

import java.io.{ File, IOException }

import zio.ExitCode

sealed abstract class CommandError(cause: Throwable) extends Exception(cause) with Product with Serializable

object CommandError {
  final case class ProgramNotFound(cause: IOException)             extends CommandError(cause)
  final case class PermissionDenied(cause: IOException)            extends CommandError(cause)
  final case class WorkingDirectoryMissing(workingDirectory: File) extends CommandError(null)
  final case class NonZeroErrorCode(exitCode: ExitCode)            extends CommandError(null)
  final case class IOError(cause: IOException)                     extends CommandError(cause)
  final case class Error(cause: Throwable)                         extends CommandError(cause)
}

private[process] object CommandThrowable {

  def classify(throwable: Throwable): CommandError =
    throwable match {
      case c: CommandError => c
      case e: IOException  =>
        val notFoundErrorCode         = 2
        val permissionDeniedErrorCode = if (OS.os == OS.Windows) 5 else 13
        if (e.getMessage.contains(s"error=$notFoundErrorCode,")) {
          CommandError.ProgramNotFound(e)
        } else if (e.getMessage.contains(s"error=$permissionDeniedErrorCode,")) CommandError.PermissionDenied(e)
        else CommandError.IOError(e)
      case e               => CommandError.Error(e)
    }

  object ProgramNotFound {
    def unapply(throwable: Throwable): Option[CommandError.ProgramNotFound] =
      throwable match {
        case e: IOException =>
          val notFoundErrorCode = 2
          if (e.getMessage.contains(s"error=$notFoundErrorCode,")) {
            Some(CommandError.ProgramNotFound(e))
          } else None

        case _ => None
      }
  }

  object PermissionDenied {
    def unapply(throwable: Throwable): Option[CommandError.PermissionDenied] =
      throwable match {
        case e: IOException =>
          val permissionDeniedErrorCode = if (OS.os == OS.Windows) 5 else 13
          if (e.getMessage.contains(s"error=$permissionDeniedErrorCode,")) Some(CommandError.PermissionDenied(e))
          else None

        case _ => None
      }
  }

  object IOError {
    def unapply(throwable: Throwable): Option[CommandError.IOError] =
      throwable match {
        case e: IOException => Some(CommandError.IOError(e))
        case _              => None
      }
  }

  object Error {
    def unapply(throwable: Throwable): Option[CommandError.Error] =
      Some(CommandError.Error(throwable))
  }
}
