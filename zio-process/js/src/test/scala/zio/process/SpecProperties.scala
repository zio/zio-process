package zio.process

trait SpecProperties {
  val dir = ""

  import FilePlatformSpecific._
  def mkFile(file: String): File = file

}
