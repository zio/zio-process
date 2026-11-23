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

/**
 * Where the standard output or standard error of a command goes. The default is [[ProcessOutput.Pipe]].
 */
sealed trait ProcessOutput

/**
 * The cases of [[ProcessOutput]].
 */
object ProcessOutput {

  /**
   * Write the output to `file`, replacing any existing content. Created by `>` on a command.
   */
  final case class FileRedirect(file: File) extends ProcessOutput

  /**
   * Append the output to `file`, keeping any existing content. Created by `>>` on a command.
   */
  final case class FileAppendRedirect(file: File) extends ProcessOutput

  /**
   * Send the output to the standard output or standard error of the parent process, so it appears on the same terminal.
   * The output cannot be read with `string`, `lines` or `stream`.
   */
  case object Inherit extends ProcessOutput

  /**
   * Make the output readable through [[Process.stdout]] or [[Process.stderr]], or through `string`, `lines` and
   * `stream` on the command. In a piped command, the output of each command except the rightmost goes to the next
   * command.
   */
  case object Pipe extends ProcessOutput
}
