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
import zio.Trace
import java.io.IOException
import zio.ZIO
import zio.Chunk
import zio.Scope
import java.io.InputStream

private[process] object Constructors {

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

  def zsink(outputStream: OutputStream): ZSink[Any, IOException, Byte, Byte, Long] = fromOutputStream(outputStream)

  /**
   * Creates a stream from a `java.io.InputStream`
   */
  def fromInputStream(
    is: => InputStream,
    chunkSize: => Int = ZStream.DefaultChunkSize
  )(implicit trace: Trace): ZStream[Any, IOException, Byte] =
    ZStream.succeed((is, chunkSize)).flatMap { case (is, chunkSize) =>
      ZStream.repeatZIOChunkOption {
        for {
          bufArray  <- ZIO.succeed(Array.ofDim[Byte](chunkSize))
          bytesRead <- ZIO
                         .attemptBlockingCancelable(is.read(bufArray))(ZIO.succeed(is.close()))
                         .refineToOrDie[java.io.IOException]
                         .asSomeError
          bytes     <- if (bytesRead < 0)
                         ZIO.fail(None)
                       else if (bytesRead == 0)
                         ZIO.succeed(Chunk.empty)
                       else if (bytesRead < chunkSize)
                         ZIO.succeed(Chunk.fromArray(bufArray).take(bytesRead))
                       else
                         ZIO.succeed(Chunk.fromArray(bufArray))
        } yield bytes
      }
    }

}
