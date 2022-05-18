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

import zio.ZIO.attemptBlockingCancelable
import zio.stream.{ ZPipeline, ZStream }
import zio.{ Chunk, ZIO }

import java.io._
import java.nio.charset.{ Charset, StandardCharsets }
import scala.collection.mutable.ArrayBuffer

final case class ProcessStream(private val inputStream: InputStream) {

  /**
   * Return the output of this process as a list of lines (default encoding of UTF-8).
   */
  def lines: ZIO[Any, CommandError, Chunk[String]] = lines(StandardCharsets.UTF_8)

  /**
   * Return the output of this process as a list of lines with the specified encoding.
   */
  def lines(charset: Charset): ZIO[Any, CommandError, Chunk[String]] =
    ZIO.scoped {
      for {
        reader <- ZIO.fromAutoCloseable(ZIO.succeed(new BufferedReader(new InputStreamReader(inputStream, charset))))
        chunks <- attemptBlockingCancelable {
                    val lines = new ArrayBuffer[String]

                    var line: String = null
                    while ({ line = reader.readLine; line != null })
                      lines.append(line)

                    Chunk.fromArray(lines.toArray)
                  }(ZIO.succeed(reader.close()))
      } yield chunks
    }.refineOrDie { case CommandThrowable.IOError(e) =>
      e
    }

  /**
   * Return the output of this process as a stream of lines (default encoding of UTF-8).
   */
  def linesStream: ZStream[Any, CommandError, String] =
    stream
      .via(
        ZPipeline.fromChannel(ZPipeline.utf8Decode.channel.orDie)
      )
      .via(ZPipeline.splitLines)

  /**
   * Return the output of this process as a chunked stream of bytes.
   */
  def stream: ZStream[Any, CommandError, Byte] =
    ZStream.fromInputStream(inputStream).mapError(CommandError.IOError.apply)

  /**
   * Return the entire output of this process as a string (default encoding of UTF-8).
   */
  def string: ZIO[Any, CommandError, String] = string(StandardCharsets.UTF_8)

  /**
   * Return the entire output of this process as a string with the specified encoding.
   */
  def string(charset: Charset): ZIO[Any, CommandError, String] =
    attemptBlockingCancelable {
      val buffer = new Array[Byte](4096)
      val result = new ByteArrayOutputStream
      var length = 0

      while ({ length = inputStream.read(buffer); length != -1 })
        result.write(buffer, 0, length)

      new String(result.toByteArray, charset)
    }(ZIO.succeed(inputStream.close())).refineOrDie { case CommandThrowable.IOError(e) =>
      e
    }
}
