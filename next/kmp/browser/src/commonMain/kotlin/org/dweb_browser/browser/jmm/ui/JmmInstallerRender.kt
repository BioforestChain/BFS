package org.dweb_browser.browser.jmm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.dweb_browser.browser.jmm.JmmInstallerController
import org.dweb_browser.browser.jmm.model.LocalJmmViewHelper
import org.dweb_browser.browser.jmm.render.BottomDownloadButton
import org.dweb_browser.browser.jmm.render.ImagePreview
import org.dweb_browser.browser.jmm.render.PreviewState
import org.dweb_browser.browser.jmm.render.WebviewVersionWarningDialog
import org.dweb_browser.browser.jmm.render.app.Render
import org.dweb_browser.browser.jmm.render.measureCenterOffset
import org.dweb_browser.helper.compose.rememberScreenSize
import org.dweb_browser.sys.window.core.WindowRenderScope
import org.dweb_browser.sys.window.render.LocalWindowController

@Composable
fun JmmInstallerController.Render(modifier: Modifier, renderScope: WindowRenderScope) {
  val lazyListState = rememberLazyListState()
  val screenSize = rememberScreenSize()
  val density = LocalDensity.current.density
  val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
  val previewState = remember {
    PreviewState(
      outsideLazy = lazyListState,
      screenWidth = screenSize.screenWidth,
      screenHeight = screenSize.screenHeight,
      statusBarHeight = statusBarHeight,
      density = density
    )
  }

  val win = LocalWindowController.current
  win.state.title = this.viewModel.uiState.jmmHistoryMetadata.metadata.name
  win.GoBackHandler {
    if (previewState.showPreview.targetState) {
      previewState.showPreview.targetState = false
    } else {
      closeSelf()
    }
  }

  CompositionLocalProvider(LocalJmmViewHelper provides viewModel) {
    Box(modifier = with(renderScope) {
      modifier
        .requiredSize((width / scale).dp, (height / scale).dp) // 原始大小
        .scale(scale)
    }) {
      val jmmMetadata = viewModel.uiState.jmmHistoryMetadata
      jmmMetadata.metadata.Render { index, imageLazyListState ->
        previewState.selectIndex.value = index
        previewState.imageLazy = imageLazyListState
        previewState.offset.value = measureCenterOffset(index, previewState)
        previewState.showPreview.targetState = true
      }
      BottomDownloadButton()
      ImagePreview(jmmMetadata.metadata, previewState)
      WebviewVersionWarningDialog()
    }
  }
}