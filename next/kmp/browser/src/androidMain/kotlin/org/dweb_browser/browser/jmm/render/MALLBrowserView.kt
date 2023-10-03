package org.dweb_browser.browser.jmm.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import org.dweb_browser.browser.jmm.ui.LocalJmmViewHelper

@Composable
fun MALLBrowserView(onBack: () -> Unit) {
  val jmmMetadata = LocalJmmViewHelper.current.uiState.jmmAppInstallManifest
  val topBarAlpha = remember { mutableFloatStateOf(0f) }
  val lazyListState = rememberLazyListState()
  val screenWidth = LocalConfiguration.current.screenWidthDp
  val screenHeight = LocalConfiguration.current.screenHeightDp
  val density = LocalContext.current.resources.displayMetrics.density
  val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
  val previewState = remember {
    PreviewState(
      outsideLazy = lazyListState,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      statusBarHeight = statusBarHeight,
      density = density
    )
  }
  val firstHeightPx = HeadHeight.value * density / 2 // 头部item的高度是128.dp
  val scope = rememberCoroutineScope()

  LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.firstVisibleItemScrollOffset }.collect {
      topBarAlpha.floatValue = when (lazyListState.firstVisibleItemIndex) {
        0 -> if (it < firstHeightPx) {
          0f
        } else {
          (it - firstHeightPx) / firstHeightPx
        }

        else -> 1f
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
    //.navigationBarsPadding()
  ) {
    AppInfoContentView(lazyListState, jmmMetadata) { index, imageLazyListState ->
      scope.launch {
        previewState.selectIndex.value = index
        previewState.imageLazy = imageLazyListState
        previewState.offset.value = measureCenterOffset(index, previewState)
        previewState.showPreview.targetState = true
      }
    }
//    TopAppBar(topBarAlpha, jmmMetadata.name, onBack)
    BottomDownloadButton()
    ImagePreview(jmmMetadata, previewState)
    DialogForWebviewVersion()
  }
}