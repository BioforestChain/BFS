package info.bagen.rust.plaoc.microService


/** 启动Boot服务*/
fun startBootNMM() {
    BootNMM().bootstrap(NativeOptions(routerTarget = "desktop.sys.dweb"))
}

open class NativeMicroModule(override val mmid: Mmid = "sys.dweb") : MicroModule() {
    override fun bootstrap(args:NativeOptions) {
        println("Kotlin#NativeMicroModule mmid:$mmid bootstrap $args")
        global_micro_dns.dnsMap[mmid]?.let { it -> it(args) }
    }

    override fun ipc(): Ipc {
        TODO("Not yet implemented")
    }
}


open class NativeOptions(
    var origin: String = "", // 程序地址
    var mainJs: String = "", // webWorker运行地址
    val routerTarget:String,
    val processId: Int? = null,  // 要挂载的父进程id
    val webViewId: String = "default", // default.mwebview.sys.dweb
) {
    operator fun set(key: String?, value: String?) {
        if (key == "origin" && value != null) {
            this.origin = value
            return
        }
        if ((key == "mainJs" || key == "main_js" ) && value != null) {
            this.mainJs = value
            return
        }
    }
}

abstract class MicroModule {
    open val mmid: String = ""
    abstract fun bootstrap(args:NativeOptions)
    abstract fun ipc(): Ipc
}