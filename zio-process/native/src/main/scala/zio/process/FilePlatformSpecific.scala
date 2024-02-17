package zio.process

object FilePlatformSpecific {
  type File = java.io.File

  def exists(file: File) = file.exists()

}
