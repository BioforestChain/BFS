package info.bagen.dwebbrowser.microService.browser.nativeui.helper

import info.bagen.dwebbrowser.microService.browser.mwebview.MultiWebViewNMM
import org.dweb_browser.microservice.help.MMID
import info.bagen.dwebbrowser.microService.browser.nativeui.NativeUiController

fun NativeUiController.Companion.fromMultiWebView(mmid: MMID) =
    ((MultiWebViewNMM.getCurrentWebViewController(mmid)
        ?: throw Exception("native ui is unavailable for $mmid")).lastViewOrNull
        ?: throw Exception("current webview instance is invalid for $mmid")).nativeUiController