package org.dweb_browser.browser.mwebview

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.dweb_browser.browser.common.createDwebView
import org.dweb_browser.core.ipc.Ipc
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.core.std.dns.nativeFetch
import org.dweb_browser.dwebview.IDWebView
import org.dweb_browser.dwebview.base.ViewItem
import org.dweb_browser.helper.ChangeableList
import org.dweb_browser.helper.SafeInt
import org.dweb_browser.helper.withMainContext
import org.dweb_browser.sys.window.core.WindowController
import org.dweb_browser.sys.window.core.windowAdapterManager
import org.dweb_browser.sys.window.core.withRenderScope

typealias WEBVIEW_ID = String

/**
 * MWebView 是为其它模块提供 GUI 的程序模块，所以这里需要传入两个模块：localeMM 与 remoteMM
 * remoteMM 只是一层抽象，未来如何需要可以通过网络成实现
 */
@Stable
class MultiWebViewController(
  /**
   * 窗口控制器
   */
  val win: WindowController,
  /**
   * 控制者的通讯通道
   */
  val ipc: Ipc,
  /// 以下这两参数是用来构建DWebView的时候使用的
  private val localeMM: MicroModule.Runtime,
  val remoteMM: MicroModule.Runtime,
) {
  companion object {
    private var webviewId_acc by SafeInt(1)
  }

  val webViewList = ChangeableList<MultiViewItem>()

  init {
    val rid = win.id
    /// 提供渲染适配
    windowAdapterManager.provideRender(rid) { modifier ->
      Render(modifier.withRenderScope(this), scale) // 开始渲染
    }
    /// 窗口销毁的时候
    win.onClose {
      // 清除释放所有的 webview
      for (item in webViewList.toList()) {
        closeWebView(item.webviewId)
      }
    }
  }

  fun isLastView(viewItem: MultiViewItem) = webViewList.lastOrNull() == viewItem
  fun isFistView(viewItem: MultiViewItem) = webViewList.firstOrNull() == viewItem
  val lastViewOrNull get() = webViewList.lastOrNull()
  fun getWebView(webviewId: String) = webViewList.find { it.webviewId == webviewId }

  data class MultiViewItem(
    override val webviewId: WEBVIEW_ID,
    override val webView: IDWebView,
    override val coroutineScope: CoroutineScope,
    override var hidden: Boolean = false,
    val windowController: WindowController,
    val layerController: MultiWebViewController,
  ) : ViewItem

  /**
   * 打开WebView
   */
  suspend fun openWebView(url: String): MultiViewItem {
    localeMM.debugMM("openWebView", url)
    val webView = win.createDwebView(remoteMM, url)
    return appendWebViewAsItem(webView)
  }

  private suspend fun appendWebViewAsItem(dWebView: IDWebView): MultiViewItem {
    localeMM.debugMM("appendWebViewAsItem", dWebView)
    val webviewId = "#w${webviewId_acc++}"
    val coroutineScope =
      CoroutineScope(CoroutineName(webviewId) + localeMM.getRuntimeScope().coroutineContext)
    return MultiViewItem(
      webviewId = webviewId,
      webView = dWebView,
      coroutineScope = coroutineScope,
      windowController = win,
      layerController = this,
    ).also { viewItem ->
      webViewList.add(viewItem)
      dWebView.onCreateWindow {
        val url = it.getUrl()
        if (url.startsWith("dweb://")) {
          dWebView.remoteMM.nativeFetch(url)
        } else {
          appendWebViewAsItem(it)
        }
      }
      dWebView.onDestroy {
        closeWebView(webviewId)
      }
    }
  }

  /**
   * 关闭WebView
   */
  suspend fun closeWebView(webViewId: String) =
    webViewList.find { it.webviewId == webViewId }?.let { viewItem ->
      webViewList.remove(viewItem)
      withMainContext {
        viewItem.webView.destroy()
      }
      return true
    } ?: false

  /**
   * 移除所有列表
   */
  suspend fun destroyWebView(): Boolean {
    webViewList.toList().forEach { viewItem ->
      viewItem.webView.destroy()
    }
    webViewList.clear()
    win.closeRoot()
    return true
  }

  /**
   * 将指定WebView移动到顶部显示
   */
  suspend fun moveToTopWebView(webviewId: String): Boolean {
    val viewItem = webViewList.find { it.webviewId == webviewId } ?: return false
    webViewList.remove(viewItem)
    webViewList.add(viewItem)
    return true
  }

  fun getState(): JsonObject {
    val views = mutableMapOf<String, JsonElement>()
    webViewList.forEachIndexed { index, it ->
      val viewItem = mutableMapOf<String, JsonElement>()
      viewItem["index"] = JsonPrimitive(index)
      viewItem["webviewId"] = JsonPrimitive(it.webviewId)
      viewItem["isActivated"] = JsonPrimitive(it.hidden)
      viewItem["mmid"] = JsonPrimitive(ipc.remote.mmid)
      viewItem["url"] = JsonPrimitive(it.webView.getUrl())
      views[it.webviewId] = JsonObject(viewItem)
    }
    val state = mutableMapOf<String, JsonElement>()
    state["wid"] = JsonPrimitive(win.id)
    state["views"] = JsonObject(views)
    return JsonObject(state)
  }
}