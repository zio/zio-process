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
  type IOException = java.io.IOException
  private val notFoundErrorCode         = 2
  val notFound                          = s"error=$notFoundErrorCode,"
  private val permissionDeniedErrorCode = if (OS.os == OS.Windows) 5 else 1
  val permissionDenied                  = s"error=$permissionDeniedErrorCode,"
}
