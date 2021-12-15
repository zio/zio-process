package zio.process

import zio.durationInt
import zio.test._

trait ZIOProcessBaseSpec extends DefaultRunnableSpec {
  override def aspects = List(TestAspect.timeout(30.seconds))
}
