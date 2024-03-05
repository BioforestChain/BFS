package org.dweb_browser.dwebview.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.WindowInsets
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.UserAgentMetadata.BrandVersion
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.dwebview.DWebViewOptions
import org.dweb_browser.dwebview.DWebViewOptions.DisplayCutoutStrategy.Default
import org.dweb_browser.dwebview.DWebViewOptions.DisplayCutoutStrategy.Ignore
import org.dweb_browser.dwebview.IDWebView
import org.dweb_browser.dwebview.base.LoadedUrlCache
import org.dweb_browser.dwebview.closeWatcher.CloseWatcher
import org.dweb_browser.dwebview.debugDWebView
import org.dweb_browser.dwebview.polyfill.UserAgentData
import org.dweb_browser.dwebview.polyfill.setupKeyboardPolyfill
import org.dweb_browser.helper.Bounds
import org.dweb_browser.helper.JsonLoose
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.mainAsyncExceptionHandler
import org.dweb_browser.helper.toAndroidRect
import org.dweb_browser.helper.withMainContext
import org.dweb_browser.sys.device.DeviceManage


/**
 * DWebView ,将 WebView 与 dweb 的 dwebHttpServer 设计进行兼容性绑定的模块
 * 该对象继承于WebView，所以需要在主线程去初始化它
 *
 * dwebHttpServer 底层提供了三个概念：
 * host/internal_origin/public_origin
 *
 * 其中 public_origin 是指标准的 http 协议链接，可以在标准的网络中被访问（包括本机的其它应用、以及本机所处的局域网），它的本质就是一个网关，所有的本机请求都会由它代理分发。
 * 而 host ，就是所谓网关分发的判定关键
 * 因此 internal_origin 是一个特殊的 http 链接协议，它非标准，只能在本应用（Dweb Browser）被特定的方法翻译后才能正常访问
 * 这个"翻译"方法的背后，本质上就是 host 这个值在其关键作用：
 * 1. 将 host 值放在 url 的 query.X-Dweb-Host。该方法最直接，基于兼容任何环境很高，缺点是用户构建链接的时候，这部分的信息很容易被干扰没掉
 * 2. 将 host 值放在 request 的 header 中 ("X-Dweb-Host: $HOST")。该方法有一定环境要求，需要确保自定义头部能够被设置并传递，缺点在于它很难被广泛地使用，因为自定义 header 就意味着必须基于命令编程而不是声明式的语句
 * 3. 将 host 值放在 url 的 username 中: uri.userInfo(HOST.encodeURI())。该方法相比第一个方法，优点在于不容易被干扰，而且属于声明式语句，可以对链接所处环境做影响。缺点是它属于身份验证的标准，有很多安全性限制，在一些老的API接口中会有奇怪的行为，比如现代浏览器中的iframe是不允许这种url的。
 * 4. 将 host 值 header["Host"] 中: uri.header("Host", HOST)。该方法对环境要求最大，通常用于可编程能力较高环境中，比如 electron 这种浏览器中对 https/http 域名做完全的拦截，或者说 nodejs 这类完全可空的后端环境中对 httpRequest 做完全的自定义构建。这种方案是最标准的存在，但也是最难适配到各个环境的存在。
 *
 * 以上四种方式，优先级依次降低，都可以将 dweb-host 携带给 public_origin 背后的服务让其进行网关路由
 *
 * 再有关于 internal_origin，是一种非标准的概念，它的存在目的是尽可能不要将请求走到 public_origin，因为这会导致我们的数据走了网卡，从而造成应用内数据被窃取，甚至是会被别人使用 http 请求发起恶意攻击。
 * 因此，我们就要在不同平台环境中的，尽可能让这个 internal_origin 标准能广泛地使用。
 * 具体说，在 Dweb-Browser 这个产品中，最大的问题就是浏览器的拦截问题。
 *
 * 当下，Android 想要拦截 POST 等带 body 的请求，必须用 service-worker 来做到，但是 service-worker 本身直接与原生交互，所以在 service-worker 层返回会引入新的问题，最终的结果就是导致性能下降等。同时 Android 的拦截还有一些限制，比如不允许 300～399 的响应等等。
 * IOS 虽然能拦截 body，但是不能像Android一样去拦截 http/https 链接
 * Electron 25 之后，已经能轻松拦截并构建所有的 http/https 请求的响应了
 *
 * 因此 internal_origin 的形态就千奇百怪。
 * 在 Electron 中的开发版使用的是: http://app.gaubee.com.dweb-443.localhost:22600/index.html
 *    未来正式环境版会使用完整版的形态: https://app.gaubee.com.dweb:443/index.html
 * 在 Android 中也是: https://app.gaubee.com.dweb:443/index.html，但只能处理 GET/200|400|500 这类简单的请求，其它情况下还是得使用 public_origin
 * 在 IOS 中使用的是 app.gaubee.com.dweb+443:/index.html 这样的链接
 *
 * 总而言之，如果你的 WebApp 需要很标准复杂的 http 协议的支持，那么只能选择完全使用 public_origin，它走的是标准的网络协议。
 * 否则，可以像 Plaoc 一样，专注于传统前后端分离的 WebApp，那么可以尽可能采用 internal_origin。
 *
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor", "RequiresFeature", "DiscouragedPrivateApi")
class DWebViewEngine internal constructor(
  /**
   * 一个WebView的上下文
   */
  context: Context,
  /// 这两个参数是用来实现请求拦截与转发的
  internal val remoteMM: MicroModule,
  /**
   * 一些DWebView自定义的参数
   */
  val options: DWebViewOptions,
  /**
   * 该参数的存在，是用来做一些跟交互式界面相关的行为的，交互式界面需要有一个上下文，比如文件选择、权限申请等行为。
   * 我们将这些功能都写到了BaseActivity上，如果没有提供该对象，则相关的功能将会被禁用
   */
  var activity: org.dweb_browser.helper.android.BaseActivity? = null
) : WebView(context) {


  init {
    if (activity == null && context is org.dweb_browser.helper.android.BaseActivity) {
      activity = context
    }
  }

  internal val mainScope = CoroutineScope(mainAsyncExceptionHandler + SupervisorJob())
  internal val ioScope = CoroutineScope(remoteMM.ioAsyncScope.coroutineContext + SupervisorJob())

  suspend fun waitReady() {
    dWebViewClient.onReady.awaitOnce()
  }

  private val evaluator = WebViewEvaluator(this, ioScope)
  suspend fun getUrlInMain() = withMainContext { url }


  private val supportDocumentStartScript by lazy {
    WebViewFeature.isFeatureSupported(
      WebViewFeature.DOCUMENT_START_SCRIPT
    )
  }

  private val documentStartJsList by lazy {
    mutableListOf<String>().also { scriptList ->
      addWebViewClient(object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
          if (url?.startsWith("https://") == true) {
            for (script in scriptList) {
              evaluateJavascript(script, null)
            }
          }
        }
      })
    }
  }

  fun addDocumentStartJavaScript(script: String) {
    if (supportDocumentStartScript) {
      WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*"))
    } else {
      documentStartJsList += script
    }
  }

  /**
   * 初始化设置 userAgent
   */
  private fun setUA() {
    val versionName = DeviceManage.deviceAppVersion()
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
      val oldUserAgent = WebSettingsCompat.getUserAgentMetadata(settings)
      val brandList = mutableListOf<BrandVersion>()

      IDWebView.brands.forEach {
        brandList.add(
          BrandVersion.Builder().setBrand(it.brand).setFullVersion(it.version).setMajorVersion(
            if (it.version.contains(".")) it.version.split(".").first() else it.version
          ).build()
        )
      }
      brandList.add(
        BrandVersion.Builder().setBrand("DwebBrowser").setFullVersion(versionName)
          .setMajorVersion(versionName.split(".").first()).build()
      )

      val userAgent = UserAgentMetadata.Builder(oldUserAgent).setBrandVersionList(
        oldUserAgent.brandVersionList + brandList
      ).build()
      WebSettingsCompat.setUserAgentMetadata(settings, userAgent)
    } else {
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
        let userAgentData = ""
        if (!navigator.userAgentData) {
         userAgentData  = new NavigatorUAData(navigator, ${
          JsonLoose.encodeToJsonElement(
            brandList
          )
        });
        } else {
          userAgentData =
           new NavigatorUAData(navigator, 
           navigator.userAgentData.brands.concat(
           ${
          JsonLoose.encodeToJsonElement(
            brandList
          )
        }));
        }
        Object.defineProperty(Navigator.prototype, 'userAgentData', {
          enumerable: true,
          configurable: true,
          get: function getUseAgentData() {
            return userAgentData;
          }
        });
        Object.defineProperty(window, 'NavigatorUAData', {
          enumerable: false,
          configurable: true,
          writable: true,
          value: NavigatorUAData
        });
      """.trimIndent()
      )
    }
  }

  internal val dWebViewClient = DWebViewClient(this).also {
    it.addWebViewClient(DWebOverwriteRequest(this@DWebViewEngine))
  }

  fun addWebViewClient(client: WebViewClient): () -> Unit {
    dWebViewClient.addWebViewClient(client)
    return {
      dWebViewClient.removeWebViewClient(client)
    }
  }

  fun addWebChromeClient(client: WebChromeClient): () -> Unit {
    dWebChromeClient.addWebChromeClient(client)
    return {
      dWebChromeClient.removeWebChromeClient(client)
    }
  }

  override fun setWebViewClient(client: WebViewClient) {
    if (client != dWebViewClient) {
      dWebViewClient.addWebViewClient(client)
    }
  }

  internal val dWebChromeClient = DWebChromeClient(this).also {
    it.addWebChromeClient(DWebFileChooser(remoteMM, ioScope, activity))
    it.addWebChromeClient(DWebPermissionRequest(remoteMM, ioScope))
  }

  val onCloseWindow = dWebChromeClient.closeSignal.toListener()
  internal val loadedUrlCache = LoadedUrlCache(ioScope)

  override fun setWebChromeClient(client: WebChromeClient?) {
    if (client == null) {
      return
    }
    dWebChromeClient.addWebChromeClient(client)
  }

  internal val dWebDownloadListener = DWebDownloadListener(this)

  fun addDownloadListener(listener: DownloadListener): () -> Unit {
    dWebDownloadListener.addDownloadListener(listener)
    return {
      dWebDownloadListener.removeDownloadListener(listener)
    }
  }

  override fun setDownloadListener(listener: DownloadListener?) {
    listener?.let {
      dWebDownloadListener.addDownloadListener(listener)
    }
  }

  init {
    debugDWebView("INIT", options)

    layoutParams = LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
    )
    setUA()
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.safeBrowsingEnabled = true
    settings.loadWithOverviewMode = true
    settings.loadsImagesAutomatically = true
    settings.setSupportMultipleWindows(true)
    settings.allowFileAccess = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.allowContentAccess = true
    settings.mediaPlaybackRequiresUserGesture = false
    setLayerType(LAYER_TYPE_HARDWARE, null) // 增加硬件加速，避免滑动时画面出现撕裂

    setupKeyboardPolyfill(this)

    super.setWebViewClient(dWebViewClient)
    super.setWebChromeClient(dWebChromeClient)
    super.setDownloadListener(dWebDownloadListener)

    if (options.url.isNotEmpty()) {
      /// 开始加载
      debugDWebView("loadInitUrl", options.url)
      loadUrl(options.url)
    }
    options.tag?.also {
      this.id = it
    }
  }

  /**
   * 避免 com.google.accompanist.web 在切换 Compose 上下文的时候重复加载同样的URL
   */
  override fun loadUrl(url: String) {
    val safeUrl = resolveUrl(url)
    loadedUrlCache.checkLoadedUrl(safeUrl) {
      super.loadUrl(url)
      true
    }
  }

  override fun loadUrl(url: String, additionalHttpHeaders: MutableMap<String, String>) {
    val safeUrl = resolveUrl(url)
    loadedUrlCache.checkLoadedUrl(safeUrl, additionalHttpHeaders) {
      super.loadUrl(safeUrl, additionalHttpHeaders)
      true
    }
  }

  fun resolveUrl(url: String): String {
    return url
  }


  /**
   * 执行同步JS代码
   */
  suspend fun evaluateSyncJavascriptCode(script: String) =
    evaluator.evaluateSyncJavascriptCode(script)

  fun evaluateJavascriptSync(script: String) {
    evaluateJavascript(script) {}
  }

  /**
   * 执行异步JS代码，需要传入一个表达式
   */
  suspend fun evaluateAsyncJavascriptCode(script: String, afterEval: suspend () -> Unit = {}) =
    withMainContext {
      evaluator.evaluateAsyncJavascriptCode(
        script, afterEval
      )
    }

  var isDestroyed = false
  private var _destroySignal = SimpleSignal();
  val onDestroy = _destroySignal.toListener()
  override fun destroy() {
    if (isDestroyed) {
      return
    }
    isDestroyed = true
    debugDWebView("DESTROY")
    if (!isAttachedToWindow) {
      super.onDetachedFromWindow()
    }
    super.destroy()
    ioScope.launch {
      _destroySignal.emitAndClear(Unit)
      ioScope.cancel()
    }
  }

  private var isAttachedToWindow = false

  override fun onDetachedFromWindow() {
    if (options.detachedStrategy == DWebViewOptions.DetachedStrategy.Default) {
      isAttachedToWindow = true
      super.onDetachedFromWindow()
    }
  }

  override fun onAttachedToWindow() {
    ioScope.launch {
      attachedStateFlow.emit(true)
    }
    super.onAttachedToWindow()
    isAttachedToWindow = false
  }

  val attachedStateFlow = MutableStateFlow<Boolean>(false);
  val closeWatcher = CloseWatcher(this)

  internal class BeforeCreateWindow(
    val dwebView: DWebViewEngine,
    val url: String,
    val isUserGesture: Boolean,
    val isDialog: Boolean,
  ) {
    var isConsumed = false
      private set

    fun consume() {
      isConsumed = true
    }
  }

  internal val beforeCreateWindow by lazy { Signal<BeforeCreateWindow>() }
  val createWindowSignal = Signal<IDWebView>()

  private val setDisplayCutoutSafeArea by lazy {
    val webView = this
    val field = WebView::class.java.getDeclaredField("mProvider");
    field.isAccessible = true;
    if (field.type.toString() != "interface android.webkit.WebViewProvider") return@lazy null
    val mProvider = field.get(webView);
    for (field1 in mProvider.javaClass.fields.iterator()) {
      if (field1.type.toString() == "class org.chromium.android_webview.AwContents") {
        val awContents = field1.get(mProvider)
        for (field2 in awContents.javaClass.fields.iterator()) {
          if (field2.type.toString() == "interface org.chromium.content_public.browser.WebContents") {
            val webContents = field2.get(awContents)
            for (method3 in webContents.javaClass.methods.iterator()) {
              val meta =
                "(${method3.parameterTypes.joinToString(", ")})->${method3.returnType}"
              if (meta == "(class android.graphics.Rect)->void") {
                println("SafeArea found setDisplayCutoutSafeArea=${method3}")
                method3.isAccessible = true
                return@lazy { rect: Rect ->
                  println("SafeArea run setDisplayCutoutSafeArea($rect)")
                  method3.invoke(webContents, rect)
                  Unit
                }
              }
            }
          }
        }
      }
    }
    null
  }
  var safeArea = Bounds.Zero
    set(value) {
      field = value
      setDisplayCutoutSafeArea?.invoke(value.toAndroidRect())
    }

  override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
    return when (options.displayCutoutStrategy) {
      Default -> super.onApplyWindowInsets(insets)
      Ignore -> {
        val windowInsetsCompat =
          WindowInsetsCompat.Builder(WindowInsetsCompat.toWindowInsetsCompat(insets, this)).run {
            setDisplayCutout(DisplayCutoutCompat(safeArea.toAndroidRect(), null))
            build()
          }
        windowInsetsCompat.toWindowInsets() ?: super.onApplyWindowInsets(insets)
      }
    }
  }
}