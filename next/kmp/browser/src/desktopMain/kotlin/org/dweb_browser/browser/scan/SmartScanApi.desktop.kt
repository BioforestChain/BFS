package org.dweb_browser.browser.scan

import com.google.zxing.BinaryBitmap
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import kotlinx.coroutines.withContext
import org.dweb_browser.browser.util.regexDeepLink
import org.dweb_browser.helper.PromiseOut
import org.dweb_browser.helper.PurePoint
import org.dweb_browser.helper.PureRect
import org.dweb_browser.helper.encodeURIComponent
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.isWebUrl
import org.dweb_browser.helper.platform.DeepLinkHook
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs


actual class ScanningManager actual constructor() {
  actual fun stop() {
  }

  // TODO 使用 openCV 来实现图像识别
  actual suspend fun recognize(data: Any): List<BarcodeResult> {
    val task = PromiseOut<List<BarcodeResult>>()
    if (data is ByteArray) {
      try {
        // 从byte array获取图片
        val bufferedImage = withContext(ioAsyncExceptionHandler) {
          ImageIO.read(ByteArrayInputStream(data))
        }
        val source = BufferedImageLuminanceSource(bufferedImage)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = QRCodeMultiReader()
        task.resolve(
          reader.decodeMultiple(bitmap).map { barcode ->
            // 返回的barcode中，resultPoints有三个标准点，也有可能存在4个，前三个是必定存在的。
            val topLeft = barcode.resultPoints[1]
            val bottomLeft = barcode.resultPoints[0]
            val topRight = barcode.resultPoints[2]
            val boundingBox = PureRect(
              topLeft.x, topLeft.y, abs(topRight.x - topLeft.x), abs(bottomLeft.y - topLeft.y)
            )
            BarcodeResult(
              data = barcode.text,
              boundingBox = boundingBox,
              topLeft = PurePoint(topLeft.x, topLeft.y),
              topRight = PurePoint(topRight.x, topRight.y),
              bottomLeft = PurePoint(bottomLeft.x, bottomLeft.y),
              bottomRight = PurePoint(topRight.x, bottomLeft.y)
            )
          }
        )
      } catch (e: Exception) {
        task.reject(e)
      }
      return task.waitPromise()
    }
    return listOf()
  }
}


/**
 * 打开扫码的结果
 */
actual fun openDeepLink(data: String, showBackground: Boolean): Boolean {
  // 下面判断的是否是 DeepLink，如果不是的话，判断是否是
  val deepLink = data.regexDeepLink() ?: run {
    if (data.isWebUrl()) {
      "dweb://openinbrowser?url=${data.encodeURIComponent()}"
    } else {
      "dweb://search?q=${data.encodeURIComponent()}"
    }
  }
  DeepLinkHook.instance.emitLink(deepLink)
  // Desktop.getDesktop().browse(URI.create(deepLink)) // 走系统 deeplink
  return true
}