package zio.process

import zio.duration._
import zio.test._

trait ZIOProcessBaseSpec extends DefaultRunnableSpec {
  override def aspects = List(TestAspect.timeout(30.seconds))
}
