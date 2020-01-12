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

import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.nio.charset.Charset

import zio.blocking.Blocking
import zio.stream.{ StreamChunk, ZSink, ZStream }
import zio.{ IO, RIO, Task, UIO, ZIO }

import scala.jdk.CollectionConverters._

sealed trait Command {

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
  def exitCode: RIO[Blocking, Int] =
    run.flatMap(_.exitCode)

  /**
   * Flatten this command to a vector of standard commands.
   * For the standard case, this simply returns a 1 element vector.
   * For the piped case, all the commands in the pipe will be extracted out into a Vector from left to right.
   */
  def flatten: Vector[Command.Standard] = this match {
    case c: Command.Standard => Vector(c)
    case Command.Piped(l, r) => l.flatten ++ r.flatten
  }

  /**
   * Inherit standard input, standard output, and standard error.
   */
  def inheritIO: Command =
    stdin(ProcessInput.inherit).stdout(ProcessOutput.Inherit).stderr(ProcessOutput.Inherit)

  /**
   * Runs the command returning the output as a list of lines (default encoding of UTF-8).
   */
  def lines: RIO[Blocking, List[String]] =
    run.flatMap(_.lines)

  /**
   * Runs the command returning the output as a list of lines with the specified encoding.
   */
  def lines(charset: Charset): RIO[Blocking, List[String]] =
    run.flatMap(_.lines(charset))

  /**
   * Runs the command returning the output as a stream of lines (default encoding of UTF-8).
   */
  def linesStream: ZStream[Blocking, Throwable, String] =
    ZStream.fromEffect(run).flatMap(_.linesStream)

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
  def run: RIO[Blocking, Process] =
    this match {
      case c: Command.Standard =>
        for {
          process <- Task {
                      val builder = new ProcessBuilder(c.command: _*)
                      builder.redirectErrorStream(c.redirectErrorStream)
                      c.workingDirectory.foreach(builder.directory)

                      if (c.env.nonEmpty) {
                        builder.environment().putAll(c.env.asJava)
                      }

                      c.stdin match {
                        case ProcessInput(None)    => builder.redirectInput(Redirect.INHERIT)
                        case ProcessInput(Some(_)) => ()
                      }

                      c.stdout match {
                        case ProcessOutput.FileRedirect(file)       => builder.redirectOutput(Redirect.to(file))
                        case ProcessOutput.FileAppendRedirect(file) => builder.redirectOutput(Redirect.appendTo(file))
                        case ProcessOutput.Inherit                  => builder.redirectOutput(Redirect.INHERIT)
                        case ProcessOutput.Pipe                     => builder.redirectOutput(Redirect.PIPE)
                      }

                      c.stderr match {
                        case ProcessOutput.FileRedirect(file)       => builder.redirectError(Redirect.to(file))
                        case ProcessOutput.FileAppendRedirect(file) => builder.redirectError(Redirect.appendTo(file))
                        case ProcessOutput.Inherit                  => builder.redirectError(Redirect.INHERIT)
                        case ProcessOutput.Pipe                     => builder.redirectError(Redirect.PIPE)
                      }

                      Process(builder.start())
                    }
          _ <- c.stdin match {
                case ProcessInput(None) => ZIO.unit
                case ProcessInput(Some(input)) =>
                  for {
                    outputStream <- process.execute(_.getOutputStream)
                    _ <- input.chunks
                          .run(ZSink.fromOutputStream(outputStream))
                          .ensuring(UIO(outputStream.close()))
                          .fork
                          .daemon
                  } yield ()
              }
        } yield process

      case c: Command.Piped =>
        c.flatten match {
          // Technically we're guaranteed to always have 2 elements in the piped case, but `Vector` can't represent this.
          // Let's just handle the impossible cases anyway for completeness.
          case v if v.isEmpty => IO.fail(new NoSuchElementException("No commands in pipe."))
          case Vector(head)   => head.run
          case Vector(head, tail @ _*) =>
            for {
              stream <- tail.init.foldLeft(head.stream) {
                         case (s, command) =>
                           s.flatMap { input =>
                             command.stdin(ProcessInput.fromStreamChunk(input)).stream
                           }
                       }
              result <- tail.last.stdin(ProcessInput.fromStreamChunk(stream)).run
            } yield result
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
  def string: RIO[Blocking, String] =
    run.flatMap(_.string)

  /**
   * Runs the command returning the entire output as a string with the specified encoding.
   */
  def string(charset: Charset): RIO[Blocking, String] =
    run.flatMap(_.string(charset))

  /**
   * Runs the command returning the output as a chunked stream of bytes.
   */
  def stream: RIO[Blocking, StreamChunk[Throwable, Byte]] =
    run.map(_.stream)

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
    command: ::[String],
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
      ::(processName, args.toList),
      Map.empty,
      Option.empty[File],
      ProcessInput.inherit,
      ProcessOutput.Pipe,
      ProcessOutput.Inherit,
      redirectErrorStream = false
    )
}
