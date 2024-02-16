package zio.process

trait SpecProperties {
  val dir = "zio-process/shared/"

  import FilePlatformSpecific._
  def mkFile(file: String): File = new File(file)
}
