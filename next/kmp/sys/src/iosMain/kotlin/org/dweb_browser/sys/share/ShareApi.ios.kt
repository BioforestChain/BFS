package org.dweb_browser.sys.share

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.core.readBytes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import objcnames.classes.LPLinkMetadata
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.core.module.getUIApplication
import org.dweb_browser.helper.toNSString
import org.dweb_browser.helper.trueAlso
import org.dweb_browser.helper.withMainContext
import org.dweb_browser.sys.scan.toNSData
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.LinkPresentation.LPMetadataProvider
import platform.UIKit.UIActivityItemSourceProtocol
import platform.UIKit.UIActivityType
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.darwin.NSObject
import kotlin.experimental.ExperimentalObjCName

actual suspend fun share(
  shareOptions: ShareOptions,
  multiPartData: MultiPartData?,
  shareNMM: MicroModule.Runtime,
): String {

  return withMainContext {
    val files = multiPartData?.let {
      val listFile = mutableListOf<NSData>()
      multiPartData.forEachPart { partData ->
        val r = when (partData) {
          is PartData.FileItem -> {
            partData.provider.invoke().readBytes()
          }

          else -> {
            null
          }
        }

        r?.let {
          listFile.add(r.toNSData())
        }

        partData.dispose()
      }
      listFile
    }

    val deferred = CompletableDeferred<String>()
    val activityItems = mutableListOf<Any>()

    shareOptions.title?.also { activityItems.add(it.toNSString()) }
    shareOptions.text?.also { activityItems.add(it.toNSString()) }
    shareOptions.url?.also { activityItems.add(it.toNSString()) }
    files?.also { activityItems.add(it) }

    val controller =
      UIActivityViewController(activityItems = activityItems, applicationActivities = null)

    shareNMM.getUIApplication().keyWindow?.rootViewController?.apply {

      presentViewController(
        controller, true, null
      )

      controller.completionWithItemsHandler = { _, completed, _, _ ->
        deferred.complete(if (completed) "OK" else "Cancel")
      }
    } ?: deferred.complete("")


    deferred.await()
  }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalObjCName::class, BetaInteropApi::class)
actual suspend fun share(
  shareOptions: ShareOptions,
  files: List<String>,
  shareNMM: MicroModule.Runtime,
): String {
  return withMainContext {
    val deferred = CompletableDeferred<String>()
    val activityItems = mutableListOf<Any>()
    var title: String? = null
    var content = ""

    shareOptions.title?.also {
      it.isNotBlank().trueAlso {
        title = it
        content += it
      }
    }
    shareOptions.text?.also { it.isNotBlank().trueAlso { content += it } }
    shareOptions.url?.also { it.isNotBlank().trueAlso { content += it } }

    val lpMetadataProvider = LPMetadataProvider()

    files.forEachIndexed { index, fileUri ->
      val filePath = fileUri.replace("file://", "")
      activityItems.add(UIImage(contentsOfFile = filePath))

      val fileUrl = NSURL.fileURLWithPath(filePath)
      if (index == 0) {
        CompletableDeferred<Unit>().also { deferred ->
          lpMetadataProvider.startFetchingMetadataForURL(fileUrl) { metadata, _ ->
            when (metadata) {
              null -> {}
              else -> {
                activityItems.add(FileShareModel(title, content, metadata))
              }
            }
            deferred.complete(Unit)
          }
        }.await()
      }
    }

    val controller =
      UIActivityViewController(activityItems = activityItems, applicationActivities = null)

    shareNMM.getUIApplication().keyWindow?.rootViewController?.apply {
      presentViewController(
        controller, true, null
      )
      controller.completionWithItemsHandler = { _, completed, _, _ ->
        deferred.complete(if (completed) "OK" else "Cancel")
      }
    } ?: deferred.complete("")


    deferred.await()
  }
}

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class FileShareModel @OptIn(ExperimentalForeignApi::class) constructor(
  val title: String?,
  val content: String,
  private val lpLinkMetadata: platform.LinkPresentation.LPLinkMetadata? = null,
) : NSObject(), UIActivityItemSourceProtocol {
  //  override fun activityViewController(
//    activityViewController: UIActivityViewController, itemForActivityType: UIActivityType,
//  ): Any {
//    return url.toString()
//  }
  override fun activityViewController(
    activityViewController: UIActivityViewController,
    itemForActivityType: UIActivityType,
  ): String {
    return content
  }


  override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any {
    return title ?: lpLinkMetadata?.title ?: ""
  }

  @OptIn(ExperimentalForeignApi::class)
  override fun activityViewControllerLinkMetadata(activityViewController: UIActivityViewController): LPLinkMetadata? {
    return lpLinkMetadata as LPLinkMetadata
  }
}