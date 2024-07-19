package org.dweb_browser.browser.scan

import org.dweb_browser.core.http.router.bind
import org.dweb_browser.core.http.router.byChannel
import org.dweb_browser.helper.datetimeNow
import org.dweb_browser.helper.toJsonElement
import org.dweb_browser.pure.http.PureBinaryFrame
import org.dweb_browser.pure.http.PureMethod
import org.dweb_browser.pure.http.PureTextFrame


suspend fun SmartScanNMM.ScanRuntime.barcodeScanning(scanningManager:ScanningManager) {
  protocol("barcode-scanning.sys.dweb") {

    routes(
      "/process" byChannel { ctx ->
        val time = datetimeNow()
        var rotation = 0
        for (frame in ctx) {
          when (frame) {
            is PureTextFrame -> {
              debugSCAN("process=>byChannel", "PureTextFrame($time)")
              rotation = frame.text.toIntOrNull() ?: 0
            }

            is PureBinaryFrame -> {
              debugSCAN("process=>byChannel", "PureBinaryFrame($time) $rotation")
              val result = try {
                scanningManager.recognize(frame.binary, rotation)
              } catch (e: Throwable) {
                debugSCAN("process=>byChannel", null, e)
                emptyList()
              }
              debugSCAN("process=>byChannel", result.joinToString(", ") { it.data })
              // 不论 result 是否为空数组，都进行响应
              ctx.sendJson(result)
            }

            else -> {
              ctx.getChannel().close()
            }
          }
        }
      },
      // 处理二维码图像
      "/process" bind PureMethod.POST by defineJsonResponse {
        val rotation = request.queryOrNull("rotation")?.toIntOrNull() ?: 0
        debugSCAN("process=>POST", "rotation=$rotation, ${request.body.contentLength}")

        val imgBitArray = request.body.toPureBinary()
        val result = try {
          scanningManager.recognize(imgBitArray, rotation)
        } catch (e: Throwable) {
          debugSCAN("process=>POST", null, e)
          emptyList()
        }
        debugSCAN("process=>POST", result.joinToString(", ") { it.data })
        return@defineJsonResponse result.toJsonElement()
      },

      // 停止处理
      "/stop" bind PureMethod.GET by defineBooleanResponse {
        scanningManager.stop()
        return@defineBooleanResponse true
      },
    ).cors()
  }
}