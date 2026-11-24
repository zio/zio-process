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

private[process] trait CommandErrorPlatformSpecific {

  /**
   * The exception that a failed program start reports. It is `java.io.IOException` on the JVM and Scala Native, and
   * `js.JavaScriptException` on Scala.js.
   */
  type IOException = java.io.IOException
  private val notFoundErrorCode         = 2
  private val permissionDeniedErrorCode = if (OS.os == OS.Windows) 5 else 13

  /**
   * Returns whether the `message` of a failed program start says that the program was not found. On the JVM and Scala
   * Native, this looks for errno 2. On Scala.js, it looks for `ENOENT`.
   */
  def isNotFound(message: String): Boolean = hasErrorCode(message, notFoundErrorCode)

  /**
   * Returns whether the `message` of a failed program start says that permission was denied. On Scala.js, this looks
   * for `EACCES`. On the JVM, this looks for error code 13 (5 on Windows). On Scala Native, it looks for error code 1
   * (5 on Windows).
   */
  def isPermissionDenied(message: String): Boolean = hasErrorCode(message, permissionDeniedErrorCode)

  // Older JDKs render the errno as `error=2,`, newer ones as `Exec failed, error: 2 (...)`.
  private def hasErrorCode(message: String, code: Int): Boolean =
    message != null && (message.contains(s"error=$code,") || message.contains(s"error: $code ("))
}
