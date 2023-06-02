package info.bagen.dwebbrowser

import info.bagen.dwebbrowser.microService.core.BootstrapContext
import info.bagen.dwebbrowser.microService.core.NativeMicroModule
import info.bagen.dwebbrowser.microService.helper.text
import IpcHeaders
import IpcResponse
import info.bagen.dwebbrowser.microService.sys.boot.BootNMM
import info.bagen.dwebbrowser.microService.sys.dns.DnsNMM
import info.bagen.dwebbrowser.microService.sys.dns.nativeFetch
import info.bagen.dwebbrowser.microService.sys.http.DwebHttpServerOptions
import info.bagen.dwebbrowser.microService.sys.http.HttpNMM
import info.bagen.dwebbrowser.microService.sys.http.createHttpDwebServer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DwebTest : AsyncBase() {

    class HttpTestNMM : NativeMicroModule("http.test.dweb") {
        override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
            val dwebServer = createHttpDwebServer(DwebHttpServerOptions());
            dwebServer.listen().onRequest { (request, ipc) ->
                ipc.postMessage(
                    IpcResponse.fromText(
                        request.req_id,
                        200,
                        IpcHeaders(),
                        "ECHO: " + request.url,
                        ipc
                    )
                )
            }
            _afterShutdownSignal.listen { dwebServer.close() }


            val internalServer =
                createHttpDwebServer(DwebHttpServerOptions(subdomain = "internal"));
            internalServer.listen().onRequest { (request, ipc) ->
                ipc.postMessage(
                    IpcResponse.fromText(
                        request.req_id,
                        200,
                        IpcHeaders(),
                        "ECHO/INTERNAL: " + request.url,
                        ipc
                    )
                )
            }
            _afterShutdownSignal.listen { dwebServer.close() }

            deferredList += GlobalScope.async(context = catcher) {
                for (i in 1..10) {
                    delay(20)
                    println("第 $i 次发送数据")
                    val data = "/hi-$i"
                    val res = nativeFetch(dwebServer.startResult.urlInfo.internal_origin + data)
                    assertEquals("ECHO: $data", res.text())
                }
            }
            deferredList += GlobalScope.async(context = catcher) {
                for (i in 1..10) {
                    delay(20)
                    println("第 $i 次发送数据")
                    val data = "/hi-$i"
                    val res = nativeFetch(internalServer.startResult.urlInfo.internal_origin + data)
                    assertEquals("ECHO/INTERNAL: $data", res.text())
                }
            }
            prepareReady.complete(Unit)
        }

        override suspend fun _shutdown() {
        }
    }

    @Test
    fun testHttp() = runBlocking {
        enableDwebDebug(listOf("stream-ipc"))

        val dnsNMM = DnsNMM()

        /// 安装系统应用
        val httpNMM = HttpNMM().also { dnsNMM.install(it) }

        /// 安装测试应用
        val httpTestNMM = HttpTestNMM().also { dnsNMM.install(it) }

        /// 安装启动程序
        val bootNMM = BootNMM(listOf(httpTestNMM.mmid)).also { dnsNMM.install(it) }

        /// 启动
        dnsNMM.bootstrap()


        prepareReady.await()
        for (def in deferredList) {
            def.await()
        }
    }
}