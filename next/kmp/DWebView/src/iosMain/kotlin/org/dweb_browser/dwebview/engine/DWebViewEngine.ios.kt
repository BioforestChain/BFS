package org.dweb_browser.dwebview.engine

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.hostWithPort
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.dweb_browser.core.http.dwebHttpGatewayServer
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.dwebview.DWebViewOptions
import org.dweb_browser.dwebview.IDWebView
import org.dweb_browser.dwebview.WebBeforeUnloadArgs
import org.dweb_browser.dwebview.WebLoadState
import org.dweb_browser.dwebview.base.LoadedUrlCache
import org.dweb_browser.dwebview.closeWatcher.CloseWatcher
import org.dweb_browser.dwebview.closeWatcher.CloseWatcherScriptMessageHandler
import org.dweb_browser.dwebview.messagePort.DWebViewWebMessage
import org.dweb_browser.dwebview.polyfill.DWebViewFaviconMessageHandler
import org.dweb_browser.dwebview.polyfill.DWebViewWebSocketMessageHandler
import org.dweb_browser.dwebview.polyfill.DwebViewPolyfill
import org.dweb_browser.dwebview.polyfill.FaviconPolyfill
import org.dweb_browser.dwebview.polyfill.UserAgentData
import org.dweb_browser.dwebview.proxy.DwebViewProxy
import org.dweb_browser.dwebview.toReadyListener
import org.dweb_browser.helper.Bounds
import org.dweb_browser.helper.JsonLoose
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.mainAsyncExceptionHandler
import org.dweb_browser.helper.platform.ios.DwebHelper
import org.dweb_browser.helper.platform.ios.DwebWKWebView
import org.dweb_browser.helper.toIosUIEdgeInsets
import org.dweb_browser.helper.withMainContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.CoreGraphics.CGRect
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIEdgeInsetsEqualToEdgeInsets
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIEdgeInsetsZero
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKContentWorld
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKPreferences
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled

@OptIn(ExperimentalForeignApi::class)
private val dwebHelper = DwebHelper()

@Suppress("CONFLICTING_OVERLOADS")
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
class DWebViewEngine(
  frame: CValue<CGRect>,
  val remoteMM: MicroModule,
  internal val options: DWebViewOptions,
  configuration: WKWebViewConfiguration,
) : DwebWKWebView(frame, configuration.also {
  /// 设置scheme，这需要在传入WKWebView之前就要运作
  registryDwebHttpUrlSchemeHandler(remoteMM, it)
  registryDwebSchemeHandler(remoteMM, it)
}) {
  val mainScope = CoroutineScope(mainAsyncExceptionHandler + SupervisorJob())
  val ioScope = CoroutineScope(remoteMM.ioAsyncScope.coroutineContext + SupervisorJob())

  val loadStateChangeSignal = Signal<WebLoadState>()
  val onReady by lazy { loadStateChangeSignal.toReadyListener() }
  val beforeUnloadSignal = Signal<WebBeforeUnloadArgs>()
  val loadingProgressSharedFlow = MutableSharedFlow<Float>()
  val closeSignal = SimpleSignal()
  val createWindowSignal = Signal<IDWebView>()

  internal val closeWatcher: CloseWatcher by lazy {
    CloseWatcher(this)
  }

  companion object {
    /**
     * 注册 dweb+http(s)? 的链接拦截，因为IOS不能拦截 `http(s)?:*.dweb`。
     * 所以这里定义了这个特殊的 scheme 来替代 http(s)?:*.dweb
     *
     * PS：IOS17+可以拦截 https:*.dweb。但是这需要依赖与网络技术栈
     * 所以 http(s)?:*.dweb 在 IOS上 反而是一个更加安全的、仅走内存控制的技术，通常用于内部模块使用
     */
    private fun registryDwebHttpUrlSchemeHandler(
      microModule: MicroModule, configuration: WKWebViewConfiguration
    ) {
      val dwebSchemeHandler = DwebHttpURLSchemeHandler(microModule)
      configuration.setURLSchemeHandler(dwebSchemeHandler, "dweb+http")
      configuration.setURLSchemeHandler(dwebSchemeHandler, "dweb+https")
    }

    fun registryDwebSchemeHandler(
      microModule: MicroModule, configuration: WKWebViewConfiguration
    ) {
      val dwebSchemeHandler = DwebURLSchemeHandler(microModule)
      configuration.setURLSchemeHandler(dwebSchemeHandler, "dweb")
    }

  }

  internal val loadedUrlCache = LoadedUrlCache(ioScope)

  suspend fun loadUrl(url: String): String {
    val safeUrl = resolveUrl(url)

    loadedUrlCache.checkLoadedUrl(url) {
      val nsUrl = NSURL(string = safeUrl)
      val nav = loadRequest(NSURLRequest(uRL = nsUrl))
      true
    }
    return safeUrl
  }

  suspend fun resolveUrl(inputUrl: String): String {
    val safeUrl = if (inputUrl.contains(".dweb")) {
      Url(inputUrl).let { url ->
        /// 处理 http(s)?:*.dweb
        if (url.host.endsWith(".dweb")) {
          if (url.protocol == URLProtocol.HTTP || (options.privateNet && url.protocol == URLProtocol.HTTPS)) {
            "dweb+$url"
          } else inputUrl
        } else {
          /// 处理 http://{*.dweb}-{port}.localhost:{gateway-port} ，直接翻译成 dweb+http://{*.dweb}:{port}
          val httpLocalhostGatewaySuffix = dwebHttpGatewayServer.getHttpLocalhostGatewaySuffix()
          val inputHostWithPort = url.hostWithPort
          if (url.protocol == URLProtocol.HTTP && inputHostWithPort.endsWith(
              httpLocalhostGatewaySuffix
            )
          ) {
            "dweb+" + inputUrl.replace(inputHostWithPort, inputHostWithPort.substring(
              0, inputHostWithPort.length - httpLocalhostGatewaySuffix.length
            ).let { dwebHost ->
              val hostInfo = dwebHost.split('-')
              val port = hostInfo.last().toUShortOrNull()
              if (port != null) {
                hostInfo.toMutableList().run {
                  removeLast()
                  joinToString("-")
                } + ":$port"
              } else dwebHost
            })
          } else inputUrl
        }
      }
    } else inputUrl
    return safeUrl
  }

  internal val dwebUIDelegate = DWebUIDelegate(this)
  internal val dwebNavigationDelegate = DWebNavigationDelegate(this)
  internal val dwebUIScrollViewDelegate = DWebUIScrollViewDelegate(this)
  private val estimatedProgressObserver = DWebEstimatedProgressObserver(this)

  init {
    /// 启动代理
    val url = Url(DwebViewProxy.ProxyUrl)
    dwebHelper.setProxyWithConfiguration(
      configuration, url.host, url.port.toUShort()
    )
    /// 测试的时候使用
    this.setInspectable(true)
    setNavigationDelegate(dwebNavigationDelegate)
    setUIDelegate(dwebUIDelegate)
    scrollView.setDelegate(dwebUIScrollViewDelegate)

    val preferences = WKPreferences()
    preferences.javaScriptEnabled = true
    preferences.javaScriptCanOpenWindowsAutomatically = false
    configuration.preferences = preferences
    configuration.userContentController.apply {
//      removeAllScriptMessageHandlers()
//      removeAllScriptMessageHandlersFromContentWorld(DWebViewWebMessage.webMessagePortContentWorld)
//      removeAllUserScripts()
      addScriptMessageHandler(CloseWatcherScriptMessageHandler(this@DWebViewEngine), "closeWatcher")
      addScriptMessageHandler(
        DWebViewWebMessage.WebMessagePortMessageHandler(),
        DWebViewWebMessage.webMessagePortContentWorld,
        "webMessagePort"
      )
      addUserScript(
        WKUserScript(
          DWebViewWebMessage.WebMessagePortPrepareCode,
          WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
          false,
          DWebViewWebMessage.webMessagePortContentWorld
        )
      )
      addScriptMessageHandler(
        scriptMessageHandler = DWebViewFaviconMessageHandler(this@DWebViewEngine),
        contentWorld = FaviconPolyfill.faviconContentWorld,
        name = "favicons"
      )
      addUserScript(
        WKUserScript(
          DwebViewPolyfill.Favicon,
          WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
          true,
          FaviconPolyfill.faviconContentWorld,
        ),
      )

      addUserScript(
        WKUserScript(
          DwebViewPolyfill.WebSocket,
          WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
          false,
        )
      )
      addScriptMessageHandlerWithReply(
        scriptMessageHandlerWithReply = DWebViewWebSocketMessageHandler(this@DWebViewEngine),
        contentWorld = WKContentWorld.pageWorld,
        name = "websocket"
      )
    }

    // 初始化设置 userAgent
    setUA()

    if (options.url.isNotEmpty()) {
      mainScope.launch {
        loadUrl(options.url)
      }
    }

    // 强制透明
    setOpaque(false)
    // 设置默认背景
    addDocumentStartJavaScript(
      """
      const sheet = new CSSStyleSheet();
      sheet.replaceSync(":root { background:#fff;color:#000; } @media (prefers-color-scheme: dark) {:root { background:#333;color:#fff; }}");
      document.adoptedStyleSheets = [sheet];
    """.trimIndent()
    )

    scrollView.contentInsetAdjustmentBehavior =
      UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
    scrollView.insetsLayoutMarginsFromSafeArea = true
    scrollView.bounces = false
  }


  //#region favicon
  suspend fun getFavicon() = withMainContext {
    awaitAsyncJavaScript<String>(
      "getIosIcon()",
      inContentWorld = FaviconPolyfill.faviconContentWorld
    )
  }

  /**
   * 重写setFrame修复虚拟键盘弹出时界面可以滚动的问题
   * 修复方法参考资料：
   * https://github.com/flutter/flutter/issues/40666
   * https://github.com/flutter/plugins/pull/2466/files
   */
  override fun setFrame(frame: CValue<CGRect>) {
    super.setFrame(frame)
    scrollView.contentInset = cValue { UIEdgeInsetsZero };
    if (!UIEdgeInsetsEqualToEdgeInsets(
        scrollView.adjustedContentInset,
        cValue { UIEdgeInsetsZero })
    ) {
      val insetToAdjust = scrollView.adjustedContentInset;
      scrollView.contentInset =
        insetToAdjust.useContents { UIEdgeInsetsMake(-top, -left, -bottom, -right); }
    }
  }

  //#endregion

  fun evalAsyncJavascript(code: String): Deferred<String> {
    val deferred = CompletableDeferred<String>()
    evaluateJavaScript(code) { result, error ->
      if (error == null) {
        deferred.complete(result as String)
      } else {
        deferred.completeExceptionally(Throwable(error.localizedDescription))
      }
    }

    return deferred
  }

  fun <T> evalAsyncJavascript(
    code: String, wkFrameInfo: WKFrameInfo?, wkContentWorld: WKContentWorld
  ): Deferred<T> {
    val deferred = CompletableDeferred<T>()
    evaluateJavaScript(code, wkFrameInfo, wkContentWorld) { result, error ->
      if (error == null) {
        deferred.complete(result as T)
      } else {
        deferred.completeExceptionally(Throwable(error.localizedDescription))
      }
    }

    return deferred
  }

  suspend fun <T> awaitAsyncJavaScript(
    functionBody: String,
    arguments: Map<Any?, *>? = null,
    inFrame: WKFrameInfo? = null,
    inContentWorld: WKContentWorld = WKContentWorld.pageWorld,
    afterEval: (suspend () -> Unit)? = null
  ): T {
    val deferred = CompletableDeferred<T>()
    callAsyncJavaScript(functionBody, arguments, inFrame, inContentWorld) { result, error ->
      if (error == null) {
        deferred.complete(result as T)
      } else {
        deferred.completeExceptionally(Throwable(error.localizedDescription))
      }
    }
    afterEval?.invoke()

    return deferred.await()
  }

  /**
   * 初始化设置 userAgent
   */
  private fun setUA() {
    val versionName =
      NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as String
    val brandList = mutableListOf<IDWebView.UserAgentBrandData>()
    IDWebView.brands.forEach {
      brandList.add(
        IDWebView.UserAgentBrandData(
          it.brand,
          if (it.version.contains(".")) it.version.split(".").first() else it.version
        )
      )
    }
    brandList.add(IDWebView.UserAgentBrandData("DwebBrowser", versionName.split(".").first()))
    addDocumentStartJavaScript(
      """
        ${UserAgentData.polyfillScript}
        if (!navigator.userAgentData) {
          let userAgentData = new NavigatorUAData(navigator, ${JsonLoose.encodeToString(brandList)});
          Object.defineProperty(Navigator.prototype, 'userAgentData', {
            enumerable: true,
            configurable: true,
            get: function getUseAgentData() {
              return userAgentData;
            }
          });
          Object.defineProperty(globalThis, 'NavigatorUAData', {
            enumerable: false,
            configurable: true,
            writable: true,
            value: NavigatorUAData
          });
        }
      """.trimIndent()
    )
  }

  private fun addDocumentStartJavaScript(script: String) {
    configuration.userContentController.addUserScript(
      WKUserScript(
        script,
        WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
        false
      )
    )
  }

  fun evaluateJavascriptSync(script: String) {
    evaluateJavaScript(script) { _, _ -> }
  }


  //#region SafeAreaInsets

  /**
   * css.env(safe-area-inset-*)
   * https://github.com/WebKit/WebKit/blob/a544a2189b62dab2a7b73034a3f298508619c448/Source/WebKit/UIProcess/API/ios/WKWebViewIOS.mm#L794
   */
  var safeArea: Bounds? = null
    set(value) {
      field = value
      when (options.displayCutoutStrategy) {
        DWebViewOptions.DisplayCutoutStrategy.Ignore -> {}
        DWebViewOptions.DisplayCutoutStrategy.Default -> {
          when (value) {
            null -> dwebHelper.disableSafeAreaInsetsWithWebView(this)
            else -> dwebHelper.enableSafeAreaInsetsWithWebView(
              this,
              value.toIosUIEdgeInsets()
            )
          }
        }
      }

    }

  //#endregion

  fun destroy() {
    estimatedProgressObserver.disconnect()
    configuration.userContentController.apply {
      removeAllUserScripts()
      removeAllScriptMessageHandlers()
    }
  }
}