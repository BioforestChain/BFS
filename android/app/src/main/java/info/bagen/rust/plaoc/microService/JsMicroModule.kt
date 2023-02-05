package info.bagen.rust.plaoc.microService


import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import com.fasterxml.jackson.core.JsonParser
import com.google.gson.Gson
import info.bagen.libappmgr.network.ApiService
import info.bagen.libappmgr.utils.JsonUtil.toJson
import info.bagen.rust.plaoc.App
import info.bagen.rust.plaoc.mapper
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.net.URLEncoder
import java.util.*


typealias workerOption = NativeOptions

class JsMicroModule : MicroModule() {
    // 该程序的来源
    override var mmid = "js.sys.dweb"
    private val routers: Router = mutableMapOf()

    // 我们隐匿地启动单例webview视图，用它来动态创建 WebWorker，来实现 JavascriptContext 的功能
    private val javascriptContext = JavascriptContext()
    //private val _connecttingIpcs = mutableSetOf<Ipc>()

    init {
        // 创建一个webWorker
        routers["create-process"] = put@{ args ->
            return@put createProcess(args as workerOption)
        }
    }

    override fun bootstrap(args: workerOption) {
        println("kotlin#JsMicroModule args==> ${args.mainCode}  ${args.origin}")
        // 开始执行开发者自己的代码
        this.createProcess(args)
    }

    override fun ipc(): Ipc {
        TODO("Not yet implemented")
    }

    // 创建一个webWorker
    @OptIn(DelicateCoroutinesApi::class)
    private fun createProcess(args: workerOption) {
        if (args.mainCode == "") return
        GlobalScope.launch {
            javascriptContext.hiJackWorkerCode(args.mainCode)
        }
    }

}

class JavascriptContext {
    // 存储每个worker
    val ALL_PROCESS_MAP = mutableMapOf<Number, String>()
    var accProcessId = 0
    // 工具方法
    val gson = Gson()

    var onWebviewFinished = Mutex()
    var isWebviewFinished = false

    // 创建了一个后台运行的webView 用来运行webWorker
    var view: WebView = WebView(App.appContext).also { view ->
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.databaseEnabled = true
        view.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                println("JavascriptContext#onPageFinished")
                isWebviewFinished = true
                if (onWebviewFinished.isLocked) {
                    onWebviewFinished.unlock()
                }
            }
        }
        WebView.setWebContentsDebuggingEnabled(true)
    }

    /** 处理ipc 请求的工厂 然后会转发到nativeFetch */
    fun icpFactory(webMessagePort: WebMessagePort, ipcString: String) {
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true) //允许出现特殊字符和转义符
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true) //允许使用单引号
        val ipcRequest = mapper.readValue(ipcString, IpcRequest::class.java)
        println("JavascriptContext#ipcFactory url: ${ipcRequest.url}")
        // 处理请求
        global_micro_dns.nativeFetch(ipcRequest.url)
        tranResponseWorker(
            webMessagePort,
            IpcResponse(
                statusCode = 200,
                req_id = ipcRequest.req_id,
                headers = ipcRequest.headers,
                body = "ok"
            )
        )
    }

    /** 这里负责返回每个webWorker里的返回值
     * 注意每个worker的post都是不同的 */
    private fun tranResponseWorker(webMessagePort: WebMessagePort, res: IpcResponse) {
        val jsonMessage = gson.toJson(res)
        println("JavascriptContext#tranResponseWorker: $jsonMessage")
        webMessagePort.postMessage(WebMessage(jsonMessage))
    }


    /** 为这个上下文安装启动代码 */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun hiJackWorkerCode(mainUrl: String) {
        if (!isWebviewFinished) {
            onWebviewFinished.lock()
        }
        GlobalScope.launch {
            val workerHandle = "worker${Date().time}"
            println("kotlin#JsMicroModule workerHandle==> $workerHandle")
            val injectJs = getInjectWorkerCode("injectWorkerJs/injectWorker.js")
            val userCode = ApiService.instance.getNetWorker(mainUrl).replace("\"use strict\";", "")
            // 构建注入的代码
            val workerCode =
                "data:utf-8,((module,exports=module.exports)=>{$injectJs;return module.exports})({exports:{}}).installEnv();$userCode"
            withContext(Dispatchers.Main) {
                injectJs(workerCode, workerHandle)
            }
        }
    }

    //    注入webView
    private fun injectJs(workerCode: String, workerHandle: String) {
        // 为每一个webWorker都创建一个通道
        val channel = view.createWebMessageChannel()
        channel[0].setWebMessageCallback(object :
            WebMessagePort.WebMessageCallback() {
            override fun onMessage(port: WebMessagePort, message: WebMessage) {
                println("kotlin#JsMicroModuleport🍟message: ${message.data}")
                icpFactory(channel[0], message.data)
            }
        })
        view.evaluateJavascript(
            "const $workerHandle = new Worker(`$workerCode`); \n" +
                    "onmessage = function (e) {\n" +
                    "console.log(\"kotlin#DwebViewActivity port1111\", e.data, e.ports[0]); \n" +
                    "$workerHandle.postMessage([\"ipc-channel\", e.ports[0]], [e.ports[0]])\n" +
                    "}\n"
        ) {
            println("worker创建完成")
        }
        // 发送post1到worker层
        view.postWebMessage(WebMessage("fetch-ipc-channel", arrayOf(channel[1])), Uri.EMPTY)

        this.ALL_PROCESS_MAP[accProcessId] = workerHandle
        this.accProcessId++
    }

    private fun getInjectWorkerCode(jsAssets: String): String {
        val inputStream = App.appContext.assets.open(jsAssets)
        val byteArray = inputStream.readBytes()
        return String(byteArray)
    }
}

