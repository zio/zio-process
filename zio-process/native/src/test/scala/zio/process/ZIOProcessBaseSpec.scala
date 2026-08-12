package zio.process

import zio.test._
import zio.{ durationInt, Chunk }

trait ZIOProcessBaseSpec extends ZIOSpecDefault {
  override def aspects = Chunk(TestAspect.timeout(30.seconds))
}
