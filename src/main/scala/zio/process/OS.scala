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

import java.util.Locale

private[process] sealed trait OS

private[process] object OS {
  case object MacOS   extends OS
  case object Windows extends OS
  case object Linux   extends OS
  case object Other   extends OS

  lazy val os: OS = {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)

    if (osName.contains("mac") || osName.contains("darwin"))
      OS.MacOS
    else if (osName.contains("win"))
      OS.Windows
    else if (osName.contains("nux"))
      OS.Linux
    else
      OS.Other
  }
}
