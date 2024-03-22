package org.dweb_browser.core.ipc

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readIntLittleEndian
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.dweb_browser.core.help.types.IMicroModuleManifest
import org.dweb_browser.core.ipc.helper.IpcMessage
import org.dweb_browser.core.ipc.helper.IpcPoolMessageArgs
import org.dweb_browser.core.ipc.helper.IpcPoolPack
import org.dweb_browser.core.ipc.helper.IpcPoolPackString
import org.dweb_browser.core.ipc.helper.PackIpcMessage
import org.dweb_browser.core.ipc.helper.ReadableStream
import org.dweb_browser.core.ipc.helper.cborToIpcMessage
import org.dweb_browser.core.ipc.helper.cborToIpcPoolPack
import org.dweb_browser.core.ipc.helper.ipcMessageToCbor
import org.dweb_browser.core.ipc.helper.ipcPoolPackToCbor
import org.dweb_browser.core.ipc.helper.ipcPoolPackToJson
import org.dweb_browser.core.ipc.helper.jsonToIpcPoolPack
import org.dweb_browser.helper.Debugger
import org.dweb_browser.helper.SimpleEventFlow
import org.dweb_browser.helper.canRead
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.readByteArray
import org.dweb_browser.helper.toLittleEndianByteArray
import org.dweb_browser.pure.http.PureStream


val debugStreamIpc = Debugger("stream-ipc")

/**
 * 基于 WebReadableStream 的IPC
 *
 * 它会默认构建出一个输出流，
 * 以及需要手动绑定输入流 {@link bindIncomeStream}
 */
class ReadableStreamIpc(
  channelId: String,
  override val remote: IMicroModuleManifest,
  endpoint: IpcPool
) : Ipc(channelId, endpoint) {
  companion object {
    val incomeStreamCoroutineScope =
      CoroutineScope(CoroutineName("income-stream") + ioAsyncExceptionHandler)
  }

  override fun toString(): String {
    return super.toString() + "@ReadableStreamIpc($channelId)"
  }
  // 虽然 ReadableStreamIpc 支持 Binary 的传输，但是不支持结构化的传输，
  // override val supportBinary: Boolean = true

  private lateinit var controller: ReadableStream.ReadableStreamController

  val input = ReadableStream(cid = "channelId=$channelId") {
    controller = it
  }

  private suspend fun enqueue(data: ByteArray) = controller.enqueue(data)
  private suspend fun enqueue(vararg dataArray: ByteArray) = controller.enqueue(*dataArray)

  private var _incomeStream: PureStream? = null

  class AbortAble(scope: CoroutineScope) {
    val signal = SimpleEventFlow(scope)
  }

  private var reader: ByteReadChannel? = null
  private val _lock = SynchronizedObject()

  // 判断流绑定
  val isBinding = reader != null

  /**
   * 输入流要额外绑定
   */
  fun bindIncomeStream(stream: PureStream, signal: AbortAble = AbortAble(ipcScope)) =
    synchronized(_lock) {
      if (this._incomeStream != null) {
        throw Exception("${this.channelId}[${this.remote.mmid}] in come stream already binding.");
      }
      this._incomeStream = stream
      if (isClosed) {
        throw Exception("")
      }
      val reader = stream.getReader("ReadableStreamIpc bindIncomeStream").also { this.reader = it }
      val offAbort = signal.signal.listen {
        debugStreamIpc("ReadableStreamIpc", "readStream close")
        reader.cancel()
      }

      val readStream = suspend {
        try {
          while (reader.canRead) {
            val size = reader.readIntLittleEndian()
            if (size <= 0) {
              continue
            }
//          debugStreamIpc("bindIncomeStream", "size=$size => $stream")
            // 读取指定数量的字节并从中生成字节数据包。 如果通道已关闭且没有足够的可用字节，则失败
            val chunk = reader.readPacket(size)
//          debugStreamIpc(
//            "bindIncomeStream",
//            "supportCbor=$supportCbor,chunk=${chunk.remaining} => $stream"
//          )
            // 判断特殊的字节
            val byteArray = chunk.readByteArray()
            if (supportCbor) {
              byteFactory(byteArray)
            } else {
              stringFactory(byteArray)
            }
          }
          debugStreamIpc("END", "$stream")
        } catch (e: Exception) {
          debugStreamIpc("ReadableStreamIpc", "output stream closed:${e.message}")
        }
        // 流是双向的，对方关闭的时候，自己也要关闭掉
        this.close()
      }
      /// 后台执行数据拉取
      incomeStreamCoroutineScope.launch {
        readStream()
        offAbort()
      }
      this
    }

  // 处理二进制消息
  private suspend fun byteFactory(byteArray: ByteArray) {
    val pack = cborToIpcPoolPack(byteArray)
    val message = cborToIpcMessage(pack.messageByteArray, this@ReadableStreamIpc)
//    val logMessage = message.toString().trim()
//    debugStreamIpc("bindIncomeStream", "message=$logMessage => pid: ${pack.pid}")
    // 收消息 分发出去
    endpoint.emitMessage(
      IpcPoolMessageArgs(
        IpcPoolPack(pack.pid, message),
        this
      )
    )
  }

  private suspend fun stringFactory(byteArray: ByteArray) {
    val data = byteArray.decodeToString()
    val pack = Json.decodeFromString<IpcPoolPackString>(data)
    val message = jsonToIpcPoolPack(pack.ipcMessage, this@ReadableStreamIpc)
//    val logMessage = message.toString().trim()
//    debugStreamIpc("bindIncomeStream", "message=$logMessage => $pack")
    // 收消息 分发出去
    endpoint.emitMessage(
      IpcPoolMessageArgs(
        IpcPoolPack(pack.pid, message),
        this
      )
    )

  }

  override suspend fun doPostMessage(pid: Int, data: IpcMessage) {
    if (supportCbor) {
      val message = ipcMessageToCbor(data)
      val pack = ipcPoolPackToCbor(PackIpcMessage(pid, message))
//      debugStreamIpc("post supportCbor", "${message.size} => $input => $data")
      enqueue(
        pack.size.toLittleEndianByteArray(),
        pack
      )// 必须合并起来发送，否则中间可能插入其他写入
      return
    }

    val message = ipcPoolPackToJson(IpcPoolPack(pid, data)).encodeToByteArray()
//    debugStreamIpc("post", "${message.size} => $input => $data")
    enqueue(message.size.toLittleEndianByteArray(), message)// 必须合并起来发送，否则中间可能插入其他写入
  }

  override suspend fun _doClose(): Unit = synchronized(_lock) {
    controller.closeWrite()
    reader?.isClosedForWrite.let {
      reader?.cancel()
    }
  }
}
