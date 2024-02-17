package zio.process

import scala.scalajs.js

private[process] object FilePlatformSpecific {
  type File = String
  type Path = String

  def getAbsolute(file: File): js.Any = {
    val path   = js.Dynamic.global.require("path")
    val nodejs = js.Dynamic.global.require("process")
    val res    = path.join(nodejs.cwd(), file)
    res
  }

  def exists(file: File): Boolean = {
    val fs = js.Dynamic.global.require("fs")
    fs.existsSync(getAbsolute(file)).asInstanceOf[Boolean]
  }

}
