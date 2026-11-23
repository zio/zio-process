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

/**
 * A program to run, with its arguments and settings, or a chain of programs connected with `|`.
 *
 * Build a command with [[Command.apply]]. Each settings method returns a new command, so the original is not changed.
 * Nothing runs until an effect that starts the command is executed, such as `run`, `string`, `lines` or `exitCode`.
 *
 * The examples that build a `java.io.File` apply to the JVM and Scala Native. On Scala.js, a file is a path string.
 *
 * {{{
 * import zio.ZIO
 *
 * val listing: ZIO[Any, CommandError, String] = Command("ls", "-l").string
 * }}}
 */
sealed trait Command extends CommandPlatformSpecific {

  /**
   * Adds environment variables for this command. They are added to the environment that the command inherits from the
   * parent process, and a variable that is already set is replaced by the new value. For a piped command, both sides
   * receive the variables.
   *
   * {{{
   * import zio.ZIO
   *
   * val greeting: ZIO[Any, CommandError, String] =
   *   Command("printenv", "GREETING").env(Map("GREETING" -> "hello")).string
   * }}}
   */
  def env(env: Map[String, String]): Command = this match {
    case c: Command.Standard => c.copy(env = env)
    case Command.Piped(l, r) => Command.Piped(l.env(env), r.env(env))
  }

  /**
   * Runs the command returning only the exit code. A non-zero exit code is returned, not failed (use
   * [[successfulExitCode]] to fail on it). Interrupting this effect while the command runs destroys the process.
   *
   * {{{
   * import zio.{ ExitCode, ZIO }
   *
   * val code: ZIO[Any, CommandError, ExitCode] = Command("false").exitCode
   * }}}
   */
  def exitCode: ZIO[Any, CommandError, ExitCode] =
    run.flatMap(_.exitCode)

  /**
   * Flatten this command to a non-empty chunk of standard commands. For the standard case, this simply returns a 1
   * element chunk. For the piped case, all the commands in the pipe will be extracted out into a chunk from left to
   * right.
   */
  def flatten: NonEmptyChunk[Command.Standard] = this match {
    case c: Command.Standard => NonEmptyChunk.single(c)
    case Command.Piped(l, r) => l.flatten ++ r.flatten
  }

  /**
   * Inherit standard input, standard output, and standard error. The command reads from and writes to the terminal of
   * the parent process, so `string`, `lines` and [[stream]] return no output.
   */
  def inheritIO: Command =
    stdin(ProcessInput.Inherit).stdout(ProcessOutput.Inherit).stderr(ProcessOutput.Inherit)

  /**
   * Runs the command returning the output as a list of lines (default encoding of UTF-8). Blank lines are kept. A final
   * line break does not add an empty line.
   *
   * {{{
   * import zio.{ Chunk, ZIO }
   *
   * val names: ZIO[Any, CommandError, Chunk[String]] = Command("ls", "-1").lines
   * }}}
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
   * Pipe the output of this command into the input of the specified command. The standard output of this command
   * becomes the standard input of `into`. `a | b | c` runs three commands in order.
   *
   * {{{
   * import zio.ZIO
   *
   * val upper: ZIO[Any, CommandError, String] =
   *   (Command("echo", "hello") | Command("tr", "a-z", "A-Z")).string
   * }}}
   */
  def |(into: Command): Command =
    pipe(into)

  /**
   * Redirect the error stream to be merged with the standard output stream. For a piped command, this applies only to
   * the rightmost command.
   */
  def redirectErrorStream(redirectErrorStream: Boolean): Command = this match {
    case c: Command.Standard => c.copy(redirectErrorStream = redirectErrorStream)
    case Command.Piped(l, r) => Command.Piped(l, r.redirectErrorStream(redirectErrorStream))
  }

  /**
   * Start running the command returning a handle to the running process. The exit code is not checked here. Fails with
   * [[CommandError.ProgramNotFound]], [[CommandError.PermissionDenied]] or [[CommandError.WorkingDirectoryMissing]]
   * when the program cannot be started.
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
   * Specify what to do with the standard input of this command. For a piped command, this applies to the leftmost
   * command, because the other commands read the output of the command before them.
   */
  def stdin(stdin: ProcessInput): Command = this match {
    case c: Command.Standard => c.copy(stdin = stdin)

    // For piped commands it only makes sense to provide `stdin` for the leftmost command as the rest will be piped in.
    case Command.Piped(l, r) => Command.Piped(l.stdin(stdin), r)
  }

  /**
   * Specify what to do with the standard error of this command. For a piped command, this applies to the rightmost
   * command.
   */
  def stderr(stderr: ProcessOutput): Command = this match {
    case c: Command.Standard => c.copy(stderr = stderr)
    case Command.Piped(l, r) => Command.Piped(l, r.stderr(stderr))
  }

  /**
   * Specify what to do with the standard output of this command. For a piped command, this applies to the rightmost
   * command, because the output of the other commands is the input of the next command.
   */
  def stdout(stdout: ProcessOutput): Command = this match {
    case c: Command.Standard => c.copy(stdout = stdout)
    case Command.Piped(l, r) => Command.Piped(l, r.stdout(stdout))
  }

  /**
   * Runs the command returning the entire output as a string (default encoding of UTF-8). A non-zero exit code is not
   * an error: the output is returned anyway. Returns an empty string when the standard output is inherited or
   * redirected to a file.
   *
   * {{{
   * import zio.ZIO
   *
   * val greeting: ZIO[Any, CommandError, String] = Command("echo", "hello").string
   * }}}
   */
  def string: ZIO[Any, CommandError, String] =
    run.flatMap(_.stdout.string)

  /**
   * Runs the command returning the entire output as a string with the specified encoding.
   */
  def string(charset: Charset): ZIO[Any, CommandError, String] =
    run.flatMap(_.stdout.string(charset))

  /**
   * Runs the command returning the output as a chunked stream of bytes. The stream ends when the output ends.
   *
   * {{{
   * import zio.stream.ZStream
   *
   * val bytes: ZStream[Any, CommandError, Byte] = Command("echo", "hello").stream
   * }}}
   */
  def stream: ZStream[Any, CommandError, Byte] =
    ZStream.fromZIO(run).flatMap(_.stdout.stream)

  /**
   * Runs the command returning only the exit code if zero. Fails with [[CommandError.NonZeroErrorCode]] when the exit
   * code is not zero.
   *
   * {{{
   * import zio.{ ExitCode, ZIO }
   *
   * val ok: ZIO[Any, CommandError, ExitCode] = Command("true").successfulExitCode
   * }}}
   */
  def successfulExitCode: ZIO[Any, CommandError, ExitCode] =
    run.flatMap(_.successfulExitCode)

  /**
   * Set the working directory that will be used when this command will be run. For the piped case, each piped command's
   * working directory will also be set. Running the command fails with [[CommandError.WorkingDirectoryMissing]] when
   * the directory does not exist.
   *
   * {{{
   * import zio.ZIO
   *
   * val listing: ZIO[Any, CommandError, String] =
   *   Command("ls").workingDirectory(new java.io.File("/tmp")).string
   * }}}
   */
  def workingDirectory(workingDirectory: File): Command = this match {
    case c: Command.Standard => c.copy(workingDirectory = Some(workingDirectory))
    case Command.Piped(l, r) =>
      Command.Piped(l.workingDirectory(workingDirectory), r.workingDirectory(workingDirectory))
  }

  /**
   * Redirect standard output to a file, overwriting any existing content. For a piped command, this redirects the
   * output of the rightmost command.
   *
   * {{{
   * import zio.{ ExitCode, ZIO }
   *
   * val saved: ZIO[Any, CommandError, ExitCode] =
   *   (Command("echo", "saved") > new java.io.File("out.txt")).exitCode
   * }}}
   */
  def >(redirectTo: File): Command =
    stdout(ProcessOutput.FileRedirect(redirectTo))

  /**
   * Redirect standard output to a file, appending content to the file if it already exists. For a piped command, this
   * redirects the output of the rightmost command.
   */
  def >>(redirectTo: File): Command =
    stdout(ProcessOutput.FileAppendRedirect(redirectTo))

  /**
   * Feed a string to standard input (default encoding of UTF-8). For a piped command, this feeds the leftmost command.
   */
  def <<(input: String): Command =
    stdin(ProcessInput.fromUTF8String(input))

}

/**
 * Constructors and the two shapes of [[Command]]: a single program ([[Command.Standard]]) and a pipe of two commands
 * ([[Command.Piped]]).
 */
object Command {

  /**
   * A single program with its arguments and settings. Usually built with [[Command.apply]] rather than directly.
   */
  final case class Standard(
    command: NonEmptyChunk[String],
    env: Map[String, String],
    workingDirectory: Option[File],
    stdin: ProcessInput,
    stdout: ProcessOutput,
    stderr: ProcessOutput,
    redirectErrorStream: Boolean
  ) extends Command

  /**
   * Two commands connected so that the standard output of `left` feeds the standard input of `right`. Created by `|`.
   */
  final case class Piped(left: Command, right: Command) extends Command

  /**
   * Create a command with the specified process name and an optional list of arguments.
   *
   * The new command adds no environment variables, runs in the working directory of the parent process, reads standard
   * input from the parent process ([[ProcessInput.Inherit]]), pipes standard output and standard error
   * ([[ProcessOutput.Pipe]]), and does not merge the error stream into the output.
   *
   * {{{
   * import zio.ZIO
   *
   * val echoed: ZIO[Any, CommandError, String] = Command("echo", "hello", "world").string
   * }}}
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
