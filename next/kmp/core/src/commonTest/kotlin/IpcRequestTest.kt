package info.bagen.dwebbrowser

import kotlinx.coroutines.launch
import org.dweb_browser.core.ipc.IpcOptions
import org.dweb_browser.core.ipc.IpcRequestInit
import org.dweb_browser.core.ipc.NativeIpc
import org.dweb_browser.core.ipc.NativeMessageChannel
import org.dweb_browser.core.ipc.helper.IpcPoolPack
import org.dweb_browser.core.ipc.helper.IpcResponse
import org.dweb_browser.core.ipc.kotlinIpcPool
import org.dweb_browser.pure.http.IPureBody
import org.dweb_browser.pure.http.PureMethod
import org.dweb_browser.test.runCommonTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IpcRequestTest {
  @Test
  fun testIpcRequestInBlackBox() = runCommonTest {
    val channel = NativeMessageChannel<IpcPoolPack, IpcPoolPack>("from.id.dweb", "to.id.dweb")
    val fromMM = TestMicroModule()
    val toMM = TestMicroModule()
    val senderIpc = kotlinIpcPool.create<NativeIpc>(
      "test-request-1", IpcOptions(toMM, channel = channel.port1)
    )
    val receiverIpc = kotlinIpcPool.create<NativeIpc>(
      "test-request-2", IpcOptions(fromMM, channel = channel.port2)
    )
    launch {
      // send text body
      println("🧨=> send text body")
      val response = senderIpc.request(
        "https://test.dwebdapp_1.com", IpcRequestInit(
          method = PureMethod.POST,
          body = IPureBody.from("senderIpc")
        )
      )
      println("🧨=> ${response.statusCode}")
      assertEquals(response.body.text(), "除夕快乐")
    }
    launch {
      println("🧨=>  收到消息")
      receiverIpc.onRequest { (request, ipc) ->
        val data = request.body.toString()
        println("结果🧨=> $data ${ipc.remote.mmid}")
        assertEquals("senderIpc", data)
        ipc.postMessage(IpcResponse.fromText(request.reqId, text = "除夕快乐", ipc = ipc))
      }
    }
  }

  @Test
  fun testIpcRequest() = runCommonTest {

  }
}