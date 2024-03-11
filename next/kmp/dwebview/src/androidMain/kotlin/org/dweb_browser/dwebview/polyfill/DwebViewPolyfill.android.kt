package org.dweb_browser.dwebview.polyfill

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.dweb_browser.helper.SuspendOnce

object DwebViewAndroidPolyfill : DwebViewCommonPolyfill() {
  internal val prepare = SuspendOnce {
    coroutineScope {
      launch {
        KeyBoard = readDwebviewPolyfill("keyboard.android.js")
        Favicon = readDwebviewPolyfill("favicon.common.js")
        CloseWatcher = readDwebviewPolyfill("close-watcher.common.js")
        UserAgentData = readDwebviewPolyfill("user-agent-data.common.js")
      }
    }
  }

  lateinit var KeyBoard: String
    private set
  lateinit var Favicon: String
    private set
  lateinit var CloseWatcher: String
    private set
  lateinit var UserAgentData: String
    private set
}