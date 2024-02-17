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

import java.io.InputStream
import java.io.OutputStream
import zio.ZIO
import java.io.PushbackInputStream
import scala.annotation.nowarn

private[process] trait ProcessPlatformSpecific { self: Process =>

  import ProcessPlatformSpecific._

  protected def waitForUnsafe: Int = self.process.waitFor()

  protected def isAliveUnsafe: Boolean          = self.process.isAlive()
  protected def destroyUnsafe(): Unit           = self.process.destroy()
  protected def destroyForciblyUnsafe: JProcess = self.process.destroyForcibly()

  protected def pidUnsafe: Long = findFirstNumber(self.process.toString()).toLong

  private def findFirstNumber(str: String): String =
    str.headOption match {
      case None    => ""
      case Some(c) => if (c.isDigit) getFirstNumber(str) else findFirstNumber(str.tail)
    }

  private def getFirstNumber(str: String): String =
    str.headOption match {
      case None    => ""
      case Some(c) => if (!c.isDigit) "" else s"$c${getFirstNumber(str.tail)}"
    }

  protected def getInputStream: InputStream = self.process.getInputStream()
  def getOutputStream: OutputStream         = self.process.getOutputStream()
  protected def getErrorStream: InputStream = self.process.getErrorStream()
  protected def get: Option[OutputStream]   = Some(getOutputStream)

}

private[process] object ProcessPlatformSpecific {

  type JProcess = java.lang.Process

  @nowarn
  def wait(in: InputStream): ZIO[Any, Throwable, Unit] = ZIO.unit

  /**
   * Connect a Java `InputStream` to the standard input of the process.
   */
  def connectJavaStream(process: Process, stream: InputStream, flush: Boolean): ZIO[Any, CommandError, Unit] =
    for {
      outputStream <- process.execute(_.getOutputStream)
      s             = new PushbackInputStream(stream)
      _            <- loop {
                        val r = s.read()
                        if (r > 0) {
                          s.unread(r)
                          true
                        } else false
                      }(
                        for {
                          _ <- ZIO.attemptBlockingInterrupt {
                                 s.transferTo(outputStream)
                                 if (flush) outputStream.flush()
                               }.mapError(CommandThrowable.classify)
                        } yield ()
                      ).ensuring(ZIO.succeed {
                        s.close()
                        outputStream.close()
                      }).orDie
      _            <- ZIO.succeed(s.close())
      _            <- ZIO.succeed(stream.close())
      _            <- ZIO.succeed(outputStream.close())
    } yield ()

  private def loop[A](cond: => Boolean)(z: ZIO[Any, CommandError, A]): ZIO[Any, CommandError, Unit] =
    for {
      bool <- ZIO.attemptBlockingInterrupt(cond).mapError(CommandThrowable.classify)
      _    <- if (bool) z *> loop(cond)(z) else ZIO.unit
    } yield ()

}
