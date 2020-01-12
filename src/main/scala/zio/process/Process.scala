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
import java.nio.charset.{ Charset, StandardCharsets }

import zio.blocking._
import zio.stream.{ Stream, StreamChunk, ZSink, ZStream }
import zio.{ RIO, UIO, ZIO, ZManaged }

import scala.collection.mutable.ArrayBuffer

final case class Process(private val process: JProcess) {

  /**
   * Access the underlying Java Process wrapped in a blocking ZIO.
   */
  def execute[T](f: JProcess => T): ZIO[Blocking, IOException, T] =
    effectBlocking(f(process)).refineToOrDie[IOException]

  /**
   * Return the exit code of this process.
   */
  def exitCode: RIO[Blocking, Int] =
    effectBlockingCancelable(process.waitFor())(UIO(process.destroy()))

  /**
   * Return the output of this process as a list of lines (default encoding of UTF-8).
   */
  def lines: RIO[Blocking, List[String]] = lines(StandardCharsets.UTF_8)

  /**
   * Return the output of this process as a list of lines with the specified encoding.
   */
  def lines(charset: Charset): RIO[Blocking, List[String]] =
    ZManaged.fromAutoCloseable(UIO(new BufferedReader(new InputStreamReader(process.getInputStream, charset)))).use {
      reader =>
        effectBlockingCancelable {
          val lines = new ArrayBuffer[String]

          var line: String = null
          while ({ line = reader.readLine; line != null }) {
            lines.append(line)
          }

          lines.toList
        }(UIO(reader.close()))
    }

  /**
   * Return the output of this process as a stream of lines (default encoding of UTF-8).
   */
  def linesStream: ZStream[Blocking, Throwable, String] =
    stream.chunks
      .aggregate(ZSink.utf8DecodeChunk)
      .aggregate(ZSink.splitLines)
      .mapConcatChunk(identity)

  /**
   * Return the output of this process as a chunked stream of bytes.
   */
  def stream: StreamChunk[Throwable, Byte] =
    Stream.fromInputStream(process.getInputStream)

  /**
   * Return the entire output of this process as a string (default encoding of UTF-8).
   */
  def string: RIO[Blocking, String] = string(StandardCharsets.UTF_8)

  /**
   * Return the entire output of this process as a string with the specified encoding.
   */
  def string(charset: Charset): RIO[Blocking, String] =
    ZManaged.fromAutoCloseable(UIO(process.getInputStream())).use { inputStream =>
      effectBlockingCancelable {
        val buffer = new Array[Byte](4096)
        val result = new ByteArrayOutputStream
        var length = 0

        while ({ length = inputStream.read(buffer); length != -1 }) {
          result.write(buffer, 0, length)
        }

        result.toString(charset)
      }(UIO(inputStream.close()))
    }
}
