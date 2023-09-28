package info.bagen.dwebbrowser.microService.browser.jmm

import androidx.compose.runtime.Composable
import info.bagen.dwebbrowser.microService.browser.jmm.ui.JmmManagerViewHelper
import io.ktor.http.URLBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.buildUnsafeString
import org.dweb_browser.microservice.help.types.JmmAppInstallManifest
import org.dweb_browser.microservice.help.types.MMID
import org.dweb_browser.microservice.http.PureRequest
import org.dweb_browser.microservice.http.PureStringBody
import org.dweb_browser.microservice.ipc.ReadableStreamIpc
import org.dweb_browser.microservice.ipc.helper.IpcEvent
import org.dweb_browser.microservice.ipc.helper.IpcMethod
import org.dweb_browser.microservice.sys.dns.nativeFetch
import org.dweb_browser.microservice.sys.download.DownloadController
import org.dweb_browser.microservice.sys.download.DownloadInfo
import org.dweb_browser.window.core.WindowController
import org.dweb_browser.window.core.createWindowAdapterManager

enum class EIpcEvent(val event: String) {
  State("state"), Ready("ready"), Activity("activity"), Close("close")
}

class JmmController(
  val win: WindowController, // 窗口控制器
  private val jmmNMM: JmmNMM,
  private val jmmAppInstallManifest: JmmAppInstallManifest
) {

  private val openLock = Mutex()
  val viewModel: JmmManagerViewHelper = JmmManagerViewHelper(jmmAppInstallManifest, this)

  fun getApp(mmid: MMID) = jmmNMM.getApps(mmid)

  private val closeSignal = SimpleSignal()
  val onClosed = closeSignal.toListener()

  internal val downloadSignal = Signal<DownloadInfo>()
  val onDownload = downloadSignal.toListener()

  init {
    val wid = win.id
    /// 提供渲染适配
    createWindowAdapterManager.renderProviders[wid] =
      @Composable { modifier ->
        Render(modifier, this)
      }
    /// 窗口销毁的时候
    win.onClose {
      // 移除渲染适配器
      createWindowAdapterManager.renderProviders.remove(wid)
    }
    onClosed {
      win.close(true)
    }
  }

  suspend fun openApp(mmid: MMID) {
    openLock.withLock {

      val (ipc) = jmmNMM.bootstrapContext.dns.connect(mmid)
      ipc.postMessage(IpcEvent.fromUtf8(EIpcEvent.Activity.event, ""))

      val (deskIpc) = jmmNMM.bootstrapContext.dns.connect("desk.browser.dweb")
      debugJMM("openApp", "postMessage==>activity desk.browser.dweb")
      deskIpc.postMessage(IpcEvent.fromUtf8(EIpcEvent.Activity.event, ""))
    }
  }

  suspend fun downloadAndSaveZip(appInstallManifest: JmmAppInstallManifest) {
    jmmNMM.nativeFetch(
      PureRequest(
        href = "file://download.sys.dweb/download",
        method = IpcMethod.POST,
        body = PureStringBody(Json.encodeToString(appInstallManifest))
      )
    )
  }

  suspend fun updateDownloadState(downloadController: DownloadController) {
    val url = when (downloadController) {
      DownloadController.PAUSE -> "file://download.sys.dweb/pause"
      DownloadController.CANCEL -> "file://download.sys.dweb/cancel"
      DownloadController.RESUME -> "file://download.sys.dweb/resume"
    }
    jmmNMM.nativeFetch(PureRequest(href = url, method = IpcMethod.GET))
  }

  suspend fun closeSelf() {
    closeSignal.emit()
  }
}