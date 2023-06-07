package info.bagen.dwebbrowser.microService.browser

import android.content.Intent
import android.os.Bundle
import info.bagen.dwebbrowser.App
import info.bagen.dwebbrowser.microService.browser.jmm.JmmNMM.Companion.getAndUpdateJmmNmmApps
import info.bagen.dwebbrowser.microService.core.BootstrapContext
import info.bagen.dwebbrowser.microService.core.NativeMicroModule
import info.bagen.dwebbrowser.microService.core.ipc.Ipc
import info.bagen.dwebbrowser.microService.core.ipc.IpcEvent
import info.bagen.dwebbrowser.microService.helper.printdebugln
import org.http4k.core.Method
import org.http4k.lens.Query
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.routes

fun debugBrowser(tag: String, msg: Any? = "", err: Throwable? = null) =
  printdebugln("browser", tag, msg, err)
class BrowserNMM : NativeMicroModule("browser.dweb") {
  companion object {
    val controllerList = mutableListOf<BrowserController>()
    val browserController get() = controllerList.firstOrNull() // 只能browser 里面调用，不能给外部调用
  }

  init {
    controllerList.add(BrowserController(this))
  }

  val queryAppId = Query.string().required("app_id")
  data class AppInfo(val id:String,val icon:String,val name:String,val short_name:String)
  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    bootstrapContext.dns.bootstrap("jmm.browser.dweb")
    apiRouting = routes(
      "/openApp" bind Method.GET to defineHandler { request ->
        val mmid = queryAppId(request)
        debugBrowser("openApp",mmid)
        return@defineHandler browserController?.openApp(mmid) // 直接调这个后端没启动
      },
    "/appsInfo" bind Method.GET to defineHandler { request ->
      val apps = getAndUpdateJmmNmmApps()
      debugBrowser("appInfo",apps.size)
      val responseApps = mutableListOf<AppInfo>()
      apps.forEach { item ->
        val meta = item.value.metadata
        responseApps.add(
          AppInfo(meta.id,
            meta.icon,
            meta.name,
            meta.short_name))
      }
      return@defineHandler responseApps
    })
  }

  override suspend fun onActivity(event: IpcEvent, ipc: Ipc) {
    App.startActivity(BrowserActivity::class.java) { intent ->
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
      // 由于SplashActivity添加了android:excludeFromRecents属性，导致同一个task的其他activity也无法显示在Recent Screen，比如BrowserActivity
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
      intent.putExtras(Bundle().also { b -> b.putString("mmid", mmid) })
    }
  }

  override suspend fun _shutdown() {
  }
}