package info.bagen.dwebbrowser.microService.browser.nativeui.splashScreen

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.dweb_browser.helper.printDebug
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.NativeMicroModule
import org.dweb_browser.microservice.help.types.MICRO_MODULE_CATEGORY
import org.dweb_browser.microservice.http.PureResponse
import org.dweb_browser.microservice.http.PureStringBody
import org.dweb_browser.microservice.http.bind

fun debugSplashScreen(tag: String, msg: Any? = "", err: Throwable? = null) =
  printDebug("SplashScreen", tag, msg, err)

class SplashScreenNMM : NativeMicroModule("splash-screen.nativeui.browser.dweb", "splashScreen") {

  init {
    categories = listOf(MICRO_MODULE_CATEGORY.Service, MICRO_MODULE_CATEGORY.Render_Service);
  }

  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    routes(
      /** 显示*/
      "/show" bind HttpMethod.Get to definePureResponse {
//                val options = querySplashScreenSettings(request)
//                val currentController = currentController(ipc.remote.mmid)
//                val microModule = bootstrapContext.dns.query(ipc.remote.mmid)
//                if (microModule is JsMicroModule) {
//                    val metadata = microModule.metadata
//                    debugSplashScreen(
//                        "show",
//                        "remoteId:${ipc.remote.mmid} show===>${options} "
//                    )
//                    if (currentController != null) {
//                        show(currentController, metadata, options)
//                        return@defineHandler Response(Status.OK)
//                    }
//                }

        PureResponse(
          HttpStatusCode.InternalServerError,
          body = PureStringBody("No current activity found")
        )
      },
      /** 隐藏*/
      "/hide" bind HttpMethod.Get to defineEmptyResponse {
//                val options = queryHideOptions(request)
//                val currentActivity = currentController(ipc.remote.mmid)?.activity
//                debugSplashScreen("hide", "apiRouting hide===>${options}")
//                if (currentActivity != null) {
////                    splashScreen.hide(options)
//                    return@defineHandler Response(Status.OK)
//                }
//                Response(Status.INTERNAL_SERVER_ERROR).body("No current activity found")
      },
    )
  }

  override suspend fun _shutdown() {
  }
}

