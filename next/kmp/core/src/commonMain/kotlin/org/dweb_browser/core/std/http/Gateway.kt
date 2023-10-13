package org.dweb_browser.core.std.http

import io.ktor.http.HttpStatusCode
import org.dweb_browser.helper.SimpleCallback
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.core.http.PureRequest
import org.dweb_browser.core.http.PureResponse
import org.dweb_browser.core.ipc.Ipc
import org.dweb_browser.core.ipc.ReadableStreamIpc
import org.dweb_browser.core.ipc.helper.IpcHeaders
import org.dweb_browser.core.ipc.helper.IpcMethod

class Gateway(
  val listener: PortListener, val urlInfo: HttpNMM.ServerUrlInfo, val token: String
) {

  class PortListener(
    val ipc: Ipc, val host: String
  ) {
    private val _routerSet = mutableSetOf<StreamIpcRouter>();

    fun addRouter(config: RouteConfig, streamIpc: ReadableStreamIpc): () -> Boolean {
      val route = StreamIpcRouter(config, streamIpc);
      this._routerSet.add(route)
      return {
        this._routerSet.remove(route)
      }
    }

    /**
     * 接收 nodejs-web 请求
     * 将之转发给 IPC 处理，等待远端处理完成再代理响应回去
     */
    suspend fun hookHttpRequest(request: PureRequest): PureResponse? {
      for (router in _routerSet) {
        val response = router.handler(request)
        if (response != null) {
          return response
        }
      }
      return null
    }

    /// 销毁
    private val destroySignal = SimpleSignal()
    fun onDestroy(cb: SimpleCallback) = destroySignal.listen(cb)

    suspend fun destroy() {
      _routerSet.map {
        it.streamIpc.input.closeRead()
      }
      destroySignal.emit()
    }
  }

  class StreamIpcRouter(val config: RouteConfig, val streamIpc: ReadableStreamIpc) {
    suspend fun handler(request: PureRequest) = if (config.isMatch(request)) {
      streamIpc.request(request)
    } else if (request.method == IpcMethod.OPTIONS) {
      // 处理options请求
      PureResponse(HttpStatusCode.OK, IpcHeaders.from(CORS_HEADERS.toMap()))
    } else null
  }
}

val CORS_HEADERS = listOf(
  Pair("Access-Control-Allow-Origin", "*"),
  Pair("Access-Control-Allow-Headers", "*"),
  Pair("Access-Control-Allow-Methods", "*"),
)