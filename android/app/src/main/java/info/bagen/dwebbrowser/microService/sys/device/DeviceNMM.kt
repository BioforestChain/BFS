package info.bagen.dwebbrowser.microService.sys.device

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.dweb_browser.helper.printDebug
import org.dweb_browser.helper.randomUUID
import org.dweb_browser.helper.toJsonElement
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.NativeMicroModule
import org.dweb_browser.microservice.help.types.MICRO_MODULE_CATEGORY
import org.dweb_browser.microservice.http.PureResponse
import org.dweb_browser.microservice.http.PureStringBody
import org.dweb_browser.microservice.http.bind
import org.dweb_browser.microservice.std.file.ext.store

fun debugDevice(tag: String, msg: Any? = "", err: Throwable? = null) =
  printDebug("Device", tag, msg, err)

class DeviceNMM : NativeMicroModule("device.sys.dweb", "Device Info") {
  init {
    short_name = "Device";
    categories =
      listOf(MICRO_MODULE_CATEGORY.Service, MICRO_MODULE_CATEGORY.Device_Management_Service);
  }

  val deviceInfo = DeviceInfo()
  val UUID_KEY = "UUID"
  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    routes(
      /** 获取设备唯一标识uuid*/
      "/uuid" bind HttpMethod.Get to defineJsonResponse {
        val uuid = store.getOrPut(UUID_KEY) { randomUUID() }
        debugDevice("getUUID", uuid)
        UUIDResponse(uuid).toJsonElement()
      },
      /** 获取手机基本信息*/
      "/info" bind HttpMethod.Get to definePureResponse {
        val result = deviceInfo.getDeviceInfo()
        PureResponse(HttpStatusCode.OK, body = PureStringBody(result))
      },
      /** 获取APP基本信息*/
      "/appInfo" bind HttpMethod.Get to definePureResponse {
        val result = deviceInfo.getAppInfo()
        PureResponse(HttpStatusCode.OK, body = PureStringBody(result))
      },
      /**单独获取手机版本*/
      "/mobileVersion" bind HttpMethod.Get to definePureResponse {
        val result = deviceInfo.getDevice()
        PureResponse(HttpStatusCode.OK, body = PureStringBody(result))
      },
      /** 获取电池基本信息*/
      "/batteryInfo" bind HttpMethod.Get to definePureResponse {
        val result = deviceInfo.getBatteryInfo()
        PureResponse(HttpStatusCode.OK, body = PureStringBody(result))
      },
      /** 获取内存信息*/
      "/storage" bind HttpMethod.Get to definePureResponse {
        val result = deviceInfo.getStorage()
        PureResponse(HttpStatusCode.OK, body = PureStringBody(result))
      },
      /** 获取当前手机状态 看看是不是勿扰模式或者其他*/
      "/module" bind HttpMethod.Get to definePureResponse {
        PureResponse(HttpStatusCode.OK, body = PureStringBody(deviceInfo.deviceData.module))
      },
    )
  }

  @Serializable
  data class UUIDResponse(val uuid: String)

  override suspend fun _shutdown() {

  }
}