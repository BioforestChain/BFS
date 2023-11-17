package org.dweb_browser.browser.web

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.dweb_browser.browser.web.model.BrowserStore
import org.dweb_browser.browser.web.model.WebLinkManifest
import org.dweb_browser.browser.web.model.WebSiteInfo
import org.dweb_browser.browser.web.ui.browser.model.BrowserViewModel
import org.dweb_browser.core.help.types.MMID
import org.dweb_browser.core.std.http.HttpDwebServer
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.UUID
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.sys.window.core.WindowController
import org.dweb_browser.sys.window.core.constant.WindowMode
import org.dweb_browser.sys.window.core.helper.setFromManifest
import org.dweb_browser.sys.window.core.windowAdapterManager
import org.dweb_browser.sys.window.core.windowInstancesManager

class BrowserController(
  private val browserNMM: BrowserNMM,
  private val browserServer: HttpDwebServer
) {
  private val browserStore = BrowserStore(browserNMM)

  private val closeWindowSignal = SimpleSignal()
  val onCloseWindow = closeWindowSignal.toListener()

  private val addWebLinkSignal = Signal<WebLinkManifest>()
  val onWebLinkAdded = addWebLinkSignal.toListener()

  private var winLock = Mutex(false)

  private val ioAsyncScope = MainScope() + ioAsyncExceptionHandler

  val bookLinks: MutableList<WebSiteInfo> = mutableListOf()
  val historyLinks: MutableMap<String, MutableList<WebSiteInfo>> = mutableMapOf()

  init {
    ioAsyncScope.launch {
      browserStore.getBookLinks().forEach { webSiteInfo ->
        bookLinks.add(webSiteInfo)
      }
      browserStore.getHistoryLinks().forEach { (key, webSiteInfoList) ->
        historyLinks[key] = webSiteInfoList
      }
    }
  }

  suspend fun saveBookLinks() = browserStore.setBookLinks(bookLinks)

  suspend fun saveHistoryLinks(key: String, historyLinks: MutableList<WebSiteInfo>) =
    browserStore.setHistoryLinks(key, historyLinks)

  /**
   * 窗口是单例模式
   */
  private var win: WindowController? = null
  suspend fun renderBrowserWindow(wid: UUID) = winLock.withLock {
    (windowInstancesManager.get(wid) ?: throw Exception("invalid wid: $wid")).also { newWin ->
      if (win == newWin) {
        return@withLock
      }
      win = newWin
      newWin.state.apply {
        mode = WindowMode.MAXIMIZE
        setFromManifest(browserNMM)
      }
      // 如果没有tab，那么创建一个新的
      // TODO 这里的tab应该从存储中恢复
      if (viewModel.currentTab == null) {
        viewModel.createNewTab()
      }
      /// 提供渲染适配
      windowAdapterManager.provideRender(wid) { modifier ->
        Render(modifier, this)
      }
      newWin.onClose {
        closeWindowSignal.emit()
        winLock.withLock {
          if (newWin == win) {
            win = null
          }
        }
      }
    }
  }

  var viewModel = BrowserViewModel(this, browserNMM, browserServer)

  fun openDwebBrowser(mmid: MMID) =
    ioAsyncScope.launch { browserNMM.bootstrapContext.dns.open(mmid) }

  suspend fun openBrowserView(search: String? = null, url: String? = null) = winLock.withLock {
    viewModel.createNewTab(search, url)
  }

  suspend fun addUrlToDesktop(title: String, url: String, icon: String) {
    // 由于已经放弃了DataStore，所有这边改为直接走WebLinkStore
    val linkId = WebLinkManifest.createLinkId(url)
    // val icons = icon?.toImageResource()?.let { listOf(it) } ?: emptyList()
    val webLinkManifest =
      WebLinkManifest(id = linkId, title = title, url = url, icons = emptyList())
    addWebLinkSignal.emit(webLinkManifest)
    // 先判断是否存在，如果存在就不重复执行
    /*if (DownloadDBStore.checkWebLinkNotExists(context, url)) {
      DownloadDBStore.saveWebLink(context, createDeskWebLink(context, title, url, icon))
    }*/
  }

  suspend fun saveStringToStore(key: String, data:String) = browserStore.saveString(key, data)
  suspend fun getStringFromStore(key: String) = browserStore.getString(key)
}