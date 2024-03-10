package org.dweb_browser.dwebview

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
import androidx.webkit.WebSettingsCompat.DARK_STRATEGY_USER_AGENT_DARKENING_ONLY
import androidx.webkit.WebSettingsCompat.FORCE_DARK_AUTO
import androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
import androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.core.module.getAppContext
import org.dweb_browser.dwebview.DWebMessagePort.Companion.into
import org.dweb_browser.dwebview.engine.DWebViewEngine
import org.dweb_browser.dwebview.polyfill.DwebViewPolyfill
import org.dweb_browser.dwebview.proxy.DwebViewProxyOverride
import org.dweb_browser.helper.Bounds
import org.dweb_browser.helper.RememberLazy
import org.dweb_browser.helper.SuspendOnce
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.withMainContext

actual suspend fun IDWebView.Companion.create(
  mm: MicroModule, options: DWebViewOptions
): IDWebView = create(getAppContext(), mm, options)

suspend fun IDWebView.Companion.create(
  /**
   * 一个WebView的上下文
   */
  context: Context,
  /// 这两个参数是用来实现请求拦截与转发的
  remoteMM: MicroModule,
  /**
   * 一些DWebView自定义的参数
   */
  options: DWebViewOptions = DWebViewOptions(),
  /**
   * 该参数的存在，是用来做一些跟交互式界面相关的行为的，交互式界面需要有一个上下文，比如文件选择、权限申请等行为。
   * 我们将这些功能都写到了BaseActivity上，如果没有提供该对象，则相关的功能将会被禁用
   */
  activity: org.dweb_browser.helper.android.BaseActivity? = null
): IDWebView =
  withMainContext {
    DWebView.prepare()
    create(DWebViewEngine(context, remoteMM, options, activity), options.url)
  }

internal fun IDWebView.Companion.create(engine: DWebViewEngine, initUrl: String?) =
  DWebView(engine, initUrl)

class DWebView(internal val engine: DWebViewEngine, initUrl: String? = null) : IDWebView(initUrl) {
  companion object {
    val prepare = SuspendOnce {
      coroutineScope {
        launch(ioAsyncExceptionHandler) {
          DwebViewPolyfill.prepare();
        }
        launch {
          DwebViewProxyOverride.prepare()
        }
      }
    }

    init {
      CoroutineScope(ioAsyncExceptionHandler).launch {
        prepare()
      }
    }
  }

  init {
    engine.remoteMM.onAfterShutdown {
      destroy()
    }
  }

  override val ioScope get() = engine.ioScope
  override suspend fun startLoadUrl(url: String) = withMainContext {
    engine.loadUrl(url)
    url
  }

  override suspend fun resolveUrl(url: String) = engine.resolveUrl(url)
  override suspend fun getOriginalUrl() = withMainContext {
    engine.evaluateSyncJavascriptCode("javascript:window.location.href;")
  }


  override suspend fun getTitle() = withMainContext {
    engine.title ?: ""
  }

  override suspend fun getIcon() = engine.dwebFavicon.href
  override suspend fun destroy() = withMainContext {
    engine.destroy()
  }

  override suspend fun historyCanGoBack() = withMainContext { engine.canGoBack() }

  override suspend fun historyGoBack(): Boolean = withMainContext {
    if (engine.canGoBack()) {
      engine.goBack()
      true
    } else {
      false
    }
  }

  override suspend fun historyCanGoForward() = withMainContext { engine.canGoForward() }

  override suspend fun historyGoForward() = withMainContext {
    if (engine.canGoForward()) {
      engine.goForward()
      true// TODO 能否有goForward钩子？
    } else {
      false
    }
  }

  override val urlStateFlow by lazy {
    generateOnUrlChangeFromLoadedUrlCache(engine.loadedUrlCache)
  }

  @SuppressLint("RequiresFeature")
  override suspend fun createMessageChannel(): IWebMessageChannel = withMainContext {
    DWebMessageChannel(WebViewCompat.createWebMessageChannel(engine))
  }

  @SuppressLint("RequiresFeature")
  override suspend fun postMessage(data: String, ports: List<IWebMessagePort>) = withMainContext {
    WebViewCompat.postWebMessage(
      engine,
      WebMessageCompat(data, ports.map { it.into() }.toTypedArray()),
      Uri.EMPTY
    )
  }

  @SuppressLint("RequiresFeature")
  override suspend fun postMessage(data: ByteArray, ports: List<IWebMessagePort>) =
    withMainContext {
      WebViewCompat.postWebMessage(
        engine,
        WebMessageCompat(data, ports.map { it.into() }.toTypedArray()),
        Uri.EMPTY
      )
    }

  val contentScale = mutableFloatStateOf(1f)
  override suspend fun setContentScale(scale: Float, width: Float, height: Float, density: Float) =
    withMainContext {
      this.contentScale.floatValue = scale
      engine.scaleX = scale
      engine.scaleY = scale
//      engine.setInitialScale((scale * density * 100).toInt())
//      engine.layoutParams = ViewGroup.LayoutParams(
//        (width / scale * density).toInt(), (height / scale * density).toInt()
//      )
    }

  override suspend fun setPrefersColorScheme(colorScheme: WebColorScheme) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
      return
    }
    when (colorScheme) {
      WebColorScheme.Normal -> {
        WebSettingsCompat.setForceDark(engine.settings, FORCE_DARK_AUTO)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
          WebSettingsCompat.setForceDarkStrategy(
            engine.settings, DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
          )
        }
      }

      WebColorScheme.Dark -> {
        WebSettingsCompat.setForceDark(engine.settings, FORCE_DARK_ON)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
          WebSettingsCompat.setForceDarkStrategy(
            engine.settings, DARK_STRATEGY_USER_AGENT_DARKENING_ONLY
          )
        }
      }

      WebColorScheme.Light -> {
        WebSettingsCompat.setForceDark(engine.settings, FORCE_DARK_OFF)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
          WebSettingsCompat.setForceDarkStrategy(
            engine.settings, DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
          )
        }
      }
    }
  }

  override suspend fun setVerticalScrollBarVisible(visible: Boolean) {
    engine.isVerticalScrollBarEnabled = visible
  }

  override suspend fun setHorizontalScrollBarVisible(visible: Boolean) {
    engine.isHorizontalScrollBarEnabled = visible
  }

  override suspend fun evaluateAsyncJavascriptCode(
    script: String, afterEval: suspend () -> Unit
  ): String = engine.evaluateAsyncJavascriptCode(script, afterEval)

  override val onDestroy = engine.onDestroy
  override val onLoadStateChange by lazy { engine.dWebViewClient.loadStateChangeSignal.toListener() }
  override val onReady get() = engine.dWebViewClient.onReady

  override val onBeforeUnload by lazy { engine.dWebChromeClient.beforeUnloadSignal.toListener() }
  override val loadingProgressFlow by lazy { engine.dWebChromeClient.loadingProgressSharedFlow.asSharedFlow() }

  override val closeWatcherLazy = RememberLazy<ICloseWatcher>(engine) {
    engine.closeWatcher
  }
  override val onCreateWindow by lazy { engine.createWindowSignal.toListener() }
  override val onDownloadListener by lazy { engine.dWebDownloadListener.downloadSignal.toListener() }

  override fun setOnScrollChangeListener(onScrollChange: ScrollChangeEvent.() -> Unit) {
    engine.setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
      ScrollChangeEvent(this, scrollX, scrollY).onScrollChange()
    }
  }

  override suspend fun getFavoriteIcon(): ImageBitmap? {
    return this.asAndroidWebView().favicon?.asImageBitmap()
  }

  override suspend fun setSafeAreaInset(bounds: Bounds) = withMainContext {
    engine.safeArea = bounds
  }
}

//#region 一些针对平台的接口
fun IDWebView.asAndroidWebView(): DWebViewEngine {
  require(this is DWebView)
  return engine
}
//#endregion
