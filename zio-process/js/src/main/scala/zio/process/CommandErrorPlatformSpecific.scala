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

import scala.scalajs.js

private[process] trait CommandErrorPlatformSpecific {

  /**
   * The exception that a failed program start reports. It is `java.io.IOException` on the JVM and Scala Native, and
   * `js.JavaScriptException` on Scala.js.
   */
  type IOException = js.JavaScriptException

  /**
   * Returns whether the `message` of a failed program start says that the program was not found. On the JVM and Scala
   * Native, this looks for errno 2. On Scala.js, it looks for `ENOENT`.
   */
  def isNotFound(message: String): Boolean = message != null && message.contains("ENOENT")

  /**
   * Returns whether the `message` of a failed program start says that permission was denied. On Scala.js, this looks
   * for `EACCES`. On the JVM and Scala Native, it looks for the errno code of a denied permission.
   */
  def isPermissionDenied(message: String): Boolean = message != null && message.contains("EACCES")
}
