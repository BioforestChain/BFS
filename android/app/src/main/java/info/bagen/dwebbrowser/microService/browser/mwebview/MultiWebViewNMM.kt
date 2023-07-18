package info.bagen.dwebbrowser.microService.browser.mwebview

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.currentRecomposeScope
import info.bagen.dwebbrowser.App
import info.bagen.dwebbrowser.microService.core.AndroidNativeMicroModule
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.dweb_browser.browserUI.download.DownLoadObserver
import org.dweb_browser.dwebview.base.ViewItem
import org.dweb_browser.dwebview.serviceWorker.emitEvent
import org.dweb_browser.microservice.help.Mmid
import org.dweb_browser.helper.*
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.MicroModule
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Query
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.routes
import kotlinx.coroutines.*

fun debugMultiWebView(tag: String, msg: Any? = "", err: Throwable? = null) =
  printdebugln("mwebview", tag, msg, err)

class MultiWebViewNMM : AndroidNativeMicroModule("mwebview.browser.dweb") {
  class ActivityClass(var mmid: Mmid, val ctor: Class<out MultiWebViewActivity>)

  companion object {
    val activityClassList = mutableListOf(
      ActivityClass("", MultiWebViewPlaceholder1Activity::class.java),
      ActivityClass("", MultiWebViewPlaceholder2Activity::class.java),
      ActivityClass("", MultiWebViewPlaceholder3Activity::class.java),
      ActivityClass("", MultiWebViewPlaceholder4Activity::class.java),
      ActivityClass("", MultiWebViewPlaceholder5Activity::class.java),
    )
    private val controllerMap = mutableMapOf<Mmid, MultiWebViewController>()

    /**获取当前的controller, 只能给nativeUI 使用，因为他们是和mwebview绑定在一起的
     */
    @Deprecated("将不会再提供这个函数")
    fun getCurrentWebViewController(mmid: Mmid): MultiWebViewController? {
      return controllerMap[mmid]
    }
  }

  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    /// nativeui 与 mwebview 是伴生关系
    bootstrapContext.dns.open("nativeui.browser.dweb")

    // 打开webview
    val queryUrl = Query.string().required("url")
    val queryWebviewId = Query.string().required("webview_id")

    apiRouting = routes(
      // 打开一个 webview 作为窗口
      "/open" bind Method.GET to defineHandler { request, ipc ->
        val url = queryUrl(request)
        val remoteMm = ipc.asRemoteInstance()
          ?: throw Exception("mwebview.browser.dweb/open should be call by locale")
        ipc.onClose {
          debugMultiWebView("/open", "listen ipc close destroy window")
          val controller = controllerMap[ipc.remote.mmid]
          controller?.destroyWebView()
        }

        val viewItem = openDwebView(remoteMm, url);
        return@defineHandler ViewItemResponse(viewItem.webviewId)
      },
      // 关闭指定 webview 窗口
      "/close" bind Method.GET to defineHandler { request, ipc ->
        val webviewId = queryWebviewId(request)
        val remoteMmid = ipc.remote.mmid
        debugMultiWebView("/close", "webviewId:$webviewId,mmid:$remoteMmid")
        closeDwebView(remoteMmid, webviewId)
      },
      "/close/app" bind Method.GET to defineHandler { request, ipc ->
        val controller = controllerMap[ipc.remote.mmid] ?: return@defineHandler false;
        controller.destroyWebView()
      },
      // 界面没有关闭，用于重新唤醒
      "/activate" bind Method.GET to defineHandler { request, ipc ->
        val remoteMmid = ipc.remote.mmid
        debugMultiWebView("/activate", "激活")
        activityClassList.find { it.mmid == remoteMmid }?.let { activityClass ->
          App.startActivity(activityClass.ctor) { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val b = Bundle()
            b.putString("mmid", remoteMmid)
            intent.putExtras(b)
          }
        }
        return@defineHandler Response(Status.OK)
      },
    )
  }

  data class ViewItemResponse(val webview_id: String)

  override suspend fun _shutdown() {
    apiRouting = null
  }


  override fun openActivity(remoteMmid: Mmid) {
    val flags = mutableListOf<Int>(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    val activityClass = activityClassList.find { it.mmid == remoteMmid } ?:
    // 如果没有，从第一个挪出来，放到最后一个，并将至付给 remoteMmid
    activityClassList.removeAt(0).also {
      it.mmid = remoteMmid
      activityClassList.add(it)
    }
    flags.add(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    App.startActivity(activityClass.ctor) { intent ->
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      val b = Bundle();
      b.putString("mmid", remoteMmid);
      intent.putExtras(b);
    }
  }


  private suspend fun openDwebView(
    remoteMm: MicroModule,
    url: String,
  ): ViewItem {
    val remoteMmid = remoteMm.mmid
    debugMultiWebView("/open", "remote-mmid: $remoteMmid / url:$url")
    val controller = controllerMap.getOrPut(remoteMmid) {
      MultiWebViewController(
        remoteMmid,
        this,
        remoteMm,
      )
    }
    GlobalScope.launch(ioAsyncExceptionHandler) {

      controller.downLoadObserver = DownLoadObserver(remoteMmid).apply {

        observe { listener ->
          controller.lastViewOrNull?.webView?.let { dWebView ->
            emitEvent(
              dWebView,
              listener.downLoadStatus.toServiceWorkerEvent(),
              listener.progress
            )
          }
        }
      }
    }

    openActivity(remoteMmid)
    /// 等待创建成功再返回
    val activity = controller.waitActivityCreated()
    activitySignal.emit(Pair(remoteMmid, activity))
    /// 销毁的时候取消绑定
    controller.onWebViewClose { }
    activity.onDestroyActivity {
      controllerMap.remove(remoteMmid, controller)
      /// FIXME 更新状态？？？
      controller.updateStateHook()
    }

    return controller.openWebView(url)
  }

  private suspend fun closeDwebView(remoteMmid: String, webviewId: String): Boolean {
    return controllerMap[remoteMmid]?.closeWebView(webviewId) ?: false
  }
}