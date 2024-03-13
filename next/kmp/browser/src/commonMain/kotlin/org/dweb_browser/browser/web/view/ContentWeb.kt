package org.dweb_browser.browser.web.view

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import org.dweb_browser.browser.common.loading.LoadingView
import org.dweb_browser.browser.common.toWebColorScheme
import org.dweb_browser.browser.web.data.BrowserContentItem
import org.dweb_browser.browser.web.data.WebSiteType
import org.dweb_browser.browser.web.model.BrowserViewModel
import org.dweb_browser.browser.web.model.toWebSiteInfo
import org.dweb_browser.dwebview.Render
import org.dweb_browser.dwebview.ScrollChangeEvent
import org.dweb_browser.helper.OffListener
import org.dweb_browser.helper.capturable.capturable
import org.dweb_browser.sys.window.core.WindowRenderScope
import org.dweb_browser.sys.window.render.LocalWindowController
import org.dweb_browser.sys.window.render.watchedState

@Composable
internal fun BrowserWebView(
  viewModel: BrowserViewModel,
  browserContentItem: BrowserContentItem,
  windowRenderScope: WindowRenderScope
) {
  val contentViewItem = browserContentItem.contentWebItem.value ?: return
  val scope = rememberCoroutineScope()
  DisposableEffect(contentViewItem.viewItem.webView) { // 点击跳转时，加载状态变化，将底部栏显示
    val job = scope.launch {
      contentViewItem.viewItem.webView.loadingProgressFlow.collect {
        when (it) {
          1f -> {
            contentViewItem.loadState.value = false
            contentViewItem.viewItem.webView.toWebSiteInfo(WebSiteType.History)?.let { item ->
              viewModel.addHistoryLink(item)
            }
            browserContentItem.captureView()
          }

          else -> {
            contentViewItem.loadState.value = true
          }
        }
      }
    }
    onDispose {
      job.cancel()
    }
  }

  BoxWithConstraints(Modifier.capturable(browserContentItem.controller).fillMaxSize()) {
    val win = LocalWindowController.current
    val colorScheme by win.watchedState { colorScheme }
    LaunchedEffect(colorScheme) {
      contentViewItem.viewItem.webView.setPrefersColorScheme(colorScheme.toWebColorScheme())
    }

    val density = LocalDensity.current.density

    LaunchedEffect(windowRenderScope.scale, maxWidth, maxHeight) {
      contentViewItem.viewItem.webView.setContentScale(
        windowRenderScope.scale,
        maxWidth.value,
        maxHeight.value,
        density,
      )
    }

    key(contentViewItem.viewItem) {
      var off by remember { mutableStateOf<OffListener<ScrollChangeEvent>?>(null) }

      contentViewItem.viewItem.webView.Render(
        modifier = Modifier.fillMaxSize(),
        onCreate = {
          val webView = contentViewItem.viewItem.webView
          off = webView.onScroll {
            contentViewItem.webViewY = it.scrollY // 用于截图的时候进行定位截图
          }
        },
        onDispose = {
          off?.also {
            it()
            off = null
          }
        }
      )
    }
  }
  LoadingView(contentViewItem.loadState) // 先不显示加载框。
}
