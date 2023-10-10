package info.bagen.dwebbrowser.microService.sys.window


import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dweb_browser.helper.Observable
import org.dweb_browser.helper.printDebug
import org.dweb_browser.helper.toJsonElement
import org.dweb_browser.core.module.BootstrapContext
import org.dweb_browser.core.module.NativeMicroModule
import org.dweb_browser.core.http.PureRequest
import org.dweb_browser.core.http.bind
import org.dweb_browser.core.ipc.helper.ReadableStream
import org.dweb_browser.core.std.dns.nativeFetch
import org.dweb_browser.window.core.constant.WindowPropertyKeys
import org.dweb_browser.window.core.constant.WindowStyle
import org.dweb_browser.window.core.windowInstancesManager

fun debugWindowNMM(tag: String, msg: Any? = "", err: Throwable? = null) =
  printDebug("window-nmm", tag, msg, err)

/**
 * 标准化窗口管理模块
 *
 * 该模块暂时不会下放到 std 级别，std 级别通常属于非常底层的中立标准，比如通讯等与客观物理相关的，std是一个dweb平台的最小子集，未来可以基于该标准做认证平台。
 * 而sys级别拥有各异的实现，不同的厂商可以在这个级别做自己的操作系统标准化设计。
 * 这里的windows.sys.dweb属于当下这个时代的一种矩形窗口化设计，它不代表所有的窗口形态，它有自己的取舍。
 *
 * TODO
 * 这里将由 deskNMM 提供直接的支持
 */
class WindowNMM : NativeMicroModule("window.sys.dweb", "Window Management") {

  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    fun getWindow(request: PureRequest) = request.query("wid").let { wid ->
      windowInstancesManager.get(wid) ?: throw Exception("No Found by window id: '$wid'")
    }

    routes(
      /// 打开主窗口，获取主窗口句柄
      "/openMainWindow" bind HttpMethod.Get to defineEmptyResponse {
        nativeFetch("file://desk.browser.dweb/openAppOrActivate?app_id=${ipc.remote.mmid}")
      },
      /** 窗口的状态监听 */
      "/observe" bind HttpMethod.Get to definePureStreamHandler {
        val win = getWindow(request)
        debugWindowNMM("/observe", "wid: ${win.id} ,mmid: ${ipc.remote.mmid}")
        val inputStream = ReadableStream { controller ->
          val off = win.state.observable.onChange {
            try {
              controller.enqueue(Json.encodeToString(win.state.toJsonElement()) + "\n")
            } catch (e: Exception) {
              controller.closeWrite()
              e.printStackTrace()
            }
          }.also {
            win.coroutineScope.launch {
              it.emitSelf(
                Observable.Change(
                  WindowPropertyKeys.Constants, null, null
                )
              )
            }
          }
          ipc.onClose {
            off()
            controller.closeWrite()
          }
        }
        inputStream.stream
      },
      "/getState" bind HttpMethod.Get to defineJsonResponse {
        val controller = getWindow(request)
        controller.state.toJsonElement()
      },
      "/focus" bind HttpMethod.Get to defineEmptyResponse { getWindow(request).focus() },
      "/blur" bind HttpMethod.Get to defineEmptyResponse { getWindow(request).blur() },
      "/maximize" bind HttpMethod.Get to defineEmptyResponse { getWindow(request).maximize() },
      "/unMaximize" bind HttpMethod.Get to defineEmptyResponse { getWindow(request).unMaximize() },
      "/visible" bind HttpMethod.Get to defineEmptyResponse { getWindow(request).toggleVisible() },
      "/close" bind HttpMethod.Get to defineEmptyResponse { getWindow(request).close() },
      "/setStyle" bind HttpMethod.Get to defineEmptyResponse {
        getWindow(request).setStyle(request.queryAs<WindowStyle>())
      },
    )
  }

  override suspend fun _shutdown() {
  }

}