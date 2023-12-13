package org.dweb_browser.core.http


import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSocketUpgrade
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dweb_browser.core.help.AdapterManager
import org.dweb_browser.core.help.asPureRequest
import org.dweb_browser.core.help.fromPureResponse
import org.dweb_browser.core.help.isWebSocket
import org.dweb_browser.core.std.http.debugHttp
import org.dweb_browser.core.std.http.findDwebGateway
import org.dweb_browser.helper.SuspendOnce
import org.dweb_browser.helper.consumeEachArrayRange
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.platform.getKtorServerEngine
import org.dweb_browser.helper.toUtf8


typealias HttpGateway = suspend (request: PureRequest) -> PureResponse?

class DwebGatewayHandlerAdapterManager : AdapterManager<HttpGateway>() {
  suspend fun doGateway(request: PureRequest): PureResponse? {
    for (adapter in adapters) {
      val response = adapter(request)
      if (response != null) {
        return response
      }
    }
    return null
  }
}

class DwebHttpGatewayServer private constructor() {
  companion object {
    val INSTANCE by lazy { DwebHttpGatewayServer() }
    val gatewayAdapterManager = DwebGatewayHandlerAdapterManager()
  }

  val server = embeddedServer(getKtorServerEngine(), port = 0) {
    install(WebSockets)
    install(createApplicationPlugin("dweb-gateway") {
      onCall { call ->
        withContext(ioAsyncExceptionHandler) {
          /// 将 ktor的request 构建成 pureRequest
          call.request.asPureRequest().also { rawRequest ->
            val rawUrl = rawRequest.href
            val url = rawRequest.queryOrNull("X-Dweb-Url")
              ?: when (val info = findDwebGateway(rawRequest)) {
                null -> rawUrl
                else -> "${info.protocol.name}://${info.host}$rawUrl"
              }
            var pureRequest = if (url != rawUrl) rawRequest.copy(href = url) else rawRequest;

            if (pureRequest.isWebSocket()) {
              pureRequest = pureRequest.copy(channel = CompletableDeferred())
            }
            val response = try {
              gatewayAdapterManager.doGateway(pureRequest)
                ?: PureResponse(HttpStatusCode.GatewayTimeout)
            } catch (e: Throwable) {
              PureResponse(HttpStatusCode.BadGateway, body = IPureBody.from(e.message ?: ""))
            }

            if (pureRequest.hasChannel) {
              /// 如果是101响应头，那么使用WebSocket来作为双工的通讯标准进行传输，这里使用PureChannel来承担这层抽象
              when (response.status.value) {
                /// 如果是200响应头，说明是传统的响应模式，这时候只处理输出，websocket的incoming数据完全忽视
                200 -> {
                  val res = WebSocketUpgrade(call, null) {
                    val ws = this;
                    val streamReader = response.stream().getReader("Http1Server websocket")
                    /// 将从服务端收到的数据，转成 200 的标准传输到 websocket 的 frame 中
                    launch {
                      streamReader.consumeEachArrayRange { byteArray, _ ->
                        ws.send(Frame.Binary(true, byteArray))
                      }
                      ws.close()
                    }
                    /// 将从客户端收到的数据，但是忽视
                    @Suppress("ControlFlowWithEmptyBody")
                    for (frame in ws.incoming) {// 注意，这里ws.incoming要立刻进行，不能在launch中异步执行，否则ws将无法完成连接建立
                    }
                    /// 等到双工关闭，同时也关闭读取层
                    streamReader.cancel(null)

                  }
                  call.respond(res)
                }
                /// 如果是100响应头，说明走的是标准的PureDuplex模型，那么使用WebSocket来作为双工的通讯标准进行传输
                101 -> {
                  val res = WebSocketUpgrade(call, null) {
                    val ws = this;
                    val income = Channel<PureFrame>()
                    val outgoing = Channel<PureFrame>()
                    val pureChannel = PureServerChannel(income, outgoing, pureRequest, response, ws)
                    pureRequest.initChannel(pureChannel)

                    /// 将从 pureChannel 收到的数据，传输到 websocket 的 frame 中
                    launch {
                      for (pureFrame in outgoing) {
                        debugHttp("WebSocketToPureChannel") { "outgoing-to-ws:$pureFrame/$url" }
                        when (pureFrame) {
                          is PureTextFrame -> ws.send(Frame.Text(pureFrame.data))
                          is PureBinaryFrame -> ws.send(Frame.Binary(true, pureFrame.data))
                        }
                      }
                      debugHttp("WebSocketToPureChannel") { "outgoing-close-ws/$url" }
                      ws.close()
                    }
                    /// 将从客户端收到的数据，转成 PureFrame 的标准传输到 pureChannel 中
                    for (frame in ws.incoming) {// 注意，这里ws.incoming要立刻进行，不能在launch中异步执行，否则ws将无法完成连接建立
                      val pureFrame = when (frame.frameType) {
                        FrameType.BINARY -> PureBinaryFrame(frame.data)
                        FrameType.TEXT -> PureTextFrame(frame.data.toUtf8())
                        else -> continue
                      }
                      debugHttp("WebSocketToPureChannel") { "ws-to-incoming:$pureFrame/$url" }
                      income.send(pureFrame)
                    }
                    /// 等到双工关闭，同时也关闭channel
                    debugHttp("WebSocketToPureChannel") { "ws-close-pureChannel:$url" }
                    pureChannel.close()
                  }
                  call.respond(res)
                }

                else -> call.response.fromPureResponse(response).also {
                  debugHttp("WS-ERROR", response.body)
                }
              }
            } else {
              call.response.fromPureResponse(response)
            }
          }
        }
      }
    })

  }
  private val startResult = lazy {
    CompletableDeferred<Int>()
  }

  suspend fun startServer(): Int {
    if (!startResult.isInitialized()) {
      startResult.value.also {
        server.start(wait = false)
        val port = server.resolvedConnectors().first().port
        it.complete(port)
      }
    }
    return startResult.value.await()
  }

  suspend fun getPort() = startServer()
  val getHttpLocalhostGatewaySuffix = SuspendOnce { ".localhost:${startServer()}" }

  suspend fun getUrl() = "http://127.0.0.1:${startServer()}"

}

val dwebHttpGatewayServer get() = DwebHttpGatewayServer.INSTANCE