package org.dweb_browser.pure.image.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.dweb_browser.pure.image.OffscreenWebCanvas

private var singletonOffscreenWebCanvasCache: OffscreenWebCanvas? = null

@Composable
internal actual fun rememberOffscreenWebCanvas(): OffscreenWebCanvas {
  return remember {
    if (singletonOffscreenWebCanvasCache == null) {
      singletonOffscreenWebCanvasCache = OffscreenWebCanvas()
    }
    singletonOffscreenWebCanvasCache!!
  }
}