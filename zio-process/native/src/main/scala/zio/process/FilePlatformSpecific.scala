package zio.process

private[process] object FilePlatformSpecific {
  type File = java.io.File

  def fileOf(file: String): File = new File(file)

  def exists(file: File): Boolean = file.exists()
}
