package org.dweb_browser.core.ipc.helper

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.ByteReadPacket
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.dweb_browser.helper.PromiseOut
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.toUtf8ByteArray
import org.dweb_browser.core.http.PureStream

fun debugStream(tag: String, msg: Any = "", err: Throwable? = null) = println("$tag $msg")
//  printDebug("stream", tag, msg, err)

/**
 * 模拟Web的 ReadableStream
 */
class ReadableStream(
  cid: String? = null,
  val onOpenReader: suspend CoroutineScope.(arg: ReadableStreamController) -> Unit = {},
  val onClose: suspend CoroutineScope.() -> Unit = {},
  val onStart: CoroutineScope.(controller: ReadableStreamController) -> Unit = {},
) {
  val scope = CoroutineScope(CoroutineName("readableStream") + ioAsyncExceptionHandler)

  /**
   * 内部的输出器
   */
  private val _stream = ByteChannel(true)

  val stream: PureStream = PureStream(object : ByteReadChannel by _stream {
    override fun cancel(cause: Throwable?): Boolean {
      return this@ReadableStream.closeRead(cause)
    }
  })

  private val controller = ReadableStreamController(this)
  private val closePo = PromiseOut<Unit>()

  class ReadableStreamController(
    val stream: ReadableStream,
  ) {
    private val lock = Mutex()
    suspend fun enqueue(vararg byteArrays: ByteArray) = lock.withLock {
      try {
        for (byteArray in byteArrays) {
          stream._stream.writePacket(ByteReadPacket(byteArray))
        }
        true
      } catch (e: Throwable) {
        false
      }
    }

    suspend fun enqueue(byteArray: ByteArray) = lock.withLock {
      try {
        stream._stream.writePacket(ByteReadPacket(byteArray))
        true
      } catch (e: Throwable) {
        false
      }
    }

    suspend fun enqueue(data: String) = enqueue(data.toUtf8ByteArray())

    fun enqueueBackground(byteArray: ByteArray) = stream.scope.launch {
      enqueue(byteArray)
    }

    fun enqueueBackground(data: String) = enqueueBackground(data.toUtf8ByteArray())

    fun closeWrite(cause: Throwable? = null) = stream.closeWrite(cause)
    fun awaitClose(onClosed: suspend () -> Unit) {
      stream.scope.launch {
        stream.waitClosed()
        onClosed()
      }
    }
  }

  suspend fun waitClosed() = closePo.waitPromise()
  private fun emitClose() = scope.launch {
    if (!closePo.isResolved) {
      closePo.resolve(Unit)
      onClose(this)
    }
  }


  fun closeRead(reason: Throwable? = null) =
    _stream.cancel(reason).also {
      emitClose()
    }

  private fun closeWrite(cause: Throwable? = null) =
    _stream.close(cause).also {
      emitClose()
    }


  companion object {
    private var id_acc by atomic(1)
  }

  val uid = "#s${id_acc++}${if (cid != null) "($cid)" else ""}"

  override fun toString() = uid


  /// 生命周期
  init {
    scope.onStart(controller)
    scope.launch {
      stream.afterOpened.await()
      onOpenReader(controller)
    }
  }
}

class ReadableStreamOut {
  private lateinit var _controller: ReadableStream.ReadableStreamController
  val stream = ReadableStream {
    _controller = it
  }
  val controller get() = _controller
}