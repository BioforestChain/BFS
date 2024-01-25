package org.dweb_browser.js_backend.ws

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import node.http.createServer
import node.http.IncomingMessage
import node.http.ServerResponse
import node.net.Socket
import node.url.parse
import org.dweb_browser.js_backend.http.HttpServer
import org.dweb_browser.js_backend.view_model.ViewModelSocket


/**
 * WS依赖HttpServer
 * - 共用一个port
 */
class WS private constructor(){
    val scope = CoroutineScope(Dispatchers.Default)
    val whenReady = CompletableDeferred<Unit>()
    init {
        scope.launch {
            HttpServer.deferredInstance.await().run {
                console.log("注册了 upgrade")
                getServer().on("upgrade") { req: IncomingMessage, socket: Socket ->
                    console.log("接受到了 upgrade 请求req.url",req.url)
                    val frontendViewModelId: String = req.url?.let {
                        parse(it, true).query?.let {o: dynamic->
                            o["frontend_view_module_id"]
                        }
                    } as? String ?: throw(Throwable("""
                            frontendViewModelId == null
                            req.url : ${req.url}
                            at class WS
                            at Ws.kt
                        """.trimIndent()))

                    console.log("frontendViewModelId: ", frontendViewModelId)
                    ViewModelSocket(socket, req.headers["sec-websocket-key"] as String, frontendViewModelId)
                    whenReady.complete(Unit)
                }
            }
        }
    }

    companion object {
        var deferredInstance = CompletableDeferred<WS>()
        fun createWS(): CompletableDeferred<WS>{
            return if(deferredInstance.isCompleted) {
                deferredInstance
            }else{
                deferredInstance.complete(WS())
                deferredInstance
            }
        }
    }
}

