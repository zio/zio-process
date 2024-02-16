package zio.process

import zio.stream.ZPipeline
import zio.test.Assertion._
import zio.test._
import zio.{ durationInt, Chunk, ExitCode, Queue, System, ZIO }

import java.nio.charset.StandardCharsets

// TODO: Add aspects for different OSes? scala.util.Properties.isWin, etc. Also try to make this as OS agnostic as possible in the first place
object CommandSpec extends ZIOProcessBaseSpec with SpecProperties {

  def spec = suite("CommandSpec")(
    test("convert stdout to string") {
      for {
        output <- Command("echo", "-n", "test").string
      } yield assertTrue(output == "test")
    },
    test("convert stdout to list of lines") {
      for {
        lines <- Command("echo", "-n", "1\n2\n3").lines
      } yield assertTrue(lines == Chunk("1", "2", "3"))
    },
    test("stream lines of output") {
      assertZIO(Command("echo", "-n", "1\n2\n3").linesStream.runCollect)(equalTo(Chunk("1", "2", "3")))
    },
    test("work with stream directly") {
      val zio = Command("echo", "-n", "1\n2\n3").stream.via(ZPipeline.utf8Decode).via(ZPipeline.splitLines).runCollect

      assertZIO(zio)(equalTo(Chunk("1", "2", "3")))
    },
    test("fail trying to run a command that doesn't exist") {
      val zio = Command("some-invalid-command", "test").string

      assertZIO(zio.exit)(fails(isSubtype[CommandError.ProgramNotFound](anything)))
    } @@ TestAspect.exceptNative,
    test("pass environment variables") {
      val zio = Command("bash", "-c", "echo -n \"var = $VAR\"").env(Map("VAR" -> "value")).string

      assertZIO(zio)(equalTo("var = value"))
    },
    test("accept streaming stdin") {
      val stream = Command("echo", "-n", "a", "b", "c").stream
      val zio    = Command("cat").stdin(ProcessInput.fromStream(stream, flushChunksEagerly = false)).string

      assertZIO(zio)(equalTo("a b c"))
    } @@ TestAspect.jvmOnly,
    test("accept string stdin") { //
      val zio = Command("cat").stdin(ProcessInput.fromUTF8String("piped in")).string

      assertZIO(zio)(equalTo("piped in"))
    },
    test("accept file stdin") {
      for {
        lines <- Command("cat").stdin(ProcessInput.fromFile(mkFile(s"${dir}src/test/bash/echo-repeat.sh"))).lines
      } yield assertTrue(lines.head == "#!/bin/bash")
    },
    test("support different encodings") {
      val zio =
        Command("cat")
          .stdin(ProcessInput.fromString("piped in", StandardCharsets.UTF_16))
          .string(StandardCharsets.UTF_16)

      assertZIO(zio)(equalTo("piped in"))
    },
    test("set workingDirectory") {
      val zio = Command("ls").workingDirectory(mkFile(s"${dir}src/test/bash")).lines

      assertZIO(zio)(contains("no-permissions.sh"))
    },
    test("be able to fallback to a different program using typed error channel") {
      val zio = Command("echo", "-n", "wrong").workingDirectory(mkFile("no-folder")).string.catchSome {
        case CommandError.WorkingDirectoryMissing(_) =>
          Command("echo", "-n", "test").string
      }

      assertZIO(zio)(equalTo("test"))
    },
    test("interrupt a process manually") {
      val zio = for {
        fiber  <- Command("sleep", "20").exitCode.fork
        _      <- fiber.interrupt.fork
        result <- fiber.join
      } yield result

      assertZIO(zio.exit)(isInterrupted)
    },
    test("interrupt a process due to timeout") {
      val zio = for {
        fiber       <- Command("sleep", "20").exitCode.timeout(5.seconds).fork
        adjustFiber <- TestClock.adjust(5.seconds).fork
        _           <- ZIO.sleep(5.seconds)
        _           <- adjustFiber.join
        result      <- fiber.join
      } yield result

      assertZIO(zio)(isNone)
    } @@ TestAspect.ignore, // TODO: Until https://github.com/zio/zio/issues/3840 is fixed or there is a workaround
    test("capture stdout and stderr separately") {
      val zio = for {
        process <- Command(s"${dir}src/test/bash/both-streams-test.sh").run
        stdout  <- process.stdout.string
        stderr  <- process.stderr.string
      } yield (stdout, stderr)

      assertZIO(zio)(equalTo(("stdout1\nstdout2\n", "stderr1\nstderr2\n")))
    },
    test("return non-zero exit code in success channel") {
      val zio = Command("ls", "--non-existent-flag").exitCode

      assertZIO(zio)(not(equalTo(ExitCode.success)))
    },
    test("absolve non-zero exit code") {
      val zio = Command("ls", "--non-existent-flag").successfulExitCode

      assertZIO(zio.exit)(fails(isSubtype[CommandError.NonZeroErrorCode](anything)))
    },
    test("permission denied is a typed error") {
      val zio = Command(s"${dir}src/test/bash/no-permissions.sh").string

      assertZIO(zio.exit)(fails(isSubtype[CommandError.PermissionDenied](anything)))
    } @@ TestAspect.exceptNative,
    test("redirectErrorStream should merge stderr into stdout") {
      for {
        process <- Command(s"${dir}src/test/bash/both-streams-test.sh").redirectErrorStream(true).run
        stdout  <- process.stdout.string
        stderr  <- process.stderr.string
      } yield assertTrue(stdout == "stdout1\nstderr1\nstdout2\nstderr2\n", stderr.isEmpty)
    } @@ TestAspect.exceptJS,
    test("be able to kill a process that's running") {
      for {
        process           <- Command(s"${dir}src/test/bash/echo-repeat.sh").run
        isAliveBeforeKill <- process.isAlive
        _                 <- process.kill
        isAliveAfterKill  <- process.isAlive
      } yield assertTrue(isAliveBeforeKill, !isAliveAfterKill)
    },
    test("typed error for non-existent working directory") {
      for {
        exit <- Command("ls").workingDirectory(mkFile("/some/bad/path")).lines.exit
      } yield assert(exit)(fails(isSubtype[CommandError.WorkingDirectoryMissing](anything)))
    },
    test("end of stream also closes underlying process") {
      val uniqueId = "1b349b66-7a94-42eb-af23-f0a281e68d07" // ScalaJS cannot use UUID.randomUUID()
      for {
        lines      <- Command("yes", uniqueId).linesStream.take(2).runCollect
        grepOutput <- (Command("ps", "aux") | Command("grep", "yes")).linesStream.runCollect
      } yield assertTrue(
        lines == Chunk(uniqueId, uniqueId),
        grepOutput.forall(!_.contains(uniqueId))
      )
    },
    test("connect to a repl-like process and flush the chunks eagerly and get responses right away") {
      for {
        commandQueue <- Queue.unbounded[Chunk[Byte]]
        process      <- Command("./stdin-echo.sh")
                          .workingDirectory(mkFile(s"${dir}src/test/bash"))
                          .stdin(ProcessInput.fromQueue(commandQueue))
                          .run
        _            <- commandQueue.offer(Chunk.fromArray("line1\nline2\n".getBytes(StandardCharsets.UTF_8)))
        _            <- commandQueue.offer(Chunk.fromArray("line3\n".getBytes(StandardCharsets.UTF_8)))
        stdout        = process.stdout
        lines        <- stdout.linesStream.take(3).runCollect
        _            <- process.kill
      } yield assertTrue(lines == Chunk("line1", "line2", "line3"))
    } @@ TestAspect.jvmOnly,
    test("interactive processes") {
      for {
        commandQueue <- Queue.unbounded[Chunk[Byte]]
        process      <- Command("node", "-i").stdin(ProcessInput.fromQueue(commandQueue)).run
        sep          <- System.lineSeparator
        fiber        <- process.stdout.linesStream.foreach { line =>
                          ZIO.debug(s"Response from REPL: $line")
                        }.fork
        _            <- commandQueue.offer(Chunk.fromArray(s"1+1${sep}".getBytes(StandardCharsets.UTF_8)))
        _            <- commandQueue.offer(Chunk.fromArray(s"2**8${sep}".getBytes(StandardCharsets.UTF_8)))
        _            <- commandQueue.offer(Chunk.fromArray(s"process.exit(0)${sep}".getBytes(StandardCharsets.UTF_8)))
        _            <- fiber.join
      } yield assertCompletes
    } @@ TestAspect.withLiveClock @@ TestAspect.jvmOnly,
    test("get pid of a running process") {
      for {
        process <- Command("ls").run
        pid     <- process.pid
      } yield assertTrue(pid > 0L)
    }
  )

}
