package org.dweb_browser.dwebview

import com.teamdev.jxbrowser.js.JsObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.dwebview.engine.DWebViewEngine
import org.dweb_browser.dwebview.engine.window
import org.dweb_browser.dwebview.messagePort.DWebMessageChannel
import org.dweb_browser.dwebview.messagePort.DWebMessagePort
import org.dweb_browser.dwebview.polyfill.DwebViewPolyfill
import org.dweb_browser.dwebview.proxy.DwebViewProxy
import org.dweb_browser.helper.Bounds
import org.dweb_browser.helper.RememberLazy
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SuspendOnce
import org.dweb_browser.helper.ioAsyncExceptionHandler

actual suspend fun IDWebView.Companion.create(
  mm: MicroModule, options: DWebViewOptions
): IDWebView {
  DWebView.prepare()
  return DWebView(DWebViewEngine(mm, options))
}

class DWebView(
  val viewEngine: DWebViewEngine, initUrl: String? = null
) : IDWebView(initUrl ?: viewEngine.options.url) {
  companion object {
    val prepare = SuspendOnce {
      coroutineScope {
        launch(ioAsyncExceptionHandler) {
          DwebViewPolyfill.prepare();
        }
        DwebViewProxy.prepare()
      }
    }

    init {
      CoroutineScope(ioAsyncExceptionHandler).launch {
        prepare()
      }
    }
  }

  override val ioScope: CoroutineScope
    get() = viewEngine.ioScope

  override suspend fun startLoadUrl(url: String): String {
    viewEngine.loadUrl(url)
    return viewEngine.getOriginalUrl()
  }

  override suspend fun resolveUrl(url: String): String = viewEngine.resolveUrl(url)

  override suspend fun getOriginalUrl(): String {
    return viewEngine.getOriginalUrl()
  }

  override suspend fun getTitle(): String {
    return viewEngine.mainFrame.executeJavaScript<String>("document.title") ?: viewEngine.getTitle()
  }

  override suspend fun getIcon() = viewEngine.dwebFavicon.href

  override suspend fun destroy() {
    viewEngine.destroy()
  }

  override suspend fun historyCanGoBack(): Boolean = viewEngine.canGoBack()

  override suspend fun historyGoBack(): Boolean = viewEngine.goBack()

  override suspend fun historyCanGoForward(): Boolean = viewEngine.canGoForward()

  override suspend fun historyGoForward(): Boolean = viewEngine.goForward()

  override val urlStateFlow by lazy { generateOnUrlChangeFromLoadedUrlCache(viewEngine.loadedUrlCache) }

  override suspend fun createMessageChannel(): IWebMessageChannel {
    val channel = viewEngine.mainFrame.executeJavaScript<JsObject>("new MessageChannel()")
    if (channel != null) {
      val port1 = DWebMessagePort(channel.property<JsObject>("port1").get(), this)
      val port2 = DWebMessagePort(channel.property<JsObject>("port2").get(), this)

      return DWebMessageChannel(port1, port2)
    }

    throw NoSuchFieldException("MessageChannel create failed!")
  }

  override suspend fun postMessage(data: String, ports: List<IWebMessagePort>) {
    viewEngine.mainFrame
    ports.first().onMessage.signal.emit(DWebMessage.DWebMessageString(data, ports))
  }

  override suspend fun postMessage(data: ByteArray, ports: List<IWebMessagePort>) {
    ports.first().onMessage.signal.emit(DWebMessage.DWebMessageBytes(data, ports))
  }

  override suspend fun setContentScale(scale: Float, width: Float, height: Float, density: Float) {
    TODO("Not yet implemented")
  }

  override suspend fun setPrefersColorScheme(colorScheme: WebColorScheme) {
    TODO("Not yet implemented")
  }

  override suspend fun setVerticalScrollBarVisible(visible: Boolean) {
    TODO("Not yet implemented")
  }

  override suspend fun setHorizontalScrollBarVisible(visible: Boolean) {
    TODO("Not yet implemented")
  }

  override suspend fun evaluateAsyncJavascriptCode(
    script: String, afterEval: suspend () -> Unit
  ): String {
    return viewEngine.evaluateAsyncJavascriptCode(script, afterEval)
  }

  override suspend fun setSafeAreaInset(bounds: Bounds) {
    TODO("Not yet implemented")
  }

  private val _engineLazy = RememberLazy(viewEngine) { viewEngine }

  override val onDestroy: Signal.Listener<Unit>
    get() = TODO("Not yet implemented")
  override val onLoadStateChange: Signal.Listener<WebLoadState>
      by _engineLazy.then { viewEngine.loadStateChangeSignal.toListener() }
  override val onReady: Signal.Listener<String>
    get() = TODO("Not yet implemented")
  override val onBeforeUnload: Signal.Listener<WebBeforeUnloadArgs>
    get() = TODO("Not yet implemented")
  override val loadingProgressFlow: SharedFlow<Float>
    get() = TODO("Not yet implemented")
  override val closeWatcherLazy: RememberLazy<ICloseWatcher>
    get() = TODO("Not yet implemented")
  override val onCreateWindow: Signal.Listener<IDWebView>
    get() = TODO("Not yet implemented")
  override val onDownloadListener: Signal.Listener<WebDownloadArgs>
    get() = TODO("Not yet implemented")

  override fun setOnTouchListener(onTouch: (IDWebView, MotionEventAction) -> Boolean) {
    TODO("Not yet implemented")
  }

  override fun setOnScrollChangeListener(onScrollChange: ScrollChangeEvent.() -> Unit) {
    viewEngine.injectJsAction {
      println("QAQ addEventListener scroll")
      window().addEventListener("scroll") {
        println("QAQ dispatchEvent scroll")
        ScrollChangeEvent(
          this@DWebView,
          executeJavaScript<Double>("(document.scrollingElement?.scrollLeft||0)*devicePixelRatio")!!.toInt(),
          executeJavaScript<Double>("(document.scrollingElement?.scrollTop||0)*devicePixelRatio")!!.toInt()
        ).onScrollChange()
      }
    }
  }

  override suspend fun getFavoriteIcon() = viewEngine.getFavoriteIcon()
}