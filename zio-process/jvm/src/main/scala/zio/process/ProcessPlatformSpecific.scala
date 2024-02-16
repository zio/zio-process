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
import scala.jdk.CollectionConverters._
import zio.ZIO
import scala.annotation.nowarn

private[process] trait ProcessPlatformSpecific { self: Process =>

  import ProcessPlatformSpecific._

  def waitForUnsafe: Int = self.process.waitFor()

  def isAliveUnsafe: Boolean          = self.process.isAlive()
  def destroyUnsafe(): Unit           = self.process.destroy()
  def destroyForciblyUnsafe: JProcess = self.process.destroyForcibly()

  def pidUnsafe: Long = self.process.pid

  def getInputStream: InputStream   = self.process.getInputStream()
  def getOutputStream: OutputStream = self.process.getOutputStream()
  def getErrorStream: InputStream   = self.process.getErrorStream()
  def get: Option[OutputStream]     = None

  def destroyHandle(handle: ProcessHandle): Boolean         = handle.destroy()
  def destroyForciblyHandle(handle: ProcessHandle): Boolean = handle.destroyForcibly()
  def isAliveHandle(handle: ProcessHandle): Boolean         = handle.isAlive()
  def onExitHandle(handle: ProcessHandle)                   = handle.onExit()

  /**
   * Kills the entire process tree and will wait until completed. Equivalent to SIGTERM on Unix platforms.
   *
   * Note: This method requires JDK 9+
   */
  def killTree: ZIO[Any, CommandError, Unit] =
    self.execute { process =>
      val d = process.descendants().toList().asScala
      d.foreach { p =>
        destroyHandle(p)
        ()
      }

      destroyUnsafe()
      waitForUnsafe

      d.foreach { p =>
        if (isAliveHandle(p)) {
          onExitHandle(p).get // `ProcessHandle` doesn't have waitFor
          ()
        }
      }
    }

  /**
   * Kills the entire process tree and will wait until completed. Equivalent to SIGKILL on Unix platforms.
   *
   * Note: This method requires JDK 9+
   */
  def killTreeForcibly: ZIO[Any, CommandError, Unit] =
    self.execute { process =>
      val d = process.descendants().toList().asScala
      d.foreach { p =>
        destroyForciblyHandle(p)
        ()
      }

      destroyForciblyUnsafe
      waitForUnsafe

      d.foreach { p =>
        if (isAliveHandle(p)) {
          onExitHandle(p).get // `ProcessHandle` doesn't have waitFor
          ()
        }
      }
    }
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
      _            <- loop {
                        stream.transferTo(outputStream) > 0
                      }(
                        for {
                          _ <- ZIO.attemptBlockingInterrupt {
                                 if (flush) outputStream.flush()
                               }.mapError(CommandThrowable.classify)
                        } yield ()
                      ).ensuring(ZIO.succeed {
                        outputStream.close()
                      }).orDie
      _            <- ZIO.succeed(stream.close())
      _            <- ZIO.succeed(outputStream.close())
    } yield ()

  private def loop[A](cond: => Boolean)(z: ZIO[Any, CommandError, A]): ZIO[Any, CommandError, Unit] =
    for {
      bool <- ZIO.attemptBlockingInterrupt(cond).mapError(CommandThrowable.classify)
      _    <- if (bool) z *> loop(cond)(z) else ZIO.unit
    } yield ()

}
