package zio.process

import zio.stream.ZPipeline
import zio.test.Assertion._
import zio.test._
import zio.{ durationInt, Chunk, ExitCode, ZIO }

import java.io.File
import java.nio.charset.StandardCharsets

// TODO: Add aspects for different OSes? scala.util.Properties.isWin, etc. Also try to make this as OS agnostic as possible in the first place
object CommandSpec extends ZIOProcessBaseSpec {

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
      assertM(Command("echo", "-n", "1\n2\n3").linesStream.runCollect)(equalTo(Chunk("1", "2", "3")))
    },
    test("work with stream directly") {
      val zio = Command("echo", "-n", "1\n2\n3").stream.via(ZPipeline.utf8Decode).via(ZPipeline.splitLines).runCollect

      assertM(zio)(equalTo(Chunk("1", "2", "3")))
    },
    test("fail trying to run a command that doesn't exist") {
      val zio = Command("some-invalid-command", "test").string

      assertM(zio.exit)(fails(isSubtype[CommandError.ProgramNotFound](anything)))
    },
    test("pass environment variables") {
      val zio = Command("bash", "-c", "echo -n \"var = $VAR\"").env(Map("VAR" -> "value")).string

      assertM(zio)(equalTo("var = value"))
    },
    test("accept streaming stdin") {
      val stream = Command("echo", "-n", "a", "b", "c").stream
      val zio    = Command("cat").stdin(ProcessInput.fromStream(stream)).string

      assertM(zio)(equalTo("a b c"))
    },
    test("accept string stdin") {
      val zio = Command("cat").stdin(ProcessInput.fromUTF8String("piped in")).string

      assertM(zio)(equalTo("piped in"))
    },
    test("support different encodings") {
      val zio =
        Command("cat")
          .stdin(ProcessInput.fromString("piped in", StandardCharsets.UTF_16))
          .string(StandardCharsets.UTF_16)

      assertM(zio)(equalTo("piped in"))
    },
    test("set workingDirectory") {
      val zio = Command("ls").workingDirectory(new File("src/main/scala/zio/process")).lines

      assertM(zio)(contains("Command.scala"))
    },
    test("be able to fallback to a different program using typed error channel") {
      val zio = Command("custom-echo", "-n", "test").string.catchSome { case CommandError.ProgramNotFound(_) =>
        Command("echo", "-n", "test").string
      }

      assertM(zio)(equalTo("test"))
    },
    test("interrupt a process manually") {
      val zio = for {
        fiber  <- Command("sleep", "20").exitCode.fork
        _      <- fiber.interrupt.fork
        result <- fiber.join
      } yield result

      assertM(zio.exit)(isInterrupted)
    },
    test("interrupt a process due to timeout") {
      val zio = for {
        fiber       <- Command("sleep", "20").exitCode.timeout(5.seconds).fork
        adjustFiber <- TestClock.adjust(5.seconds).fork
        _           <- ZIO.sleep(5.seconds)
        _           <- adjustFiber.join
        result      <- fiber.join
      } yield result

      assertM(zio)(isNone)
    } @@ TestAspect.ignore, // TODO: Until https://github.com/zio/zio/issues/3840 is fixed or there is a workaround
    test("capture stdout and stderr separately") {
      val zio = for {
        process <- Command("src/test/bash/both-streams-test.sh").run
        stdout  <- process.stdout.string
        stderr  <- process.stderr.string
      } yield (stdout, stderr)

      assertM(zio)(equalTo(("stdout1\nstdout2\n", "stderr1\nstderr2\n")))
    },
    test("return non-zero exit code in success channel") {
      val zio = Command("ls", "--non-existent-flag").exitCode

      assertM(zio)(not(equalTo(ExitCode.success)))
    },
    test("absolve non-zero exit code") {
      val zio = Command("ls", "--non-existent-flag").successfulExitCode

      assertM(zio.exit)(fails(isSubtype[CommandError.NonZeroErrorCode](anything)))
    },
    test("permission denied is a typed error") {
      val zio = Command("src/test/bash/no-permissions.sh").string

      assertM(zio.exit)(fails(isSubtype[CommandError.PermissionDenied](anything)))
    },
    test("redirectErrorStream should merge stderr into stdout") {
      for {
        process <- Command("src/test/bash/both-streams-test.sh").redirectErrorStream(true).run
        stdout  <- process.stdout.string
        stderr  <- process.stderr.string
      } yield assertTrue(stdout == "stdout1\nstderr1\nstdout2\nstderr2\n", stderr.isEmpty)
    },
    test("be able to kill a process that's running") {
      for {
        process           <- Command("src/test/bash/echo-repeat.sh").run
        isAliveBeforeKill <- process.isAlive
        _                 <- process.kill
        isAliveAfterKill  <- process.isAlive
      } yield assertTrue(isAliveBeforeKill, !isAliveAfterKill)
    },
    test("typed error for non-existent working directory") {
      for {
        exit <- Command("ls").workingDirectory(new File("/some/bad/path")).lines.exit
      } yield assert(exit)(fails(isSubtype[CommandError.WorkingDirectoryMissing](anything)))
    }
  )
}
