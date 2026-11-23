package zio.process

/**
 * Platform-specific file types and helpers used by [[Command]].
 */
object FilePlatformSpecific {

  /**
   * The type of the paths that commands use as working directories and redirect targets: `java.io.File` on the JVM and
   * Scala Native, and a path string on Scala.js.
   */
  type File = java.io.File

  /**
   * Returns whether `file` exists.
   */
  def exists(file: File) = file.exists()

}
