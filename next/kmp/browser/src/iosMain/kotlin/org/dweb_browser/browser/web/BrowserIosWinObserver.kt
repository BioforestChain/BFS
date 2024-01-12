package org.dweb_browser.browser.web

import kotlinx.cinterop.ExperimentalForeignApi
import org.dweb_browser.browser.web.model.BrowserViewModel
import org.dweb_browser.helper.OffListener
import org.dweb_browser.helper.platform.ios_browser.DwebWebView
import org.dweb_browser.helper.platform.ios_browser.browserActiveOn
import org.dweb_browser.helper.platform.ios_browser.browserClear

@OptIn(ExperimentalForeignApi::class)
class BrowserIosWinObserver() {

  private var onWinVisibleListener: OffListener<Boolean>? = null
  private var onWinCloseListener: OffListener<Unit>? = null

  var iOSBrowserView: DwebWebView? = null

  var browserViewModel: BrowserViewModel? = null
    set(value) {
      cancelViewModeObservers()
      value?.let {
        onWinVisibleListener = it.browserOnVisible { isVisiable ->
          iOSBrowserView?.let {
            it.browserActiveOn(isVisiable)
          }
        }
        onWinCloseListener = it.browserOnClose {
          iOSBrowserView?.let {
            it.browserClear()
          }
          cancelViewModeObservers()
        }
      }
    }

  private fun cancelViewModeObservers() {
    onWinVisibleListener?.let {
      it()
    }
    onWinCloseListener?.let {
      it()
    }
  }
}