package zio.process

import zio.duration._
import zio.test._

trait ZIOProcessBaseSpec extends DefaultRunnableSpec {
  override def aspects = List(TestAspect.timeout(5.seconds), TestAspect.nonFlaky)
}
