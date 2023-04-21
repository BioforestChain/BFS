package info.bagen.dwebbrowser.microService.sys.plugin.device

import info.bagen.dwebbrowser.microService.core.BootstrapContext
import info.bagen.dwebbrowser.microService.core.NativeMicroModule
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes

class NfcNMM: NativeMicroModule("nfc.sys.dweb") {
    override suspend fun _bootstrap(bootstrapContext: BootstrapContext)
 {
        apiRouting = routes(
            /** 开始读取当前信息*/
            "/read" bind Method.GET to defineHandler { request ->
                Response(Status.OK)
            },
        )
    }

    override suspend fun _shutdown() {
        TODO("Not yet implemented")
    }
}