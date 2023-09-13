package org.dweb_browser.helper.compose

import androidx.compose.runtime.Composable
import org.dweb_browser.helper.platform.OffscreenWebCanvas

@Composable
internal actual fun rememberOffscreenWebCanvas(): OffscreenWebCanvas {
  return OffscreenWebCanvas(0, 0)
}