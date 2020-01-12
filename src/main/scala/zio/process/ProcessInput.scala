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

import java.io.ByteArrayInputStream
import java.nio.charset.{ Charset, StandardCharsets }

import zio.Chunk
import zio.stream.{ Stream, StreamChunk }

final case class ProcessInput(source: Option[StreamChunk[Throwable, Byte]])

object ProcessInput {
  val inherit: ProcessInput = ProcessInput(None)

  /**
   * Returns a ProcessInput from an array of bytes.
   */
  def fromByteArray(bytes: Array[Byte]): ProcessInput =
    ProcessInput(Some(Stream.fromInputStream(new ByteArrayInputStream(bytes))))

  /**
   * Returns a ProcessInput from a stream of chunked bytes.
   */
  def fromStreamChunk(stream: StreamChunk[Throwable, Byte]): ProcessInput =
    ProcessInput(Some(stream))

  /**
   * Returns a ProcessInput from a String with the given charset.
   */
  def fromString(text: String, charset: Charset): ProcessInput =
    ProcessInput(Some(StreamChunk.fromChunks(Chunk.fromArray(text.getBytes(charset)))))

  /**
   * Returns a ProcessInput from a UTF-8 String.
   */
  def fromUTF8String(text: String): ProcessInput =
    ProcessInput(Some(StreamChunk.fromChunks(Chunk.fromArray(text.getBytes(StandardCharsets.UTF_8)))))
}
