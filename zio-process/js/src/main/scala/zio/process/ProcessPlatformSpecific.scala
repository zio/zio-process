/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.process

import zio.ZIO

import java.io.{ InputStream, OutputStream }
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js
import zio.Chunk
import js.JSConverters._

private[process] trait ProcessPlatformSpecific extends ProcessInterface { self: Process =>

  import ProcessPlatformSpecific._

  private var killed = false

  protected def waitForUnsafe: Int = self.process.exitCode

  protected def isAliveUnsafe: Boolean = !killed
  protected def destroyUnsafe(): Unit = {
    val wasKilled = self.process.kill()
    killed = wasKilled
  }

  protected def destroyForciblyUnsafe: JProcess = {
    self.process.kill()
    self.process
  }

  protected def pidUnsafe: Long = {
    val pid = self.process.pid
    if (js.isUndefined(pid)) null.asInstanceOf[Long] else pid.asInstanceOf[Int].toLong
  }

  protected lazy val stdinInternal          = ProcessPlatformSpecific.JSOutputStream(self.process.stdin)
  protected lazy val stdoutInternal         = ProcessPlatformSpecific.JSInputStream(self.process.stdout)
  protected lazy val stderrInternal         = ProcessPlatformSpecific.JSInputStream(self.process.stderr)
  protected def getInputStream: InputStream = stdoutInternal
  def getOutputStream: OutputStream         = stdinInternal
  protected def getErrorStream: InputStream = stderrInternal

  protected def get: Option[OutputStream] = Some(stdinInternal)

}

private[process] object ProcessPlatformSpecific {

  type JProcess = JS.ChildProcess

  private def bufferToArray(buf: JS.Buffer): Array[Byte] =
    buf.values().toIterator.toArray.map(_.toByte)

  case class JSInputStream(stdout: JS.Readable, pause: Boolean = false) extends InputStream {

    if (stdout == null) throw CommandError.Error(new Throwable("stdout is null"))
    var readable            = false
    var buffer: Chunk[Byte] = Chunk.empty
    val ended               = false

    if (pause) {
      val node = js.Dynamic.global.require("process")
      node.nextTick { () =>
        stdout.resume()
        stdout.on(
          "readable",
          { (_: js.Dynamic) =>
            val buf = stdout.read()
            if (buf != null) {
              val chunk = Chunk.fromArray(bufferToArray(buf))
              buffer = buffer ++ chunk
            } else ()

          }
        )
      }

    } else {
      stdout.on(
        "readable",
        { (_: js.Dynamic) =>
          val buf = stdout.read()
          if (buf != null) {
            val chunk = Chunk.fromArray(bufferToArray(buf))
            buffer = buffer ++ chunk
          } else ()

        }
      )

    }

    override def read(): Int =
      if (buffer.isEmpty) {
        if (stdout.readableEnded) -1 else 0
      } else {
        val (b, tail) = buffer.splitAt(1)
        buffer = tail
        b.head.toInt
      }

    override def available(): Int = buffer.length

    override def close(): Unit = {
      stdout.destroy()
      ()
    }

    override def read(b: Array[Byte], off: Int = 0, len: Int): Int = {
      if (off < 0 || len < 0 || len > b.length - off)
        throw new IndexOutOfBoundsException

      if (buffer.isEmpty) {
        if (stdout.readableEnded) -1 else 0
      } else {
        val (toCopy, tail) = buffer.splitAt(len)
        var bytesWritten   = 0
        for (byte <- toCopy) {
          b(off + bytesWritten) = byte
          bytesWritten += 1
        }
        buffer = tail

        bytesWritten
      }

    }
    override def readAllBytes(): Array[Byte] = {
      val arr = buffer.toArray
      buffer = Chunk.empty
      arr
    }
  }

  case class JSOutputStream(val stdin: JS.Writable) extends OutputStream {

    override def write(b: Int): Unit = if (stdin.writable) {
      stdin.cork()
      stdin.write(Uint8Array.of(b.toShort))
      val process = js.Dynamic.global.require("process")
      process.nextTick(() => stdin.uncork())
      ()
    }

    override def write(b: Array[Byte], off: Int, len: Int): Unit = if (stdin.writable) {
      if (off < 0 || len < 0 || len > b.length - off)
        throw new IndexOutOfBoundsException()

      stdin.cork()
      stdin.write(new Uint8Array(b.map(_.toShort).toJSArray), null, { (_: js.Dynamic) => })
      val process = js.Dynamic.global.require("process")
      process.nextTick(() => stdin.uncork())
      ()

    }

    override def close(): Unit = if (stdin != null) {
      stdin.end()
      ()
    }

  }

  def wait(in: InputStream): ZIO[Any, Throwable, Unit] =
    in match {
      case JSInputStream(stdout, _) =>
        val p = new js.Promise[Unit]((resolve, _) =>
          if (stdout.readableEnded) resolve(()) else stdout.on("end", () => resolve(()))
        )
        ZIO.fromPromiseJS(p)
      case _                        => ZIO.unit
    }

}
