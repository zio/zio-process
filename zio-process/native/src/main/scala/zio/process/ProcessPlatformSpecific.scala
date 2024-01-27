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

private[process] trait ProcessPlatformSpecific { self: Process =>

  /**
   * Access the standard output stream.
   */
  val stdout: ProcessStream = ProcessStream(self.process.getInputStream(), Some(self.process.getOutputStream()))

  /**
   * Access the standard error stream.
   */
  val stderr: ProcessStream = ProcessStream(self.process.getErrorStream(), Some(self.process.getOutputStream()))

  /**
   * Returns the native process ID of the process.
   *
   * Note: This method needs to be implemented in scala-native `java.lang.Process`.
   * Until then, this works.
   */
  def pid: ZIO[Any, CommandError, Long] =
    self.execute { process =>
      findFirstNumber(process.toString()).toLong
    }

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

}
