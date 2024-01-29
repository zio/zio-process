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

import scala.jdk.CollectionConverters._

private[process] trait ProcessPlatformSpecific { self: Process =>

  /**
   * Access the standard output stream.
   */
  val stdout: ProcessStream = ProcessStream(self.process.getInputStream(), None)

  /**
   * Access the standard error stream.
   */
  val stderr: ProcessStream = ProcessStream(self.process.getErrorStream(), None)

  /**
   * Kills the entire process tree and will wait until completed. Equivalent to SIGTERM on Unix platforms.
   *
   * Note: This method requires JDK 9+
   */
  def killTree: ZIO[Any, CommandError, Unit] =
    self.execute { process =>
      val descendants = process.descendants().iterator().asScala.toSeq
      descendants.foreach(_.destroy())

      process.destroy()
      process.waitFor()

      descendants.foreach { p =>
        if (p.isAlive) {
          p.onExit().get // `ProcessHandle` doesn't have waitFor
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
      val descendants = process.descendants().iterator().asScala.toSeq
      descendants.foreach(_.destroyForcibly())

      process.destroyForcibly()
      process.waitFor()

      descendants.foreach { p =>
        if (p.isAlive) {
          p.onExit().get // `ProcessHandle` doesn't have waitFor
          ()
        }
      }
    }

  /**
   * Returns the native process ID of the process.
   *
   * Note: This method requires JDK 9+
   */
  def pid: ZIO[Any, CommandError, Long] =
    self.execute { process =>
      process.pid()
    }

}
