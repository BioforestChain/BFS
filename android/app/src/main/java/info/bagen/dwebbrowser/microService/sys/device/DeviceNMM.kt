package info.bagen.dwebbrowser.microService.sys.device

import org.dweb_browser.helper.*
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.NativeMicroModule
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes

fun debugDevice(tag: String, msg: Any? = "", err: Throwable? = null) =
  printdebugln("Device", tag, msg, err)

class DeviceNMM: NativeMicroModule("device.sys.dweb") {

    val deviceInfo = DeviceInfo()
    override suspend fun _bootstrap(bootstrapContext: BootstrapContext)
 {
        apiRouting = routes(
            /** 获取设备唯一标识uuid*/
            "/getUUID" bind Method.GET to defineHandler { request ->
                val uuid = Device.getId()
                debugDevice("getUUID",uuid)
               return@defineHandler uuid
            },
            /** 获取手机基本信息*/
            "/info" bind Method.GET to defineHandler { request ->
                val result = deviceInfo.getDeviceInfo()
                Response(Status.OK).body(result)
            },
            /** 获取APP基本信息*/
            "/appInfo" bind Method.GET to defineHandler { request ->
                val result = deviceInfo.getAppInfo()
                Response(Status.OK).body(result)
            },
            /**单独获取手机版本*/
            "/mobileVersion" bind Method.GET to defineHandler { request ->
                val result = deviceInfo.getDevice()
                Response(Status.OK).body(result)
            },
            /** 获取电池基本信息*/
            "/batteryInfo" bind Method.GET to defineHandler { request ->
                val result = deviceInfo.getBatteryInfo()
                Response(Status.OK).body(result)
            },
            /** 获取内存信息*/
            "/storage" bind Method.GET to defineHandler { request ->
                val result = deviceInfo.getStorage()
                Response(Status.OK).body(result)
            },
            /** 获取当前手机状态 看看是不是勿扰模式或者其他*/
            "/module" bind Method.GET to defineHandler { request ->
                Response(Status.OK).body(deviceInfo.deviceData.module)
            },

        )
    }

    override suspend fun _shutdown() {
        TODO("Not yet implemented")
    }
}