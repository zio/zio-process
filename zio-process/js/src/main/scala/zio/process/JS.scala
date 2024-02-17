package zio.process

import scala.scalajs.js
import scala.annotation.nowarn

@nowarn
object JS {

  @js.native
  trait EventEmitter extends js.Object {
    def on(eventName: String, listener: js.Function): this.type = js.native
  }

  @js.native
  trait ChildProcess extends EventEmitter {

    val stdin: Writable    = js.native
    val stdout: Readable   = js.native
    val stderr: Readable   = js.native
    def pid: js.Any        = js.native
    def connected: Boolean = js.native
    def kill(): Boolean    = js.native
    def exitCode: Int      = js.native

  }

  @js.native
  trait FileHandle extends js.Object

  @js.native
  trait Readable extends EventEmitter {
    def read(): Buffer                        = js.native
    def read(size: Int): Buffer               = js.native
    def pause(): this.type                    = js.native
    def isPaused(): Boolean                   = js.native
    def resume(): this.type                   = js.native
    def readableEnded: Boolean                = js.native
    def destroy(): Readable                   = js.native
    def destroyed: Boolean                    = js.native
    def pipe(destination: Writable): Writable = js.native
    def push(buffer: JS.Buffer): Boolean      = js.native
  }

  @js.native
  trait Writable extends EventEmitter {
    def write(b: js.typedarray.Uint8Array): Boolean                                          = js.native
    def write(b: String): Boolean                                                            = js.native
    def write(b: js.Any): Boolean                                                            = js.native
    def write(b: js.typedarray.Uint8Array, encoding: String, callback: js.Function): Boolean = js.native
    def end(): Writable                                                                      = js.native
    def writable: Boolean                                                                    = js.native
    def writableEnded: Boolean                                                               = js.native
    def cork(): js.Any                                                                       = js.native
    def uncork(): js.Any                                                                     = js.native
  }

  @js.native
  trait Buffer extends js.Object {
    def values(): js.Iterator[Short] = js.native
    def subarray(start: Int): Buffer = js.native
  }

}
