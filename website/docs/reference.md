---
id: reference
title: "Reference"
---

This page lists the public types and methods of ZIO Process, with the platforms where each one is available. For how to use them, see the guides from [Running Commands and Reading Output](./running-commands.md) onward.

## CommandError

Every failure in the error channel is a `CommandError`. A `CommandError` is a `Throwable`, so it can also be logged or rethrown like any exception. Match on the case to handle a specific failure:

| Case                                   | Raised when                                                              |
| -------------------------------------- | ------------------------------------------------------------------------ |
| `NotStarted`                           | The base class of the three cases below. Match on those cases instead.   |
| `ProgramNotFound(cause)`               | The program does not exist. Not reliably raised on Scala Native.         |
| `PermissionDenied(cause)`              | The program cannot be executed. Not reliably raised on Scala Native.     |
| `WorkingDirectoryMissing(workingDirectory)` | The directory set with `Command#workingDirectory` does not exist. Raised on every platform. |
| `NonZeroErrorCode(exitCode)`           | The program ran but exited with a non-zero code, from `successfulExitCode`. |
| `IOError(cause)`                       | Reading or writing a stream failed. `cause` is a `java.io.IOException`.  |
| `Error(cause)`                         | Any other failure. Treat it as a catch-all.                              |

`cause` is the underlying exception where one exists. On Scala.js, the `cause` of `ProgramNotFound` and `PermissionDenied` is a `JavaScriptException`. `WorkingDirectoryMissing` carries the `File` you passed, which is a `String` path on Scala.js.

## Command

`Command` describes a program and its settings. Each setting method returns a new `Command`. The `Command` type has two shapes, one for a single program and one for a pipeline, but most code never needs to tell them apart.

### Construction and Settings

| Member                                 | Returns                       | Description                                                       |
| -------------------------------------- | ----------------------------- | ----------------------------------------------------------------- |
| `Command.apply(name, args*)`           | `Command.Standard`            | Creates a command from a program name and its arguments           |
| `env(env: Map[String, String])`        | `Command`                     | Sets environment variables; replaces the earlier map              |
| `workingDirectory(dir: File)`          | `Command`                     | Sets the directory the program starts in                          |
| `redirectErrorStream(enabled: Boolean)` | `Command`                    | Merges standard error into standard output; ignored on Scala.js   |
| `stdin(input: ProcessInput)`           | `Command`                     | Sets where standard input comes from                              |
| `stdout(output: ProcessOutput)`        | `Command`                     | Sets where standard output goes                                   |
| `stderr(output: ProcessOutput)`        | `Command`                     | Sets where standard error goes                                    |
| `inheritIO`                            | `Command`                     | Sends all three standard streams to the current program's terminal |
| `>(file: File)`                        | `Command`                     | Redirects standard output to a file, overwriting it               |
| `>>(file: File)`                       | `Command`                     | Redirects standard output to a file, appending to it              |
| `<<(input: String)`                    | `Command`                     | Sends a UTF-8 string to standard input                            |
| `\|(into: Command)` and `pipe(into)`   | `Command`                     | Connects the standard output of this command to the next command  |
| `flatten`                              | `NonEmptyChunk[Command.Standard]` | Lists the commands in a pipeline, left to right                |

### Running

| Member                                 | Returns                                 | Description                                                |
| -------------------------------------- | --------------------------------------- | ---------------------------------------------------------- |
| `run`                                  | `ZIO[Any, CommandError, Process]`       | Starts the program and returns its handle                  |
| `exitCode`                             | `ZIO[Any, CommandError, ExitCode]`      | Runs the program and returns its exit code, zero or not    |
| `successfulExitCode`                   | `ZIO[Any, CommandError, ExitCode]`      | Runs the program and fails with `NonZeroErrorCode` if it did not exit with zero |
| `lines` and `lines(charset)`           | `ZIO[Any, CommandError, Chunk[String]]` | Collects standard output as lines                          |
| `linesStream`                          | `ZStream[Any, CommandError, String]`    | Emits standard output as lines                             |
| `string` and `string(charset)`         | `ZIO[Any, CommandError, String]`        | Returns standard output as one string                      |
| `stream`                               | `ZStream[Any, CommandError, Byte]`      | Emits standard output as bytes                             |

## Process

`Process` is the handle returned by `Command#run`. It gives access to the running program's streams and controls it.

| Member                                  | Returns                                 | Description                                                            |
| --------------------------------------- | --------------------------------------- | ---------------------------------------------------------------------- |
| `stdout`                                | `ProcessStream`                         | Standard output of the program                                         |
| `stderr`                                | `ProcessStream`                         | Standard error of the program                                          |
| `stdoutJava`                            | `InputStream`                           | Standard output as a Java `InputStream`                                |
| `exitCode`                              | `ZIO[Any, CommandError, ExitCode]`      | Waits for the program and returns its exit code                        |
| `successfulExitCode`                    | `ZIO[Any, CommandError, ExitCode]`      | Waits for the program and fails if the exit code is not zero           |
| `isAlive`                               | `UIO[Boolean]`                          | Whether the program is still running; see [Platform Support](./platform-support.md) |
| `pid`                                   | `ZIO[Any, CommandError, Long]`          | The operating system's process ID                                      |
| `kill`                                  | `ZIO[Any, CommandError, Unit]`          | Sends the terminate signal and waits for the program to exit           |
| `killForcibly`                          | `ZIO[Any, CommandError, Unit]`          | Forcefully stops the program and waits for it to exit                  |
| `killTree`                              | `ZIO[Any, CommandError, Unit]`          | Stops the program and its child processes. JVM only, JDK 9 or later    |
| `killTreeForcibly`                      | `ZIO[Any, CommandError, Unit]`          | Forcefully stops the program and its children. JVM only, JDK 9 or later |
| `execute[T](f)`                         | `ZIO[Any, CommandError, T]`             | Runs `f` on the platform's underlying process object. For advanced use only |

`execute` gives access to the platform's own process type: `java.lang.Process` on the JVM and Scala Native, and the Node.js child process on Scala.js. Code that uses it is not portable across platforms.

## ProcessStream

`ProcessStream` reads the output of a program, and it is the type of `Process#stdout` and `Process#stderr`. Each method reads from the stream once, so a second read after the first finds no output.

| Member                             | Returns                                 | Description                                   |
| ---------------------------------- | --------------------------------------- | --------------------------------------------- |
| `lines` and `lines(charset)`       | `ZIO[Any, CommandError, Chunk[String]]` | Reads all lines                               |
| `linesStream`                      | `ZStream[Any, CommandError, String]`    | Emits lines as they arrive                    |
| `string` and `string(charset)`     | `ZIO[Any, CommandError, String]`        | Reads everything as one string                |
| `stream`                           | `ZStream[Any, CommandError, Byte]`      | Emits bytes as they arrive                    |

## ProcessInput

`ProcessInput` describes where standard input comes from. Pass one to `Command#stdin`.

| Member                                          | Description                                                            |
| ----------------------------------------------- | ---------------------------------------------------------------------- |
| `Inherit`                                       | Use the current program's standard input. This is the default.         |
| `Pipe`                                          | Standard input is a pipe that the caller writes to by hand. Most code uses one of the constructors below instead. |
| `fromFile(file, chunkSize)`                     | Streams a file. Scala Native takes no `chunkSize`.                     |
| `fromPath(path, chunkSize)`                     | Streams a `java.nio.file.Path`.                                        |
| `fromByteArray(bytes)`                          | Sends an array of bytes                                                |
| `fromString(text, charset)`                     | Sends a string in the given encoding                                   |
| `fromUTF8String(text)`                          | Sends a string in UTF-8                                                |
| `fromStream(stream, flushChunksEagerly)`        | Sends a `ZStream` of bytes. `flushChunksEagerly` defaults to `false`  |
| `fromQueue(queue)`                              | Sends chunks of bytes from a `Queue`, flushing each one right away     |

## ProcessOutput

`ProcessOutput` describes where a stream of output goes. Pass one to `Command#stdout` or `Command#stderr`.

| Member                      | Description                                                      |
| --------------------------- | ---------------------------------------------------------------- |
| `Pipe`                      | Capture the output so the program's `Process` handle can read it. This is the default. |
| `Inherit`                   | Send the output to the current program's terminal                |
| `FileRedirect(file)`        | Write the output to a file, overwriting it                       |
| `FileAppendRedirect(file)`  | Append the output to a file                                      |

## Platform Notes

Each table above notes where a member differs by platform. The full comparison, with the reasons, is on [Platform Support](./platform-support.md).
