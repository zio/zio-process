package zio.process

import scala.scalajs.js

/**
 * Platform-specific file types and helpers used by [[Command]].
 */
private[process] object FilePlatformSpecific {

  /**
   * The type of the paths that commands use as working directories and redirect targets: `java.io.File` on the JVM and
   * Scala Native, and a path string on Scala.js.
   */
  type File = String
  type Path = String

  def getAbsolute(file: File): js.Any = {
    val path   = js.Dynamic.global.require("path")
    val nodejs = js.Dynamic.global.require("process")
    val res    = path.join(nodejs.cwd(), file)
    res
  }

  /**
   * Returns whether `file` exists. On Scala.js, `file` is joined to the current working directory, even when it is
   * absolute.
   */
  def exists(file: File): Boolean = {
    val fs = js.Dynamic.global.require("fs")
    fs.existsSync(getAbsolute(file)).asInstanceOf[Boolean]
  }

}
