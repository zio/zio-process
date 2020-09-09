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
import java.nio.charset.{ Charset, StandardCharsets }

import zio.blocking.{ effectBlockingCancelable, Blocking }
import zio.stream.{ ZStream, ZTransducer }
import zio.{ Chunk, UIO, ZIO, ZManaged }

import scala.collection.mutable.ArrayBuffer

final case class ProcessStream(private val inputStream: InputStream) {

  /**
   * Return the output of this process as a list of lines (default encoding of UTF-8).
   */
  def lines: ZIO[Blocking, CommandError, Chunk[String]] = lines(StandardCharsets.UTF_8)

  /**
   * Return the output of this process as a list of lines with the specified encoding.
   */
  def lines(charset: Charset): ZIO[Blocking, CommandError, Chunk[String]] =
    ZManaged
      .fromAutoCloseable(UIO(new BufferedReader(new InputStreamReader(inputStream, charset))))
      .use { reader =>
        effectBlockingCancelable {
          val lines = new ArrayBuffer[String]

          var line: String = null
          while ({ line = reader.readLine; line != null })
            lines.append(line)

          Chunk.fromArray(lines.toArray)
        }(UIO(reader.close()))
      }
      .refineOrDie { case CommandThrowable.IOError(e) =>
        e
      }

  /**
   * Return the output of this process as a stream of lines (default encoding of UTF-8).
   */
  def linesStream: ZStream[Blocking, CommandError, String] =
    stream
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitLines)

  /**
   * Return the output of this process as a chunked stream of bytes.
   */
  def stream: ZStream[Blocking, CommandError, Byte] =
    ZStream.fromInputStream(inputStream).mapError(CommandError.IOError)

  /**
   * Return the entire output of this process as a string (default encoding of UTF-8).
   */
  def string: ZIO[Blocking, CommandError, String] = string(StandardCharsets.UTF_8)

  /**
   * Return the entire output of this process as a string with the specified encoding.
   */
  def string(charset: Charset): ZIO[Blocking, CommandError, String] =
    ZManaged
      .fromAutoCloseable(UIO(inputStream))
      .use_ {
        effectBlockingCancelable {
          val buffer = new Array[Byte](4096)
          val result = new ByteArrayOutputStream
          var length = 0

          while ({ length = inputStream.read(buffer); length != -1 })
            result.write(buffer, 0, length)

          new String(result.toByteArray, charset)
        }(UIO(inputStream.close()))
      }
      .refineOrDie { case CommandThrowable.IOError(e) =>
        e
      }
}
