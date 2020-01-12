package zio.process

import java.io.File

import zio.test.Assertion._
import zio.test._

object PipedCommandSpec
    extends DefaultRunnableSpec(
      suite("PipedCommandSpec")(
        testM("support piping") {
          val zio = (Command("echo", "2\n1\n3") | Command("cat") | Command("sort")).lines

          assertM(zio, equalTo(List("1", "2", "3")))
        },
        testM("piping is associative") {
          for {
            lines1 <- (Command("echo", "2\n1\n3") | (Command("cat") | (Command("sort") | Command("head", "-2")))).lines
            lines2 <- (Command("echo", "2\n1\n3") | Command("cat") | (Command("sort") | Command("head", "-2"))).lines
          } yield assert(lines1, equalTo(lines2))
        },
        testM("stdin on piped command") {
          val zio = (Command("cat") | Command("sort") | Command("head", "-2"))
            .stdin(ProcessInput.fromUTF8String("2\n1\n3"))
            .lines

          assertM(zio, equalTo(List("1", "2")))
        },
        test("env delegate to all commands") {
          val env     = Map("key" -> "value")
          val command = (Command("cat") | (Command("sort") | Command("head", "-2"))).env(env)

          assert(command.flatten.map(_.env), equalTo(Vector(env, env, env)))
        },
        test("workingDirectory delegate to all commands") {
          val workingDirectory = new File("working-directory")
          val command          = (Command("cat") | (Command("sort") | Command("head", "-2"))).workingDirectory(workingDirectory)

          assert(
            command.flatten.map(_.workingDirectory),
            equalTo(Vector(Some(workingDirectory), Some(workingDirectory), Some(workingDirectory)))
          )
        },
        test("stderr delegate to rightmost command") {
          val command = (Command("cat") | (Command("sort") | Command("head", "-2"))).stderr(ProcessOutput.Pipe)

          assert(
            command.flatten.map(_.stderr),
            equalTo(Vector(ProcessOutput.Inherit, ProcessOutput.Inherit, ProcessOutput.Pipe))
          )
        },
        test("stdout delegate to rightmost command") {
          val command = (Command("cat") | (Command("sort") | Command("head", "-2"))).stdout(ProcessOutput.Inherit)

          assert(
            command.flatten.map(_.stdout),
            equalTo(Vector(ProcessOutput.Pipe, ProcessOutput.Pipe, ProcessOutput.Inherit))
          )
        },
        test("redirectErrorStream delegate to rightmost command") {
          val command = (Command("cat") | (Command("sort") | Command("head", "-2"))).redirectErrorStream(true)

          assert(
            command.flatten.map(_.redirectErrorStream),
            equalTo(Vector(false, false, true))
          )
        }
      )
    )
