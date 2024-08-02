package org.dweb_browser.browser.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.dweb_browser.helper.platform.IPureViewController
import org.dweb_browser.helper.platform.PureViewControllerPlatform
import org.dweb_browser.helper.platform.platform
import org.dweb_browser.sys.window.core.WindowContentRenderScope

@Composable
fun WindowContentRenderScope.RenderBarcodeScanning(
  modifier: Modifier, controller: SmartScanController
) {
  val selectImg by controller.albumImageFlow.collectAsState()
  // 当用户选中文件的时候切换到Album模式
  selectImg?.let {
    controller.previewTypes.value = SmartModuleTypes.Album
  }
  Box(modifier) {
    when (controller.previewTypes.value) {
      // 视图切换,如果扫描到了二维码
      SmartModuleTypes.Scanning -> {
        // 渲染相机内容
        CameraPreviewRender(
          modifier = Modifier.fillMaxSize(), controller = controller
        )
        // 扫描线和打开相册，暂时不再桌面端支持
        when (IPureViewController.platform) {
          PureViewControllerPlatform.Desktop -> {
            controller.DefaultScanningView(Modifier.fillMaxSize().zIndex(2f), false)
          }

          PureViewControllerPlatform.Apple, PureViewControllerPlatform.Android -> {
            controller.DefaultScanningView(Modifier.fillMaxSize().zIndex(2f))
          }
        }
        // 渲染扫码结果
        controller.RenderScanResultView(
          Modifier.matchParentSize().zIndex(3f)
        )
      }
      // 相册选择
      SmartModuleTypes.Album -> {
        if (selectImg == null) {
          AlbumPreviewRender(modifier, controller)
        }
        selectImg?.let {
          // 如果是选中图片，渲染选中的图片
          controller.RenderAlbumPreview(
            Modifier.fillMaxSize(), it
          )
        }
      }
      // 内窥模式
      SmartModuleTypes.Endoscopic -> {
        controller.EndoscopicPreview(modifier)
        ScannerLine() // 添加扫描线
        // 渲染扫码结果
        controller.RenderScanResultView(
          Modifier.matchParentSize().zIndex(3f)
        )
      }
    }
  }
}

/**相机preview视图*/
@Composable
expect fun CameraPreviewRender(
  modifier: Modifier = Modifier, controller: SmartScanController
)

/**这里是文件选择视图*/
@Composable
expect fun AlbumPreviewRender(
  modifier: Modifier = Modifier, controller: SmartScanController
)