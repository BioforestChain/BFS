package info.bagen.dwebbrowser.microService.sys.window


import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dweb_browser.helper.Observable
import org.dweb_browser.helper.printDebug
import org.dweb_browser.helper.toJsonElement
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.NativeMicroModule
import org.dweb_browser.microservice.ipc.helper.ReadableStream
import org.dweb_browser.window.core.Rect
import org.dweb_browser.window.core.constant.WindowPropertyKeys
import org.dweb_browser.window.core.constant.WindowStyle
import org.dweb_browser.window.core.windowInstancesManager
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.lens.Query
import org.http4k.lens.boolean
import org.http4k.lens.composite
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.routes

fun debugWindowNMM(tag: String, msg: Any? = "", err: Throwable? = null) =
  printDebug("window-nmm", tag, msg, err)

/**
 * 标准化窗口管理模块
 *
 * 该模块暂时不会下放到 std 级别，std 级别通常属于非常底层的中立标准，比如通讯等与客观物理相关的，std是一个dweb平台的最小子集，未来可以基于该标准做认证平台。
 * 而sys级别拥有各异的实现，不同的厂商可以在这个级别做自己的操作系统标准化设计。
 * 这里的windows.sys.dweb属于当下这个时代的一种矩形窗口化设计，它不代表所有的窗口形态，它有自己的取舍。
 */
class WindowNMM : NativeMicroModule("window.sys.dweb", "Window Management") {

  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    val query_wid = Query.string().required("wid")


    val query_Style = Query.composite {
      WindowStyle(
        topBarOverlay = boolean().optional("topBarOverlay")(it),
        bottomBarOverlay = boolean().optional("bottomBarOverlay")(it),
        topBarContentColor = string().optional("topBarContentColor")(it),
        topBarContentDarkColor = string().optional("topBarContentDarkColor")(it),
        topBarBackgroundColor = string().optional("topBarBackgroundColor")(it),
        topBarBackgroundDarkColor = string().optional("topBarBackgroundDarkColor")(it),
        bottomBarContentColor = string().optional("bottomBarContentColor")(it),
        bottomBarContentDarkColor = string().optional("bottomBarContentDarkColor")(it),
        bottomBarBackgroundColor = string().optional("bottomBarBackgroundColor")(it),
        bottomBarBackgroundDarkColor = string().optional("bottomBarBackgroundDarkColor")(it),
        bottomBarTheme = string().optional("bottomBarTheme")(it),
        themeColor = string().optional("themeColor")(it),
        themeDarkColor = string().optional("themeDarkColor")(it),
      )
    }

    fun getWindow(request: Request) = query_wid(request).let { wid ->
      windowInstancesManager.get(wid) ?: throw Exception("No Found by window id: '$wid'")
    }

    apiRouting = routes(
      /** 窗口的状态监听 */
      "/observe" bind Method.GET to defineInputStreamHandler {
        val win = getWindow(request)
        debugWindowNMM("/observe", "wid: ${win.id} ,mmid: ${ipc.remote.mmid}")
        val inputStream = ReadableStream(onStart = { controller ->
          val off = win.state.observable.onChange {
            try {
              controller.enqueue(Json.encodeToString(win.state.toJsonElement()) + "\n")
            } catch (e: Exception) {
              controller.close()
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
            controller.close()
          }
        })
        inputStream
      },
      "/getState" bind Method.GET to defineJsonResponse {
        getWindow(request).state.toJsonElement()
      },
      "/focus" bind Method.GET to defineEmptyResponse { request -> getWindow(request).focus() },
      "/blur" bind Method.GET to defineEmptyResponse { request -> getWindow(request).blur() },
      "/maximize" bind Method.GET to defineEmptyResponse { request -> getWindow(request).maximize() },
      "/unMaximize" bind Method.GET to defineEmptyResponse { request -> getWindow(request).unMaximize() },
      "/minimize" bind Method.GET to defineEmptyResponse { request -> getWindow(request).toggleVisible() },
      "/close" bind Method.GET to defineEmptyResponse { request -> getWindow(request).close() },
      "/setStyle" bind Method.GET to defineEmptyResponse { request ->
        getWindow(request).setStyle(
          query_Style(request)
        )
      },
      "/display" bind Method.GET to defineJsonResponse {
        val manager =  getWindow(request).manager?:return@defineJsonResponse "not found window".toJsonElement()
        val state = manager.state
        @Serializable
        data class Display(
          val height:Float,
          val width: Float,
          val imeBoundingRect: Rect
        )
        Display(state.viewHeightDp,state.viewWidthDp,state.imeBoundingRect).toJsonElement()
      }
    )
  }

  override suspend fun _shutdown() {
  }

}