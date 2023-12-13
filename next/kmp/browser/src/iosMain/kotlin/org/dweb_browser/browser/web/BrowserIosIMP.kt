package org.dweb_browser.browser.web

import platform.UIKit.UILabel
import platform.UIKit.UIView

interface IosInterface {
  fun getBrowserView(): UIView
  fun doSearch(key: String)
  fun gobackIfCanDo(): Boolean
}

class BrowserIosIMP {

  private var imp: IosInterface? = null

  fun registerIosIMP(imp: IosInterface) {
    this.imp = imp
  }

  fun createIosMainView(): UIView {
    return imp?.getBrowserView() ?: UILabel().apply {
      this.text = "iOS Main View Load Fail"
    }
  }

  fun doSearch(key: String) {
    imp?.doSearch(key)
  }

  fun gobackIfCanDo() = imp?.let { it.gobackIfCanDo() } ?: false
}