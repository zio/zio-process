package zio.process

import java.io.File
import java.nio.charset.StandardCharsets
import zio.duration._
import zio.stream.ZTransducer
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock
import zio.{Chunk, ExitCode, ZIO}

import java.util.Optional

// TODO: Add aspects for different OSes? scala.util.Properties.isWin, etc. Also try to make this as OS agnostic as possible in the first place
object CommandSpec extends ZIOProcessBaseSpec {

  def spec = suite("CommandSpec")(
    testM("convert stdout to string") {
      for {
        output <- Command("echo", "-n", "test").string
      } yield assertTrue(output == "test")
    },
    testM("convert stdout to list of lines") {
      for {
        lines <- Command("echo", "-n", "1\n2\n3").lines
      } yield assertTrue(lines == Chunk("1", "2", "3"))
    },
    testM("stream lines of output") {
      assertM(Command("echo", "-n", "1\n2\n3").linesStream.runCollect)(equalTo(Chunk("1", "2", "3")))
    },
    testM("work with stream directly") {
      val zio = Command("echo", "-n", "1\n2\n3").stream
        .aggregate(ZTransducer.utf8Decode)
        .aggregate(ZTransducer.splitLines)
        .runCollect

      assertM(zio)(equalTo(Chunk("1", "2", "3")))
    },
    testM("fail trying to run a command that doesn't exist") {
      val zio = Command("some-invalid-command", "test").string

      assertM(zio.run)(fails(isSubtype[CommandError.ProgramNotFound](anything)))
    },
    testM("pass environment variables") {
      val zio = Command("bash", "-c", "echo -n \"var = $VAR\"").env(Map("VAR" -> "value")).string

      assertM(zio)(equalTo("var = value"))
    },
    testM("accept streaming stdin") {
      val stream = Command("echo", "-n", "a", "b", "c").stream
      val zio    = Command("cat").stdin(ProcessInput.fromStream(stream)).string

      assertM(zio)(equalTo("a b c"))
    },
    testM("accept string stdin") {
      val zio = Command("cat").stdin(ProcessInput.fromUTF8String("piped in")).string

      assertM(zio)(equalTo("piped in"))
    },
    testM("support different encodings") {
      val zio =
        Command("cat")
          .stdin(ProcessInput.fromString("piped in", StandardCharsets.UTF_16))
          .string(StandardCharsets.UTF_16)

      assertM(zio)(equalTo("piped in"))
    },
    testM("set workingDirectory") {
      val zio = Command("ls").workingDirectory(new File("src/main/scala/zio/process")).lines

      assertM(zio)(contains("Command.scala"))
    },
    testM("be able to fallback to a different program using typed error channel") {
      val zio = Command("custom-echo", "-n", "test").string.catchSome { case CommandError.ProgramNotFound(_) =>
        Command("echo", "-n", "test").string
      }

      assertM(zio)(equalTo("test"))
    },
    testM("interrupt a process manually") {
      val zio = for {
        fiber  <- Command("sleep", "20").exitCode.fork
        _      <- fiber.interrupt.fork
        result <- fiber.join
      } yield result

      assertM(zio.run)(isInterrupted)
    },
    testM("interrupt a process due to timeout") {
      val zio = for {
        fiber       <- Command("sleep", "20").exitCode.timeout(5.seconds).fork
        adjustFiber <- TestClock.adjust(5.seconds).fork
        _           <- ZIO.sleep(5.seconds)
        _           <- adjustFiber.join
        result      <- fiber.join
      } yield result

      assertM(zio)(isNone)
    } @@ TestAspect.ignore, // TODO: Until https://github.com/zio/zio/issues/3840 is fixed or there is a workaround
    testM("capture stdout and stderr separately") {
      val zio = for {
        process <- Command("src/test/bash/both-streams-test.sh").run
        stdout  <- process.stdout.string
        stderr  <- process.stderr.string
      } yield (stdout, stderr)

      assertM(zio)(equalTo(("stdout1\nstdout2\n", "stderr1\nstderr2\n")))
    },
    testM("return non-zero exit code in success channel") {
      val zio = Command("ls", "--non-existent-flag").exitCode

      assertM(zio)(not(equalTo(ExitCode.success)))
    },
    testM("absolve non-zero exit code") {
      val zio = Command("ls", "--non-existent-flag").successfulExitCode

      assertM(zio.run)(fails(isSubtype[CommandError.NonZeroErrorCode](anything)))
    },
    testM("permission denied is a typed error") {
      val zio = Command("src/test/bash/no-permissions.sh").string

      assertM(zio.run)(fails(isSubtype[CommandError.PermissionDenied](anything)))
    },
    testM("redirectErrorStream should merge stderr into stdout") {
      for {
        process <- Command("src/test/bash/both-streams-test.sh").redirectErrorStream(true).run
        stdout  <- process.stdout.string
        stderr  <- process.stderr.string
      } yield assertTrue(stdout == "stdout1\nstderr1\nstdout2\nstderr2\n", stderr.isEmpty)
    },
    testM("be able to kill a process that's running") {
      for {
        process           <- Command("src/test/bash/echo-repeat.sh").run
        isAliveBeforeKill <- process.isAlive
        _                 <- process.kill
        isAliveAfterKill  <- process.isAlive
      } yield assertTrue(isAliveBeforeKill, !isAliveAfterKill)
    },
    testM("typed error for non-existent working directory") {
      for {
        exit <- Command("ls").workingDirectory(new File("/some/bad/path")).lines.run
      } yield assert(exit)(fails(isSubtype[CommandError.WorkingDirectoryMissing](anything)))
    },
    testM("kill only kills parent process") {
      for {
        process <- Command("./sample-parent.sh").workingDirectory(new File("src/test/bash/kill-test")).run
        pids <- process.stdout.stream
                  .aggregate(ZTransducer.utf8Decode)
                  .aggregate(ZTransducer.splitLines)
                  .take(3)
                  .runCollect
                  .map(_.map(_.toInt))
        _ <- process.kill
        pidsAlive = pids.map { pid =>
                      toScalaOption(ProcessHandle.of(pid.toLong)).exists(_.isAlive)
                    }
      } yield assertTrue(pidsAlive == Chunk(false, true, true))
    },
    testM("killTree also kills child processes") {
      for {
        process <- Command("./sample-parent.sh").workingDirectory(new File("src/test/bash/kill-test")).run
        pids <- process.stdout.stream
                  .aggregate(ZTransducer.utf8Decode)
                  .aggregate(ZTransducer.splitLines)
                  .take(3)
                  .runCollect
                  .map(_.map(_.toInt))
        _ <- process.killTree
        pidsAlive = pids.map { pid =>
                      toScalaOption(ProcessHandle.of(pid.toLong)).exists(_.isAlive)
                    }
      } yield assertTrue(pidsAlive == Chunk(false, false, false))
    },
    testM("killTreeForcibly also kills child processes") {
      for {
        process <- Command("./sample-parent.sh").workingDirectory(new File("src/test/bash/kill-test")).run
        pids <- process.stdout.stream
                  .aggregate(ZTransducer.utf8Decode)
                  .aggregate(ZTransducer.splitLines)
                  .take(3)
                  .runCollect
                  .map(_.map(_.toInt))
        _ <- process.killTree
        pidsAlive = pids.map { pid =>
                      toScalaOption(ProcessHandle.of(pid.toLong)).exists(_.isAlive)
                    }
      } yield assertTrue(pidsAlive == Chunk(false, false, false))
    }
  )

  private def toScalaOption[A](o: Optional[A]): Option[A] = if (o.isPresent) Some(o.get) else None
}
