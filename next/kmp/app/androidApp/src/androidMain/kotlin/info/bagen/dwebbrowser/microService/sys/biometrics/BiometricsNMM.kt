package info.bagen.dwebbrowser.microService.sys.biometrics

import android.content.Intent
import info.bagen.dwebbrowser.microService.sys.biometrics.BiometricsActivity.Companion.biometrics_promise_out
import info.bagen.dwebbrowser.microService.sys.biometrics.BiometricsController.Companion.biometricsController
import io.ktor.http.HttpMethod
import org.dweb_browser.core.module.getAppContext
import org.dweb_browser.helper.printDebug
import org.dweb_browser.helper.toJsonElement
import org.dweb_browser.core.module.BootstrapContext
import org.dweb_browser.core.module.NativeMicroModule
import org.dweb_browser.core.help.types.MICRO_MODULE_CATEGORY
import org.dweb_browser.core.help.types.MMID
import org.dweb_browser.core.http.router.bind
import org.dweb_browser.helper.PromiseOut

fun debugBiometrics(tag: String, msg: Any? = "", err: Throwable? = null) =
  printDebug("biometrics", tag, msg, err)

class BiometricsNMM : NativeMicroModule("biometrics.sys.dweb", "biometrics") {

  init {
    categories =
      listOf(MICRO_MODULE_CATEGORY.Service, MICRO_MODULE_CATEGORY.Machine_Learning_Service);
  }

  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    routes(/** 检查识别支持生物识别*/
      "/check" bind HttpMethod.Get to defineBooleanResponse {
        val type = request.queryOrNull("type") ?: ""
        debugBiometrics("check", type)

        openActivity(ipc.remote.mmid, request.queryAs<BiometricsData>())
        val context = biometricsController.waitActivityCreated()
        val result = context.chuck()
        context.finish()
        return@defineBooleanResponse result
      },
      /** 生物识别*/
      "/biometrics" bind HttpMethod.Get to defineJsonResponse {
        biometrics_promise_out = PromiseOut()
        debugBiometrics("fingerprint", ipc.remote.mmid)
        openActivity(ipc.remote.mmid)
        val context = biometricsController.waitActivityCreated()
        context.biometrics()
        return@defineJsonResponse biometrics_promise_out.waitPromise().toJsonElement()
      }).cors()
  }

  fun openActivity(remoteMMID: MMID) {
    val context = getAppContext()
    val intent = Intent(context, BiometricsActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

  private fun openActivity(remoteMMID: MMID, data: BiometricsData) {
    val context = getAppContext()
    val intent = Intent(context, BiometricsActivity::class.java)
    intent.putExtra("data", data)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

  override suspend fun _shutdown() {
  }
}