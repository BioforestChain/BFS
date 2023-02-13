package info.bagen.rust.plaoc.microService

import info.bagen.rust.plaoc.microService.network.Http1Server
import info.bagen.rust.plaoc.microService.network.HttpListener
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


class HttpNMM {
    private val mmid: String = "http.sys.dweb"
    private val listenMap = mutableMapOf</* host */ String, HttpListener>()
    private val internal = "internal"
    private val http1 = Http1Server()



    private fun bootstrap() {
        http1.createServer()
    }

    private fun createListen(port: String): String {
        println("kotlin#LocalhostNMM createListen==> $mmid")
        val host = getHost(port)
        this.listenMap["$internal.$port"] = HttpListener(host)
        return host
    }


    private fun getHost(port: String): String {
        return "http://$internal.$port.$mmid";
    }
    fun closeServer() {

    }
}
fun Application.moduleRouter() {
    environment.monitor.subscribe(ApplicationStarted) { application ->
        application.environment.log.info("Server is started rootPath:${application.environment.rootPath}")
    }
    routing {
        get("/listen") {
            val port = call.request.queryParameters["port"]
            if (port == null || port == "") {
                call.respondText(
                    DefaultErrorResponse(
                        statusCode = 403,
                        errorMessage = "not found request param port"
                    ).toString()
                )
                return@get
            }
            println("https.sys.dweb#listen:$port")
//                    createListen(port)
        }
        get("/create-process") { }
    }
}
