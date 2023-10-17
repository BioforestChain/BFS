package org.dweb_browser.browser.mwebview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalDensity
import com.google.accompanist.web.WebContent
import com.google.accompanist.web.WebViewNavigator
import com.google.accompanist.web.WebViewState
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.dweb_browser.core.ipc.Ipc
import org.dweb_browser.core.ipc.helper.IpcEvent
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.dwebview.DWebViewOptions
import org.dweb_browser.dwebview.base.ViewItem
import org.dweb_browser.dwebview.engine.DWebViewEngine
import org.dweb_browser.helper.Callback
import org.dweb_browser.helper.ChangeableList
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.mainAsyncExceptionHandler
import org.dweb_browser.helper.runBlockingCatching
import org.dweb_browser.helper.withMainContext
import org.dweb_browser.sys.window.core.WindowController
import org.dweb_browser.sys.window.core.createWindowAdapterManager
import java.util.concurrent.atomic.AtomicInteger

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
  private val localeMM: MicroModule,
  private val remoteMM: MicroModule,
) {
  companion object {
    private var webviewId_acc = AtomicInteger(1)
  }

  val webViewList = ChangeableList<MultiViewItem>()

  init {
    webViewList.onChange {
      updateStateHook()
    }
    val wid = win.id
    /// 提供渲染适配
    createWindowAdapterManager.renderProviders[wid] = @Composable { modifier ->
      val webViewScale = (LocalDensity.current.density * scale * 100).toInt()
      Render(modifier, webViewScale)
    }
    /// ipc 断开的时候，强制关闭窗口
    val off = ipc.onClose {
      win.close(force = true)
    }
    /// 窗口销毁的时候
    win.onClose {
      off();
      // 移除渲染适配器
      createWindowAdapterManager.renderProviders.remove(wid)
      // 清除释放所有的 webview
      for (item in webViewList) {
        closeWebView(item.webviewId)
      }
    }
  }

  fun isLastView(viewItem: MultiViewItem) = webViewList.lastOrNull() == viewItem
  fun isFistView(viewItem: MultiViewItem) = webViewList.firstOrNull() == viewItem
  val lastViewOrNull get() = webViewList.lastOrNull()
  fun getWebView(webviewId: String) = webViewList.find { it.webviewId == webviewId }

  data class MultiViewItem(
    override val webviewId: String,
    override val webView: DWebViewEngine,
    override val state: WebViewState,
    override val navigator: WebViewNavigator,
    override val coroutineScope: CoroutineScope,
    override var hidden: Boolean = false,
    val win: WindowController
  ) : ViewItem {}

  private var downLoadTaskId: String? = null

  /**
   * 打开WebView
   */
  suspend fun openWebView(url: String) = appendWebViewAsItem(createDwebView(url))

  suspend fun createDwebView(url: String): DWebViewEngine = withMainContext {
    val currentActivity = win.viewController.activity!!;// App.appContext
    val dWebView = DWebViewEngine(
      currentActivity, remoteMM, DWebViewOptions(
        url = url,
        /// 我们会完全控制页面将如何离开，所以这里兜底默认为留在页面
        onDetachedFromWindowStrategy = DWebViewOptions.DetachedFromWindowStrategy.Ignore,
      ), currentActivity
    )
    dWebView
  }

  @Synchronized
  fun appendWebViewAsItem(dWebView: DWebViewEngine) =
    runBlockingCatching(mainAsyncExceptionHandler) {
      val webviewId = "#w${webviewId_acc.getAndAdd(1)}"
      val state = WebViewState(WebContent.Url(dWebView.url ?: ""))
      val coroutineScope = CoroutineScope(CoroutineName(webviewId))
      val navigator = WebViewNavigator(coroutineScope)
      MultiViewItem(
        webviewId = webviewId,
        webView = dWebView,
        state = state,
        coroutineScope = coroutineScope,
        navigator = navigator,
        win = win,
      ).also { viewItem ->
        webViewList.add(viewItem)
        dWebView.onCloseWindow {
          closeWebView(webviewId)
        }
        webViewOpenSignal.emit(webviewId)
      }
    }.getOrThrow()

  /**
   * 关闭WebView
   */
  suspend fun closeWebView(webViewId: String) =
    webViewList.find { it.webviewId == webViewId }?.let { viewItem ->
      webViewList.remove(viewItem)
      withMainContext {
        viewItem.webView.destroy()
      }
      webViewCloseSignal.emit(webViewId)
      return true
    } ?: false

  /**
   * 移除所有列表
   */
  suspend fun destroyWebView(): Boolean {
    withMainContext {
      webViewList.forEach { viewItem ->
        viewItem.webView.destroy()
      }
    }
    webViewList.clear()
//    this.downLoadTaskId?.close() // 移除下载状态监听
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
    debugMultiWebView("updateStateHook =>", webViewList.size)
    webViewList.forEachIndexed { index, it ->
      val viewItem = mutableMapOf<String, JsonElement>()
      viewItem["index"] = JsonPrimitive(index)
      viewItem["webviewId"] = JsonPrimitive(it.webviewId)
      viewItem["isActivated"] = JsonPrimitive(it.hidden)
      viewItem["mmid"] = JsonPrimitive(ipc.remote.mmid)
      viewItem["url"] = JsonPrimitive((it.state.content as WebContent.Url).url)
      views[it.webviewId] = JsonObject(viewItem)
    }
    val state = mutableMapOf<String, JsonElement>()
    state["wid"] = JsonPrimitive(win.id)
    state["views"] = JsonObject(views)
    return JsonObject(state)
  }

  private suspend fun updateStateHook() {
    ipc.postMessage(IpcEvent.fromUtf8("state", Json.encodeToString(getState())))
  }

  private val webViewCloseSignal = Signal<String>()
  private val webViewOpenSignal = Signal<String>()

  fun onWebViewClose(cb: Callback<String>) = webViewCloseSignal.listen(cb)
  fun onWebViewOpen(cb: Callback<String>) = webViewOpenSignal.listen(cb)


}