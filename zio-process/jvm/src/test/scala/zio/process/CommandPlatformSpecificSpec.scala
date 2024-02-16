package zio.process

import zio.stream.ZPipeline
import zio.test._
import zio.Chunk

import FilePlatformSpecific._
import java.util.Optional

// TODO: Add aspects for different OSes? scala.util.Properties.isWin, etc. Also try to make this as OS agnostic as possible in the first place
object CommandPlatformSpecificSpec extends ZIOProcessBaseSpec {

  def spec = suite("CommandSpec")(
    test("killTree also kills child processes") {
      for {
        process  <- Command("./sample-parent.sh").workingDirectory(new File("src/test/bash/kill-test")).run
        pids     <- process.stdout.stream
                      .via(ZPipeline.utf8Decode)
                      .via(ZPipeline.splitLines)
                      .take(3)
                      .runCollect
                      .map(_.map(_.toInt))
        _        <- process.killTree
        pidsAlive = pids.map { pid =>
                      toScalaOption(ProcessHandle.of(pid.toLong)).exists(_.isAlive)
                    }
      } yield assertTrue(pidsAlive == Chunk(false, false, false))
    } @@ TestAspect.nonFlaky(25),
    test("killTreeForcibly also kills child processes") {
      for {
        process  <- Command("./sample-parent.sh").workingDirectory(new File("src/test/bash/kill-test")).run
        pids     <- process.stdout.stream
                      .via(ZPipeline.utf8Decode)
                      .via(ZPipeline.splitLines)
                      .take(3)
                      .runCollect
                      .map(_.map(_.toInt))
        _        <- process.killTree
        pidsAlive = pids.map { pid =>
                      toScalaOption(ProcessHandle.of(pid.toLong)).exists(_.isAlive)
                    }
      } yield assertTrue(pidsAlive == Chunk(false, false, false))
    } @@ TestAspect.nonFlaky(25),
    test("kill only kills parent process") {
      for {
        process  <- Command("./sample-parent.sh").workingDirectory(new File("src/test/bash/kill-test")).run
        pids     <- process.stdout.stream
                      .via(ZPipeline.utf8Decode)
                      .via(ZPipeline.splitLines)
                      .take(3)
                      .runCollect
                      .map(_.map(_.toInt))
        _        <- process.kill
        pidsAlive = pids.map { pid =>
                      toScalaOption(ProcessHandle.of(pid.toLong)).exists(_.isAlive)
                    }
      } yield assertTrue(pidsAlive == Chunk(false, true, true))
    } @@ TestAspect.nonFlaky(25)
  )

  private def toScalaOption[A](o: Optional[A]): Option[A] = if (o.isPresent) Some(o.get) else None
}
