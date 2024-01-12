package org.dweb_browser.sys.media

import kotlinx.cinterop.ExperimentalForeignApi
import org.dweb_browser.helper.platform.MultiPartFile
import org.dweb_browser.helper.platform.MultiPartFileEncode
import org.dweb_browser.helper.platform.MultipartFieldDescription
import org.dweb_browser.helper.toBase64ByteArray
import org.dweb_browser.sys.scan.toNSData
import platform.Foundation.NSMutableData
import platform.Foundation.appendData
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum

actual suspend fun savePictures(saveLocation: String, files: List<MultiPartFile>) {
  files.forEach { multiPartFile ->
    savePicture(multiPartFile)
  }
}

private fun savePicture(files: MultiPartFile) {
  val data = when (files.encoding) {
    MultiPartFileEncode.UTF8 -> files.data.encodeToByteArray()
    MultiPartFileEncode.BASE64 -> files.data.toBase64ByteArray()
    MultiPartFileEncode.BINARY -> files.data.encodeToByteArray()
  }

  val uiImage = UIImage(data = data.toNSData())
  //图片对象、目标对象、一个完成时调用的选择器（selector）以及一个上下文信息对象
  saveImageToPhotosAlbum(uiImage)
}

@OptIn(ExperimentalForeignApi::class)
fun saveImageToPhotosAlbum(image: UIImage) {

  debugMedia("ios ","saveImageToPhotosAlbum ${image.images?.size}")
  UIImageWriteToSavedPhotosAlbum(
    image,
    null,
    null,
    null
  )
}

actual fun MediaPicture.Companion.create(saveLocation: String, desc: MultipartFieldDescription): MediaPicture = MediaPictureImpl(saveLocation, desc)

class MediaPictureImpl(saveLocation: String, desc: MultipartFieldDescription) : MediaPicture(saveLocation, desc) {
  val data = NSMutableData()
  override suspend fun consumePicktureChunk(chunk: ByteArray) {
    data.appendData(chunk.toNSData())
  }

  // TODO: 自定义存储目录需要权限
  override suspend fun savePicture() {
    val uiImage = UIImage(data = data)
    saveImageToPhotosAlbum(uiImage)
  }
}