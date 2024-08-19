package zio.process

private[process] object FilePlatformSpecific {
  type File = java.io.File

  def exists(file: File): Boolean = file.exists()
}
