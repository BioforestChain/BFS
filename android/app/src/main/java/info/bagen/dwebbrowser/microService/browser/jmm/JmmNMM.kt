package info.bagen.dwebbrowser.microService.browser.jmm

import android.content.Context
import info.bagen.dwebbrowser.App
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.dweb_browser.browserUI.download.isGreaterThan
import org.dweb_browser.browserUI.microService.browser.link.WebLinkMicroModule
import org.dweb_browser.helper.APP_DIR_TYPE
import org.dweb_browser.helper.Debugger
import org.dweb_browser.helper.FilesUtil
import org.dweb_browser.helper.ImageResource
import org.dweb_browser.helper.resolvePath
import org.dweb_browser.helper.toJsonElement
import org.dweb_browser.microservice.core.AndroidNativeMicroModule
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.DwebResult
import org.dweb_browser.microservice.help.types.IMicroModuleManifest
import org.dweb_browser.microservice.help.types.JmmAppInstallManifest
import org.dweb_browser.microservice.help.types.MICRO_MODULE_CATEGORY
import org.dweb_browser.microservice.help.types.MMID
import org.dweb_browser.microservice.http.PureResponse
import org.dweb_browser.microservice.http.bind
import org.dweb_browser.microservice.http.bindDwebDeeplink
import org.dweb_browser.microservice.ipc.Ipc
import org.dweb_browser.microservice.sys.dns.RespondLocalFileContext.Companion.respondLocalFile
import org.dweb_browser.microservice.sys.dns.nativeFetch
import org.dweb_browser.microservice.sys.dns.nativeFetchAdaptersManager
import org.dweb_browser.microservice.sys.dns.returnAndroidFile
import org.dweb_browser.microservice.sys.download.db.AppType
import org.dweb_browser.microservice.sys.download.db.DeskAppInfo
import org.dweb_browser.microservice.sys.download.db.DownloadDBStore
import org.dweb_browser.window.core.WindowState
import org.dweb_browser.window.core.constant.WindowConstants
import org.dweb_browser.window.core.constant.WindowMode
import org.dweb_browser.window.core.createWindowAdapterManager
import org.dweb_browser.window.core.helper.setFromManifest
import java.io.File

val debugJMM = Debugger("JMM")

class JmmNMM(val context: Context) :
  AndroidNativeMicroModule("jmm.browser.dweb", "Js MicroModule Management") {
  init {
    short_name = "JMM";
    dweb_deeplinks = listOf("dweb://install")
    categories = listOf(MICRO_MODULE_CATEGORY.Service, MICRO_MODULE_CATEGORY.Hub_Service);
    icons = listOf(
      ImageResource(
        src = "file:///sys/icons/$mmid.svg", type = "image/svg+xml", purpose = "monochrome"
      )
    )

    /// 提供JsMicroModule的文件适配器
    /// 这个适配器不需要跟着bootstrap声明周期，只要存在JmmNMM模块，就能生效
    nativeFetchAdaptersManager.append { fromMM, request ->
      return@append request.respondLocalFile {
        if (filePath.startsWith("/usr/")) {
          debugJMM("UsrFile", "$fromMM => ${request.href}")
          returnAndroidFile(
            getAppContext().dataDir.absolutePath + File.separator + APP_DIR_TYPE.SystemApp.rootName + File.separator + fromMM.mmid,
            filePath
          )
        } else returnNext()
      }
    }
  }

  private var jmmController: JmmController? = null

  fun getApps(mmid: MMID): IMicroModuleManifest? {
    return bootstrapContext.dns.query(mmid)
  }

  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    installJmmApps()

    this.onAfterShutdown {
      jmmController = null
    }

    val routeInstallHandler = definePureResponse {
      val metadataUrl = request.query("url") ?: JmmStore.getMetadataUrl(ipc.remote.mmid)
      if (metadataUrl == "") {
        return@definePureResponse PureResponse(HttpStatusCode.ExpectationFailed).body("You need to pass metadataUrl！")
      }
      val response = nativeFetch(metadataUrl)
      if (response.isOk()) {
        try {
          val jmmAppInstallManifest = response.json<JmmAppInstallManifest>()
          // 保存下载链接
          debugJMM("save->", "$metadataUrl ${jmmAppInstallManifest.id}")
          JmmStore.setMetadataUrl(jmmAppInstallManifest.id, metadataUrl)
          val url = Url(metadataUrl)
          // 根据 jmmMetadata 打开一个应用信息的界面，用户阅读界面信息后，可以点击"安装"
          installJsMicroModule(jmmAppInstallManifest, ipc, url)
          PureResponse(HttpStatusCode.OK)
        } catch (e: Throwable) {
          debugJMM("install", "fail -> ${e.message}")
          PureResponse(HttpStatusCode.ExpectationFailed).body(e.stackTraceToString())
        }
      } else {
        PureResponse(HttpStatusCode.ExpectationFailed).body("invalid status code: ${response.status}")
      }
    }
    routes(
      // 安装
      "install" bindDwebDeeplink routeInstallHandler,
      "/install" bind HttpMethod.Get to routeInstallHandler,
      "/uninstall" bind HttpMethod.Get to defineBooleanResponse {
        val mmid = request.queryOrFail("app_id")
        debugJMM("uninstall", mmid)
        jmmMetadataUninstall(mmid)
        true
      },
      //检查是否有新版本
      "/check" bind HttpMethod.Get to defineJsonResponse {
        val metadataUrl = JmmStore.getMetadataUrl(ipc.remote.mmid)
        val response = nativeFetch(metadataUrl)
        debugJMM("check-> ${ipc.remote.mmid}", " $metadataUrl ${response.isOk()}")
        if (!response.isOk()) {
          return@defineJsonResponse DwebResult(false, "network anomaly！").toJsonElement()
        }
        val jmmAppInstallManifest = response.json<JmmAppInstallManifest>()
        val needUpdate = jmmAppInstallManifest.version.isGreaterThan(ipc.remote.version)
        println("needUpdate=>$needUpdate  ${ipc.remote.version}  ${jmmAppInstallManifest.version}")
        if (needUpdate) {
          return@defineJsonResponse DwebResult(true, "Need update").toJsonElement()
        }
        return@defineJsonResponse DwebResult(false, "No update required").toJsonElement()
      },
      // app详情
      "/detailApp" bind HttpMethod.Get to defineBooleanResponse {
        val mmid = request.queryOrFail("app_id")
        debugJMM("detailApp", mmid)
        val microModule = bootstrapContext.dns.query(mmid)

        // TODO: 系统原生应用如WebBrowser的详情页展示？
        if (microModule is JsMicroModule) {
          installJsMicroModule(microModule.metadata, ipc)
          true
        } else {
          false
        }
      })
  }

  override suspend fun _shutdown() {
  }

  /**
   * 从内存中加载数据
   */
  private fun installJmmApps() {
    ioAsyncScope.launch {
      var preList = mutableListOf<DeskAppInfo>()
      DownloadDBStore.queryDeskAppInfoList(context)
        .collectLatest { list -> // TODO 只要datastore更新，这边就会实时更新
          debugJMM("AppInfoDataStore", "size=${list.size}")
          list.map { deskAppInfo ->
            when (deskAppInfo.appType) {
              AppType.MetaData -> deskAppInfo.metadata?.let { jsMetaData ->
                preList.removeIf { it.metadata?.id == jsMetaData.id }
                // 检测版本
                bootstrapContext.dns.query(jsMetaData.id)?.let { lastMetaData ->
                  if (jsMetaData.version.isGreaterThan(lastMetaData.version)) {
                    bootstrapContext.dns.close(lastMetaData.mmid)
                  }
                }
                bootstrapContext.dns.install(JsMicroModule(jsMetaData))
              }

              AppType.URL -> deskAppInfo.weblink?.let { deskWebLink ->
                preList.removeIf { preDeskAppInfo -> preDeskAppInfo.weblink?.id == deskWebLink.id }
                bootstrapContext.dns.install(WebLinkMicroModule(deskWebLink))
              }
            }
          }
          /// 将剩余的应用卸载掉
          for (uninstallItem in preList) {
            uninstallItem.weblink?.deleteIconFile(context) // 删除已下载的图标
            (uninstallItem.metadata?.id ?: uninstallItem.weblink?.id)?.let { uninstallId ->
              bootstrapContext.dns.uninstall(uninstallId)
            }
          }
          preList = list
        }
    }
  }

  private suspend fun installJsMicroModule(
    jmmAppInstallManifest: JmmAppInstallManifest, ipc: Ipc, url: Url? = null,
  ) {
    jmmController?.closeSelf() // 如果存在的话，关闭先，同时可以考虑置空
    if (!jmmAppInstallManifest.bundle_url.startsWith("http")) {
      url?.let {
        jmmAppInstallManifest.bundle_url =
          URLBuilder(it).run {
            resolvePath(jmmAppInstallManifest.bundle_url);
            buildString()
          }
      }
    }
    debugJMM("openJmmMetadataInstallPage", jmmAppInstallManifest.bundle_url)
    // 打开安装的界面
    // JmmManagerActivity.startActivity(jmmAppInstallManifest)
    // 打开安装窗口
    val win = createWindowAdapterManager.createWindow(WindowState(
      WindowConstants(
        owner = mmid, ownerVersion = version, provider = mmid, microModule = this
      )
    ).apply {
      mode = WindowMode.FLOATING
      setFromManifest(this@JmmNMM)
    })
    jmmController = JmmController(win, this@JmmNMM, jmmAppInstallManifest).also { ctrl ->
      ctrl.onClosed {
        if (jmmController == ctrl) {
          jmmController = null
        }
      }
    }
  }

  private suspend fun jmmMetadataUninstall(mmid: MMID) {
    // 先从列表移除，然后删除文件
    bootstrapContext.dns.uninstall(mmid)
    DownloadDBStore.deleteDeskAppInfo(context, mmid)
    FilesUtil.uninstallApp(App.appContext, mmid)
  }
}
