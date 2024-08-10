package zio.process

import zio.test._
import zio._

trait ZIOProcessBaseSpec extends ZIOSpecDefault {
  override def aspects = Chunk(TestAspect.timeout(30.seconds))
}
