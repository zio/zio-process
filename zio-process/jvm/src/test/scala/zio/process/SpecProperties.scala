package zio.process

// TODO::
trait SpecProperties {
  val dir = ""

  import FilePlatformSpecific._
  def mkFile(file: String): File = new File(file)
}
