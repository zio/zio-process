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

import java.io._
import java.lang.{ Process => JProcess }

import zio.blocking._
import zio.{ RIO, UIO, ZIO }

final case class Process(private val process: JProcess) {

  /**
   * Access the standard output stream.
   */
  val stdout: ProcessStream = ProcessStream(process.getInputStream)

  /**
   * Access the standard error stream.
   */
  val stderr: ProcessStream = ProcessStream(process.getErrorStream)

  /**
   * Access the underlying Java Process wrapped in a blocking ZIO.
   */
  def execute[T](f: JProcess => T): ZIO[Blocking, IOException, T] =
    effectBlockingInterrupt(f(process)).refineToOrDie[IOException]

  /**
   * Return the exit code of this process.
   */
  def exitCode: RIO[Blocking, Int] =
    effectBlockingCancelable(process.waitFor())(UIO(process.destroy()))
}
