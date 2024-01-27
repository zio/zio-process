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

import zio.stream.ZStream
import zio.{ Chunk, Queue }

import java.io.ByteArrayInputStream
import java.nio.charset.{ Charset, StandardCharsets }
import java.io.File
import java.nio.file.Path
import java.io.InputStream

sealed trait ProcessInput

object ProcessInput {
  final case class FromStream(stream: ZStream[Any, CommandError, Byte], flushChunksEagerly: Boolean)
      extends ProcessInput
  final case class JavaStream(stream: InputStream, flushChunksEagerly: Boolean) extends ProcessInput
  case object Inherit                                                           extends ProcessInput
  case object Pipe                                                              extends ProcessInput

  /**
   * Returns a ProcessInput from a file.
   */
  def fromFile(file: File, chunkSize: Int = ZStream.DefaultChunkSize): ProcessInput =
    ProcessInput.FromStream(
      ZStream.fromFile(file, chunkSize).refineOrDie { case CommandThrowable.IOError(e) => e },
      flushChunksEagerly = false
    )

  /**
   * Returns a ProcessInput from a path to a file.
   */
  def fromPath(path: Path, chunkSize: Int = ZStream.DefaultChunkSize): ProcessInput =
    ProcessInput.FromStream(
      ZStream.fromPath(path, chunkSize).refineOrDie { case CommandThrowable.IOError(e) => e },
      flushChunksEagerly = false
    )

  /**
   * Returns a ProcessInput from an array of bytes.
   */
  def fromByteArray(bytes: Array[Byte]): ProcessInput =
    ProcessInput.FromStream(
      ZStream.fromInputStream(new ByteArrayInputStream(bytes)).mapError(CommandError.IOError.apply),
      flushChunksEagerly = false
    )

  /**
   * Returns a ProcessInput from a queue of bytes to send to the process in a controlled manner.
   */
  def fromQueue(queue: Queue[Chunk[Byte]]): ProcessInput =
    ProcessInput.fromStream(ZStream.fromQueue(queue).flattenChunks, flushChunksEagerly = true)

  /**
   * Returns a ProcessInput from a stream of bytes.
   *
   * You may want to set `flushChunksEagerly` to true when doing back-and-forth communication with a process such as
   * interacting with a repl (flushing the command to send so that you can receive a response immediately).
   */
  def fromStream(stream: ZStream[Any, CommandError, Byte], flushChunksEagerly: Boolean = false): ProcessInput =
    ProcessInput.FromStream(stream, flushChunksEagerly)

  /**
   * Returns a ProcessInput from a String with the given charset.
   */
  def fromString(text: String, charset: Charset): ProcessInput =
    ProcessInput.FromStream(ZStream.fromChunks(Chunk.fromArray(text.getBytes(charset))), flushChunksEagerly = false)

  /**
   * Returns a ProcessInput from a UTF-8 String.
   */
  def fromUTF8String(text: String): ProcessInput =
    ProcessInput.FromStream(
      ZStream.fromChunks(Chunk.fromArray(text.getBytes(StandardCharsets.UTF_8))),
      flushChunksEagerly = false
    )

}
