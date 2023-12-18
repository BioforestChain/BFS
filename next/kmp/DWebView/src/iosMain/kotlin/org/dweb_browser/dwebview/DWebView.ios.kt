package org.dweb_browser.dwebview

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asSharedFlow
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.dwebview.engine.DWebViewEngine
import org.dweb_browser.dwebview.messagePort.DWebMessageChannel
import org.dweb_browser.dwebview.messagePort.DWebMessagePort
import org.dweb_browser.dwebview.messagePort.DWebViewWebMessage
import org.dweb_browser.helper.Bounds
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.WARNING
import org.dweb_browser.helper.platform.setScale
import org.dweb_browser.helper.toUtf8
import org.dweb_browser.helper.trueAlso
import org.dweb_browser.helper.withMainContext
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSArray
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.UIUserInterfaceStyle
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import kotlin.native.runtime.NativeRuntimeApi

@OptIn(ExperimentalForeignApi::class)
actual suspend fun IDWebView.Companion.create(
  mm: MicroModule,
  options: DWebViewOptions
): IDWebView =
  create(
    CGRectMake(0.0, 0.0, 100.0, 100.0),
    mm,
    options,
    withMainContext { WKWebViewConfiguration() })

@OptIn(ExperimentalForeignApi::class)
suspend fun IDWebView.Companion.create(
  frame: CValue<CGRect>,
  remoteMM: MicroModule,
  options: DWebViewOptions = DWebViewOptions(),
  configuration: WKWebViewConfiguration,
) = withMainContext {
  DWebViewEngine.prepare()
  create(DWebViewEngine(frame, remoteMM, options, configuration), options.url)
}

@OptIn(ExperimentalForeignApi::class)
internal fun IDWebView.Companion.create(
  engine: DWebViewEngine,
  initUrl: String? = null,
) = DWebView(engine, initUrl)

class DWebView(
  engine: DWebViewEngine,
  initUrl: String? = null
) : IDWebView(initUrl ?: engine.options.url) {
  init {
    engine.remoteMM.onAfterShutdown {
      destroy()
    }
  }

  private var _engine: DWebViewEngine? = engine
  internal val engine get() = _engine ?: throw NullPointerException("dwebview already been destroy")
  override val scope get() = engine.ioScope

  override suspend fun startLoadUrl(url: String) = withMainContext {
    engine.loadUrl(url)
  }

  override suspend fun startGoBack(): Boolean = withMainContext {
    engine.canGoBack.trueAlso {
      engine.goBack()
    }
  }

  override suspend fun resolveUrl(url: String) = engine.resolveUrl(url)


  override suspend fun getTitle(): String {
    return engine.title ?: ""
  }


  override suspend fun getOriginalUrl() = withMainContext {
    evaluateAsyncJavascriptCode("javascript:window.location.href;")
  }

  override suspend fun getIcon() = engine.getFavicon()


  private var _destroyed = false
  private var _destroySignal = SimpleSignal();

  override val onDestroy by lazy { _destroySignal.toListener() }
  override val onLoadStateChange by lazy { engine.loadStateChangeSignal.toListener() }
  override val onReady get() = engine.onReady
  override val onBeforeUnload by lazy { engine.beforeUnloadSignal.toListener() }
  override val loadingProgressFlow by lazy { engine.loadingProgressSharedFlow.asSharedFlow() }
  override val closeWatcher: ICloseWatcher
    get() = engine.closeWatcher
  override val onCreateWindow by lazy { engine.createWindowSignal.toListener() }

  @OptIn(NativeRuntimeApi::class)
  override suspend fun destroy() {
    if (_destroyed) {
      return
    }
    _destroyed = true
    debugDWebView("DESTROY")
//    loadUrl("about:blank", true)
    _destroySignal.emitAndClear(Unit)
    withMainContext {
      engine.destroy()
      engine.navigationDelegate = null
      engine.removeFromSuperview()
      engine.webViewWebContentProcessDidTerminate(webView = engine)
      engine.mainScope.cancel(null)
      engine.ioScope.cancel(null)
      _engine = null
      kotlin.native.runtime.GC.collect()
    }
  }

  override suspend fun canGoBack() = withMainContext { engine.canGoBack }

  override suspend fun canGoForward() = withMainContext { engine.canGoForward }

//  override suspend fun goBack() = withMainContext {
//    engine.canGoBack.trueAlso {
//      engine.goBack()
//    }
//  }

  override suspend fun goForward() = withMainContext {
    engine.canGoForward.trueAlso {
      engine.goForward()
    }
  }

  override suspend fun createMessageChannel() = withMainContext {
    val deferred = engine.evalAsyncJavascript<NSArray>(
      "nativeCreateMessageChannel()", null,
      DWebViewWebMessage.webMessagePortContentWorld
    )
    val ports_id = deferred.await()
    val port1_id = ports_id.objectAtIndex(0u) as Double
    val port2_id = ports_id.objectAtIndex(1u) as Double

    val port1 = DWebMessagePort(port1_id.toInt(), this)
    val port2 = DWebMessagePort(port2_id.toInt(), this)

    DWebMessageChannel(port1, port2)
  }

  @OptIn(ExperimentalForeignApi::class)
  override suspend fun setContentScale(scale: Float, width: Float, height: Float, density: Float) =
    engine.setScale(scale);

  override suspend fun setPrefersColorScheme(colorScheme: WebColorScheme) {
    engine.overrideUserInterfaceStyle = when (colorScheme) {
      WebColorScheme.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
      WebColorScheme.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
      WebColorScheme.Normal -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
    }
  }

  override suspend fun setVerticalScrollBarVisible(visible: Boolean) = withMainContext {
    engine.scrollView.showsVerticalScrollIndicator = visible
  }

  override suspend fun setHorizontalScrollBarVisible(visible: Boolean) = withMainContext {
    engine.scrollView.showsHorizontalScrollIndicator = visible
  }

  override suspend fun evaluateAsyncJavascriptCode(
    script: String,
    afterEval: suspend () -> Unit
  ) = withMainContext {
    engine.awaitAsyncJavaScript<String>(
      "return JSON.stringify(await($script))??'undefined'",
      afterEval = afterEval
    )
  }

  @OptIn(BetaInteropApi::class)
  override suspend fun postMessage(data: String, ports: List<IWebMessagePort>) {
    val portIdList = ports.map {
      require(it is DWebMessagePort)
      it.portId
    }
    withMainContext {
      val arguments = mutableMapOf<NSString, NSObject>().apply {
        put(NSString.create(string = "data"), NSString.create(string = data))
        put(NSString.create(string = "ports"), NSArray.create(portIdList))
      }
      engine.awaitAsyncJavaScript<Unit>(
        "nativeWindowPostMessage(data,ports)",
        arguments.toMap(),
        null,
        DWebViewWebMessage.webMessagePortContentWorld
      )
    }
  }

  @OptIn(BetaInteropApi::class)
  override suspend fun postMessage(data: ByteArray, ports: List<IWebMessagePort>) {
    val portIdList = ports.map {
      require(it is DWebMessagePort)
      it.portId
    }
    withMainContext {
      val arguments = mutableMapOf<NSString, NSObject>().apply {
        put(NSString.create(string = "data"), NSString.create(string = data.toUtf8()))
        put(NSString.create(string = "ports"), NSArray.create(portIdList))
      }
      engine.awaitAsyncJavaScript<Unit>(
        "nativeWindowPostMessage(data,ports)",
        arguments.toMap(),
        null,
        DWebViewWebMessage.webMessagePortContentWorld
      )
    }
  }

  override fun setOnTouchListener(onTouch: (IDWebView, MotionEventAction) -> Boolean) {
    WARNING("Not yet implemented setOnTouchListener")
  }

  override fun setOnScrollChangeListener(onScrollChange: (IDWebView, Int, Int, Int, Int) -> Unit) {
    WARNING("Not yet implemented setOnScrollChangeListener")
  }

  override suspend fun getFavoriteIcon(): ImageBitmap? = withMainContext {
    WARNING("Not yet implemented")
    null
  }

  override suspend fun setSafeAreaInset(bounds: Bounds) {
    engine.safeArea = bounds
  }
}

fun IDWebView.asIosWebView(): DWebViewEngine {
  require(this is DWebView)
  return engine
}