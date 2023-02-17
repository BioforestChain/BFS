package info.bagen.rust.plaoc.microService.ipc.ipcWeb

import android.webkit.WebMessage
import android.webkit.WebMessagePort
import info.bagen.rust.plaoc.microService.MicroModule
import info.bagen.rust.plaoc.microService.helper.moshiPack
import info.bagen.rust.plaoc.microService.ipc.*

class MessagePortIpc(
    val port: WebMessagePort,
    override val remote: MicroModule,
    override val role: IPC_ROLE,
    /** MessagePort 默认支持二进制传输 */
    override val supportMessagePack: Boolean = false
) : Ipc() {
    val context =this
    init {
        port.setWebMessageCallback(object :
            WebMessagePort.WebMessageCallback() {
            override fun onMessage(port: WebMessagePort, event: WebMessage) {
                println("MessagePortIpc#port🍟message: ${event.data}")
                val message = messageToIpcMessage(event.data,context) ?: return

                if (message === "close") {
                    context.close();
                    return;
                }
                context._messageSignal.emit(message as IpcMessageArgs)
            }
        })
    }

    override suspend fun _doPostMessage(data: IpcMessage) {
        val webMessage = if (this.supportMessagePack) {
            println("MessagePortIpc#_doPostMessage===>${moshiPack.pack(data)}")
            WebMessage(moshiPack.pack(data).toString()) //TODO 要测试
        } else {
            println("MessagePortIpc#message===>$data")
            WebMessage(data.toString())
        }
      this.port.postMessage(webMessage)
    }

    override fun _doClose() {
       this.port.postMessage(WebMessage("close"))
        this.port.close()
    }
}

