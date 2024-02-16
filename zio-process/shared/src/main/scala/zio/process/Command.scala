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

import zio._
import zio.stream.ZStream

import java.nio.charset.Charset
import FilePlatformSpecific._
import java.io.InputStream

sealed trait Command extends CommandPlatformSpecific {

  /**
   * Specify the environment variables that will be used when running this command.
   */
  def env(env: Map[String, String]): Command = this match {
    case c: Command.Standard => c.copy(env = env)
    case Command.Piped(l, r) => Command.Piped(l.env(env), r.env(env))
  }

  /**
   * Runs the command returning only the exit code.
   */
  def exitCode: ZIO[Any, CommandError, ExitCode] =
    run.flatMap(_.exitCode)

  /**
   * Flatten this command to a non-empty chunk of standard commands.
   * For the standard case, this simply returns a 1 element chunk.
   * For the piped case, all the commands in the pipe will be extracted out into a chunk from left to right.
   */
  def flatten: NonEmptyChunk[Command.Standard] = this match {
    case c: Command.Standard => NonEmptyChunk.single(c)
    case Command.Piped(l, r) => l.flatten ++ r.flatten
  }

  /**
   * Inherit standard input, standard output, and standard error.
   */
  def inheritIO: Command =
    stdin(ProcessInput.Inherit).stdout(ProcessOutput.Inherit).stderr(ProcessOutput.Inherit)

  /**
   * Runs the command returning the output as a list of lines (default encoding of UTF-8).
   */
  def lines: ZIO[Any, CommandError, Chunk[String]] =
    run.flatMap(_.stdout.lines)

  /**
   * Runs the command returning the output as a list of lines with the specified encoding.
   */
  def lines(charset: Charset): ZIO[Any, CommandError, Chunk[String]] =
    run.flatMap(_.stdout.lines(charset))

  /**
   * Runs the command returning the output as a stream of lines (default encoding of UTF-8).
   */
  def linesStream: ZStream[Any, CommandError, String] =
    ZStream.fromZIO(run).flatMap(_.stdout.linesStream)

  /**
   * A named alias for `|`
   */
  def pipe(into: Command): Command =
    Command.Piped(this, into)

  /**
   * Pipe the output of this command into the input of the specified command.
   */
  def |(into: Command): Command =
    pipe(into)

  /**
   * Redirect the error stream to be merged with the standard output stream.
   */
  def redirectErrorStream(redirectErrorStream: Boolean): Command = this match {
    case c: Command.Standard => c.copy(redirectErrorStream = redirectErrorStream)
    case Command.Piped(l, r) => Command.Piped(l, r.redirectErrorStream(redirectErrorStream))
  }

  /**
   * Start running the command returning a handle to the running process.
   */
  def run: ZIO[Any, CommandError, Process] = run(None)

  private[process] def run(piping: Option[InputStream]): ZIO[Any, CommandError, Process] =
    this match {
      case c: Command.Standard =>
        for {
          _       <- c.workingDirectory match {
                       case Some(workingDirectory) =>
                         ZIO.when(
                           !FilePlatformSpecific.exists(workingDirectory)
                         )(ZIO.fail(CommandError.WorkingDirectoryMissing(workingDirectory)))
                       case None                   => ZIO.unit
                     }
          process <- build(c, piping).mapError(CommandThrowable.classify)
          _       <- connectStdin(process, c.stdin)

        } yield process

      case c: Command.Piped =>
        c.flatten match {
          case chunk if chunk.length == 1 => chunk.head.run
          case chunk                      =>
            val flushChunksEagerly = chunk.head.stdin match {
              case ProcessInput.FromStream(_, eager)        => eager
              case ProcessInput.JavaStream(_, eager)        => eager
              case ProcessInput.Inherit | ProcessInput.Pipe => true
            }

            val process = chunk.tail.init.foldLeft(chunk.head.run(None)) { case (process, command) =>
              for {
                p   <- process
                _   <- ProcessPlatformSpecific.wait(p.stdoutJava).mapError(CommandThrowable.classify)
                res <- command.stdin(ProcessInput.JavaStream(p.stdoutJava, flushChunksEagerly)).run(Some(p.stdoutJava))
              } yield res
            }

            for {
              p   <- process
              _   <- ProcessPlatformSpecific.wait(p.stdoutJava).mapError(CommandThrowable.classify)
              res <- chunk.last.stdin(ProcessInput.JavaStream(p.stdoutJava, flushChunksEagerly)).run(Some(p.stdoutJava))
            } yield res

        }
    }

  /**
   * Specify what to do with the standard input of this command.
   */
  def stdin(stdin: ProcessInput): Command = this match {
    case c: Command.Standard => c.copy(stdin = stdin)

    // For piped commands it only makes sense to provide `stdin` for the leftmost command as the rest will be piped in.
    case Command.Piped(l, r) => Command.Piped(l.stdin(stdin), r)
  }

  /**
   * Specify what to do with the standard error of this command.
   */
  def stderr(stderr: ProcessOutput): Command = this match {
    case c: Command.Standard => c.copy(stderr = stderr)
    case Command.Piped(l, r) => Command.Piped(l, r.stderr(stderr))
  }

  /**
   * Specify what to do with the standard output of this command.
   */
  def stdout(stdout: ProcessOutput): Command = this match {
    case c: Command.Standard => c.copy(stdout = stdout)
    case Command.Piped(l, r) => Command.Piped(l, r.stdout(stdout))
  }

  /**
   * Runs the command returning the entire output as a string (default encoding of UTF-8).
   */
  def string: ZIO[Any, CommandError, String] =
    run.flatMap(_.stdout.string)

  /**
   * Runs the command returning the entire output as a string with the specified encoding.
   */
  def string(charset: Charset): ZIO[Any, CommandError, String] =
    run.flatMap(_.stdout.string(charset))

  /**
   * Runs the command returning the output as a chunked stream of bytes.
   */
  def stream: ZStream[Any, CommandError, Byte] =
    ZStream.fromZIO(run).flatMap(_.stdout.stream)

  /**
   * Runs the command returning only the exit code if zero.
   */
  def successfulExitCode: ZIO[Any, CommandError, ExitCode] =
    run.flatMap(_.successfulExitCode)

  /**
   * Set the working directory that will be used when this command will be run.
   * For the piped case, each piped command's working directory will also be set.
   */
  def workingDirectory(workingDirectory: File): Command = this match {
    case c: Command.Standard => c.copy(workingDirectory = Some(workingDirectory))
    case Command.Piped(l, r) =>
      Command.Piped(l.workingDirectory(workingDirectory), r.workingDirectory(workingDirectory))
  }

  /**
   * Redirect standard output to a file, overwriting any existing content.
   */
  def >(redirectTo: File): Command =
    stdout(ProcessOutput.FileRedirect(redirectTo))

  /**
   * Redirect standard output to a file, appending content to the file if it already exists.
   */
  def >>(redirectTo: File): Command =
    stdout(ProcessOutput.FileAppendRedirect(redirectTo))

  /**
   * Feed a string to standard input (default encoding of UTF-8).
   */
  def <<(input: String): Command =
    stdin(ProcessInput.fromUTF8String(input))

}

object Command {

  final case class Standard(
    command: NonEmptyChunk[String],
    env: Map[String, String],
    workingDirectory: Option[File],
    stdin: ProcessInput,
    stdout: ProcessOutput,
    stderr: ProcessOutput,
    redirectErrorStream: Boolean
  ) extends Command

  final case class Piped(left: Command, right: Command) extends Command

  /**
   * Create a command with the specified process name and an optional list of arguments.
   */
  def apply(processName: String, args: String*): Command.Standard =
    Command.Standard(
      NonEmptyChunk(processName, args: _*),
      Map.empty,
      Option.empty[File],
      ProcessInput.Inherit,
      ProcessOutput.Pipe,
      ProcessOutput.Pipe,
      redirectErrorStream = false
    )
}
