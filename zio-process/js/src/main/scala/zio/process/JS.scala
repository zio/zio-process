package zio.process

import scala.scalajs.js
import scala.annotation.nowarn

/**
 * Scala.js facades for the parts of the Node.js API that this library uses. Each trait mirrors a Node.js type of the
 * same name, and its members have the names and behaviour of the Node.js members. See the Node.js documentation for the
 * full API.
 */
@nowarn
object JS {

  /**
   * A Node.js `EventEmitter`: an object that calls the listeners registered for named events.
   */
  @js.native
  trait EventEmitter extends js.Object {

    /**
     * Registers `listener` to be called each time the event named `eventName` is emitted. Returns this object.
     */
    def on(eventName: String, listener: js.Function): this.type = js.native
  }

  /**
   * A Node.js `ChildProcess`, as returned when a program is started. It emits events while the child process runs.
   */
  @js.native
  trait ChildProcess extends EventEmitter {

    /**
     * The standard input of the child process, as a writable stream.
     */
    val stdin: Writable = js.native

    /**
     * The standard output of the child process, as a readable stream.
     */
    val stdout: Readable = js.native

    /**
     * The standard error of the child process, as a readable stream.
     */
    val stderr: Readable = js.native

    /**
     * The process ID of the child process. Node.js reports it as undefined when the process failed to start.
     */
    def pid: js.Any = js.native

    /**
     * Whether the child process still has an open IPC channel to the parent process.
     */
    def connected: Boolean = js.native

    /**
     * Sends a signal to the child process. Returns true when the signal was sent.
     */
    def kill(): Boolean = js.native

    /**
     * The exit code of the child process. It is set once the process has exited.
     */
    def exitCode: Int = js.native

  }

  /**
   * A Node.js file handle. This facade declares no members.
   */
  @js.native
  trait FileHandle extends js.Object

  /**
   * A Node.js readable stream.
   */
  @js.native
  trait Readable extends EventEmitter {

    /**
     * Reads the buffered data as a `Buffer`, or returns null when no data is buffered.
     */
    def read(): Buffer = js.native

    /**
     * Reads `size` bytes of the buffered data as a `Buffer`, or returns null when not enough data is buffered.
     */
    def read(size: Int): Buffer = js.native

    /**
     * Pauses the stream, so that it stops emitting `data` events. Returns this stream.
     */
    def pause(): this.type = js.native

    /**
     * Whether the stream is paused.
     */
    def isPaused(): Boolean = js.native

    /**
     * Resumes a paused stream, so that it emits data again. Returns this stream.
     */
    def resume(): this.type = js.native

    /**
     * Whether the `end` event has been emitted.
     */
    def readableEnded: Boolean = js.native

    /**
     * Destroys the stream and releases its resources. Returns this stream.
     */
    def destroy(): Readable = js.native

    /**
     * Whether the stream has been destroyed.
     */
    def destroyed: Boolean = js.native

    /**
     * Connects this stream to `destination`, so that the data read from this stream is written to it. Returns
     * `destination`.
     */
    def pipe(destination: Writable): Writable = js.native

    /**
     * Adds a chunk of data to the readable side of the stream. Returns false when the caller should stop pushing data.
     */
    def push(buffer: JS.Buffer): Boolean = js.native
  }

  /**
   * A Node.js writable stream.
   */
  @js.native
  trait Writable extends EventEmitter {

    /**
     * Writes the bytes of `b` to the stream. Returns false when the stream is full, and the caller should wait for its
     * `drain` event.
     */
    def write(b: js.typedarray.Uint8Array): Boolean = js.native

    /**
     * Writes the text `b` to the stream, encoded as UTF-8 by default. Returns false when the stream is full, and the
     * caller should wait for its `drain` event.
     */
    def write(b: String): Boolean = js.native

    /**
     * Writes `b` to the stream. Returns false when the stream is full, and the caller should wait for its `drain`
     * event.
     */
    def write(b: js.Any): Boolean = js.native

    /**
     * Writes the bytes of `b`, using `encoding`, and calls `callback` once the chunk has been handled. Returns false
     * when the stream is full.
     */
    def write(b: js.typedarray.Uint8Array, encoding: String, callback: js.Function): Boolean = js.native

    /**
     * Ends the stream. No data can be written after this call. Returns this stream.
     */
    def end(): Writable = js.native

    /**
     * Whether the stream can still be written to.
     */
    def writable: Boolean = js.native

    /**
     * Whether `end` has been called on the stream.
     */
    def writableEnded: Boolean = js.native

    /**
     * Buffers writes in memory until `uncork` is called.
     */
    def cork(): js.Any = js.native

    /**
     * Writes the data that was buffered since `cork` was called.
     */
    def uncork(): js.Any = js.native
  }

  /**
   * A Node.js `Buffer` of bytes.
   */
  @js.native
  trait Buffer extends js.Object {

    /**
     * Returns an iterator over the bytes of the buffer, as values from 0 to 255.
     */
    def values(): js.Iterator[Short] = js.native

    /**
     * Returns a view of the bytes from index `start` to the end of the buffer.
     */
    def subarray(start: Int): Buffer = js.native
  }

}
