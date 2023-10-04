package org.dweb_browser.microservice.std.dns

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.dweb_browser.helper.ChangeState
import org.dweb_browser.helper.ChangeableMap
import org.dweb_browser.helper.PromiseOut
import org.dweb_browser.helper.printDebug
import org.dweb_browser.helper.removeWhen
import org.dweb_browser.helper.toJsonElement
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.ConnectResult
import org.dweb_browser.microservice.core.DnsMicroModule
import org.dweb_browser.microservice.core.MicroModule
import org.dweb_browser.microservice.core.NativeMicroModule
import org.dweb_browser.microservice.core.connectMicroModules
import org.dweb_browser.microservice.help.buildRequestX
import org.dweb_browser.microservice.help.types.MICRO_MODULE_CATEGORY
import org.dweb_browser.microservice.help.types.MMID
import org.dweb_browser.microservice.http.PureRequest
import org.dweb_browser.microservice.http.PureResponse
import org.dweb_browser.microservice.http.PureStringBody
import org.dweb_browser.microservice.http.bind
import org.dweb_browser.microservice.http.bindDwebDeeplink
import org.dweb_browser.microservice.ipc.helper.IpcEvent
import org.dweb_browser.microservice.ipc.helper.IpcMethod
import org.dweb_browser.microservice.ipc.helper.ReadableStream

fun debugDNS(tag: String, msg: Any = "", err: Throwable? = null) =
  printDebug("fetch", tag, msg, err)

class DnsNMM : NativeMicroModule("dns.std.dweb", "Dweb Name System") {
  init {
    dweb_deeplinks = mutableListOf("dweb://open")
    short_name = "DNS";
    categories = listOf(MICRO_MODULE_CATEGORY.Service, MICRO_MODULE_CATEGORY.Routing_Service);
  }

  private val installApps = ChangeableMap<MMID, MicroModule>() // 已安装的应用
  private val runningApps = ChangeableMap<MMID, PromiseOut<MicroModule>>() // 正在运行的应用

  suspend fun bootstrap() {
    if (!this.running) {
      bootstrapMicroModule(this)
    }
  }

  data class MM(val fromMMID: MMID, val toMMID: MMID) {
    companion object {
      val values = mutableMapOf<MMID, MutableMap<MMID, MM>>()
      fun from(fromMMID: MMID, toMMID: MMID) =
        values.getOrPut(fromMMID) { mutableMapOf() }.getOrPut(toMMID) { MM(fromMMID, toMMID) }
    }
  }

  /** 对等连接列表 */
  private val mmConnectsMap = mutableMapOf<MM, PromiseOut<ConnectResult>>()
  private val mmConnectsMapLock = Mutex()

  /** 为两个mm建立 ipc 通讯 */
  private suspend fun connectTo(
    fromMM: MicroModule, toMMID: MMID, reason: PureRequest
  ) = mmConnectsMapLock.withLock {
    val mmKey = MM.from(fromMM.mmid, toMMID)
    /**
     * 一个互联实例
     */
    mmConnectsMap.getOrPut(mmKey) {
      PromiseOut<ConnectResult>().alsoLaunchIn(ioAsyncScope) {
        debugFetch("DNS/open", "${fromMM.mmid} => $toMMID")
        val toMM = open(toMMID)
        debugFetch("DNS/connect", "${fromMM.mmid} <=> $toMMID")
        val connectResult = connectMicroModules(fromMM, toMM, reason)
        connectResult.ipcForFromMM.onClose {
          mmConnectsMap.remove(mmKey)
        }

        // 如果可以，反向存储
        if (connectResult.ipcForToMM != null) {
          val mmKey2 = MM.from(toMMID, fromMM.mmid)
          mmConnectsMapLock.withLock {
            mmConnectsMap.getOrPut(mmKey2) {
              PromiseOut<ConnectResult>().also { po2 ->
                val connectResult2 = ConnectResult(
                  connectResult.ipcForToMM, connectResult.ipcForFromMM
                );
                connectResult2.ipcForToMM?.also {
                  it.onClose {
                    mmConnectsMap.remove(mmKey2)
                  }
                }
                po2.resolve(connectResult2)
              }
            }
          }
        }
        connectResult
      }
    }

  }.waitPromise()

  class MyDnsMicroModule(private val dnsMM: DnsNMM, private val fromMM: MicroModule) :
    DnsMicroModule {
    override fun install(mm: MicroModule) {
      // TODO 作用域保护
      dnsMM.install(mm)
    }

    override fun uninstall(mmid: MMID): Boolean {
      // TODO 作用域保护
      return dnsMM.uninstall(mmid)
    }

    override fun query(mmid: MMID): MicroModule? {
      return dnsMM.query(mmid)
    }

    override suspend fun search(category: MICRO_MODULE_CATEGORY): MutableList<MicroModule> {
      return dnsMM.search(category)
    }

    // 调用重启
    override suspend fun restart(mmid: MMID) {
      // 关闭后端连接
      dnsMM.close(mmid)
      dnsMM.open(mmid)
    }

    override suspend fun connect(
      mmid: MMID, reason: PureRequest?
    ): ConnectResult {
      // TODO 权限保护
      return dnsMM.connectTo(
        fromMM, mmid, reason ?: PureRequest("file://$mmid", IpcMethod.GET)
      )
    }

    override suspend fun open(mmid: MMID): Boolean {
      if (this.dnsMM.runningApps[mmid] == null) {
        return false
      }
      dnsMM.open(mmid)
      return true
    }

    override suspend fun close(mmid: MMID): Boolean {
      if (this.dnsMM.runningApps[mmid] !== null) {
        dnsMM.close(mmid);
        return true;
      }
      return false;
    }
  }

  class MyBootstrapContext(override val dns: MyDnsMicroModule) : BootstrapContext {}

  suspend fun bootstrapMicroModule(fromMM: MicroModule) {
    fromMM.bootstrap(MyBootstrapContext(MyDnsMicroModule(this@DnsNMM, fromMM)))
  }

  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    install(this)
    runningApps[this.mmid] = PromiseOut.resolve(this)

    /**
     * 对全局的自定义路由提供适配器
     * 对 nativeFetch 定义 file://xxx.dweb的解析
     */
    nativeFetchAdaptersManager.append { fromMM, request ->
      if (request.url.protocol.name == "file" && request.url.host.endsWith(".dweb")) {
        val mmid = request.url.host
        debugFetch("DNS/nativeFetch", "$fromMM => ${request.href}")
        val url = request.href
        val reasonRequest =
          buildRequestX(url, request.method, request.headers, request.body);
        installApps[mmid]?.let {
          val (fromIpc) = connectTo(fromMM, mmid, reasonRequest)
          return@let fromIpc.request(request)
        } ?: PureResponse(HttpStatusCode.BadGateway, body = PureStringBody(url))
      } else null
    }.removeWhen(this.onAfterShutdown)
    /** dwebDeepLink 适配器*/
    nativeFetchAdaptersManager.append { fromMM, request ->
      if (request.href.startsWith("dweb:")) {
        debugFetch("DPLink/nativeFetch", "$fromMM => ${request.href}")
        for (microModule in installApps) {
          for (deeplink in microModule.value.dweb_deeplinks) {
            if (request.href.startsWith(deeplink)) {
              val (fromIpc) = connectTo(fromMM, microModule.key, request)
              return@append fromIpc.request(request)
            }
          }
        }
        return@append PureResponse(
          HttpStatusCode.BadGateway,
          body = PureStringBody(request.href)
        )
      } else null
    }.removeWhen(this.onAfterShutdown)

    val queryAppId = PureRequest.query("app_id")
    val openApp = defineBooleanResponse {
      val mmid = request.queryAppId()
      debugDNS("open/$mmid", request.url.fullPath)
      open(mmid)
      true
    }
    /// 定义路由功能
    routes(
      "open" bindDwebDeeplink openApp,
      // 打开应用
      "/open" bind HttpMethod.Get to openApp,
      // 关闭应用
      // TODO 能否关闭一个应该应该由应用自己决定
      "/close" bind HttpMethod.Get to defineBooleanResponse {
        val mmid = request.queryAppId()
        debugDNS("close/$mmid", request.url.fullPath)
        close(mmid)
        true
      },
      //
      "/query" bind HttpMethod.Get to defineJsonResponse {
        val mmid = request.queryAppId()
        Json.encodeToString("")
        query(mmid)?.toManifest()?.toJsonElement() ?: JsonNull
      },
      //
      "/observe/install-apps" bind HttpMethod.Get to definePureStreamHandler {
        val inputStream = ReadableStream { controller ->
          val off = installApps.onChange { changes ->
            try {
              controller.enqueueBackground(
                (Json.encodeToString(
                  ChangeState(
                    changes.adds, changes.updates, changes.removes
                  )
                ) + "\n").toByteArray()
              )
            } catch (e: Exception) {
              controller.close()
              e.printStackTrace()
            }
          }
          ipc.onClose {
            off()
            controller.close()
          }
        }
        inputStream.stream
      },
      //
      "/observe/running-apps" bind HttpMethod.Get to definePureStreamHandler {
        val inputStream = ReadableStream(onStart = { controller ->
          val off = runningApps.onChange { changes ->
            try {
              controller.enqueueBackground(
                (Json.encodeToString(
                  ChangeState(
                    changes.adds, changes.updates, changes.removes
                  )
                ) + "\n").toByteArray()
              )
            } catch (e: Exception) {
              controller.close()
              e.printStackTrace()
            }
          }
          ipc.onClose {
            off()
            controller.close()
          }
        })
        inputStream.stream
      })

    /// 启动 boot 模块
    connect("boot.sys.dweb").postMessage(IpcEvent.fromUtf8("activity", ""))
  }

  override suspend fun _shutdown() {
    installApps.forEach {
      it.value.shutdown()
    }
    installApps.clear()
    ioAsyncScope.cancel()
  }

  /** 安装应用 */
  fun install(mm: MicroModule) {
    installApps[mm.mmid] = mm
  }

  /** 卸载应用 */
  @OptIn(DelicateCoroutinesApi::class)
  fun uninstall(mmid: MMID): Boolean {
    installApps.remove(mmid)
    ioAsyncScope.launch { close(mmid) }
    return true
  }

  /** 查询应用 */
  fun query(mmid: MMID): MicroModule? {
    return installApps[mmid]
  }

  /**
   * 根据类目搜索模块
   * > 这里暂时不需要支持复合搜索，未来如果有需要另外开接口
   * @param category
   */
  fun search(category: MICRO_MODULE_CATEGORY): MutableList<MicroModule> {
    val categoryList = mutableListOf<MicroModule>()
    for (app in this.installApps.values) {
      if (app.categories.contains(category)) {
        categoryList.add(app)
      }
    }
    return categoryList
  }

  /** 打开应用 */
  private fun _open(mmid: MMID): PromiseOut<MicroModule> {
    return runningApps.getOrPut(mmid) {
      PromiseOut<MicroModule>().alsoLaunchIn(ioAsyncScope) {
        when (val openingMm = query(mmid)) {
          null -> {
            runningApps.remove(mmid)
            throw Exception("no found app: $mmid")
          }

          else -> {
            bootstrapMicroModule(openingMm)
            openingMm.onAfterShutdown {
              if (runningApps[mmid] !== null) {
                runningApps.remove(mmid)
              }
            }
            openingMm
          }
        }
      }
    }
  }

  suspend fun open(mmid: MMID): MicroModule {
    return _open(mmid).waitPromise()
  }

  /** 关闭应用 */
  suspend fun close(mmid: MMID): Int {
    return runningApps.remove(mmid)?.let { microModulePo ->
      runCatching {
        val microModule = microModulePo.waitPromise()
        microModule.shutdown()
        1
      }.getOrDefault(0)
    } ?: -1
  }
}



