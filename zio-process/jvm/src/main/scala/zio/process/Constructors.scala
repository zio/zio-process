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

import zio.stream.ZSink
import zio.stream.ZStream

import java.io.OutputStream
import java.io.InputStream
import zio.Trace
import java.io.IOException

private[process] object Constructors {

  def zsink(outputStream: OutputStream) = ZSink.fromOutputStream(outputStream)

  /**
   * Creates a stream from a `java.io.InputStream`
   */
  def fromInputStream(
    is: => InputStream,
    chunkSize: => Int = ZStream.DefaultChunkSize
  )(implicit trace: Trace): ZStream[Any, IOException, Byte] = ZStream.fromInputStream(is, chunkSize)

}
