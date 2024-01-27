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

import java.io.OutputStream
import zio.Trace
import java.io.IOException
import zio.ZIO
import zio.Chunk
import zio.Scope

private[process] case class ZSinkConstructor(outputStream: OutputStream) {

  private def fromOutputStream(
    os: => OutputStream
  )(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] = fromOutputStreamScoped(
    ZIO.succeed(os)
  )

  /**
   * Uses the provided `OutputStream` resource to create a [[ZSink]] that
   * consumes byte chunks and writes them to the `OutputStream`. The sink will
   * yield the count of bytes written.
   *
   * The `OutputStream` will be automatically closed after the stream is
   * finished or an error occurred.
   */
  private def fromOutputStreamScoped(
    os: => ZIO[Scope, IOException, OutputStream]
  )(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] =
    ZSink.unwrapScoped {
      os.map { out =>
        ZSink.foldLeftChunksZIO(0L) { (bytesWritten, byteChunk: Chunk[Byte]) =>
          ZIO.attemptBlockingInterrupt {
            val bytes = byteChunk.toArray
            out.write(bytes)
            bytesWritten + bytes.length
          }.refineOrDie { case e: IOException =>
            e
          }
        }
      }
    }

  def mk: ZSink[Any, IOException, Byte, Byte, Long] = fromOutputStream(outputStream)

}
