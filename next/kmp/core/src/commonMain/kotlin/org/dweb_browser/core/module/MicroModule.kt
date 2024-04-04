package org.dweb_browser.core.module

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.dweb_browser.core.help.types.CommonAppManifest
import org.dweb_browser.core.help.types.IMicroModuleManifest
import org.dweb_browser.core.help.types.MMID
import org.dweb_browser.core.help.types.MicroModuleManifest
import org.dweb_browser.core.ipc.Ipc
import org.dweb_browser.core.std.permission.PermissionProvider
import org.dweb_browser.helper.Debugger
import org.dweb_browser.helper.SafeHashMap
import org.dweb_browser.helper.SafeHashSet
import org.dweb_browser.helper.defaultAsyncExceptionHandler
import org.dweb_browser.helper.listen
import org.dweb_browser.pure.http.PureRequest

typealias Router = MutableMap<String, AppRun>
typealias AppRun = (options: NativeOptions) -> Any
typealias NativeOptions = MutableMap<String, String>

enum class MMState {
  BOOTSTRAP, SHUTDOWN,
}

abstract class MicroModule(val manifest: MicroModuleManifest) : IMicroModuleManifest by manifest {
  val debugMM by lazy { Debugger("$this") }

  companion object {}

  /**
   * 获取权限提供器，这需要在bootstrap之前就能提供
   * 因为 dweb_permissions 字段并不难直接使用，所以需要模块对其数据进行加工处理，从而确保数据合法与安全
   */
  abstract suspend fun getSafeDwebPermissionProviders(): List<PermissionProvider>

  /**
   * 如果启动
   * 那么会创建该运行时
   */
  abstract inner class Runtime : IMicroModuleManifest by manifest {
    val debugMM get() = this@MicroModule.debugMM
    abstract val bootstrapContext: BootstrapContext
    val scope =
      CoroutineScope(SupervisorJob() + defaultAsyncExceptionHandler + CoroutineName(manifest.mmid))

    open val routers: Router? = null

    private fun getModuleCoroutineScope() =
      CoroutineScope(SupervisorJob() + defaultAsyncExceptionHandler + CoroutineName(mmid))

    val mmScope = getModuleCoroutineScope() // 给外部使用

    private val stateLock = Mutex()
    private var state = MMState.SHUTDOWN
    val isRunning get() = state == MMState.BOOTSTRAP


    protected abstract suspend fun _bootstrap()

    private val beforeBootstrapFlow = MutableSharedFlow<Unit>()
    val onBeforeBootstrap = beforeBootstrapFlow.shareIn(mmScope, SharingStarted.Eagerly)


    private val afterBootstrapFlow = MutableSharedFlow<Unit>()
    val onAfterBootstrap = afterBootstrapFlow.shareIn(mmScope, SharingStarted.Eagerly)

    suspend fun bootstrap() = stateLock.withLock {
      if (state != MMState.BOOTSTRAP) {
        debugMM("bootstrap-start")
        beforeBootstrapFlow.emit(Unit)
        _bootstrap();
        afterBootstrapFlow.emit(Unit)
        debugMM("bootstrap-end")
      } else {
        debugMM("bootstrap", "$mmid already running")
      }
      state = MMState.BOOTSTRAP
    }

    val microModule get() = this@MicroModule

    private val beforeShutdownFlow = MutableSharedFlow<Unit>()
    val onBeforeShutdown = beforeShutdownFlow.shareIn(mmScope, SharingStarted.Eagerly)

    private val afterShutdownFlow = MutableSharedFlow<Unit>()
    val onAfterShutdown = afterShutdownFlow.shareIn(mmScope, SharingStarted.Eagerly)

//
//    /**
//     * 让回调函数一定在启动状态内被运行
//     */
//    suspend fun <R> withBootstrap(block: suspend () -> R) = readyLock.withLock { block() }

    protected abstract suspend fun _shutdown()

    suspend fun shutdown() = stateLock.withLock {
      if (state != MMState.SHUTDOWN) {
        beforeShutdownFlow.emit(Unit)
        _shutdown()
        afterShutdownFlow.emit(Unit)
        // 取消所有的工作
        this.mmScope.cancel()
      }
      state = MMState.SHUTDOWN
    }

    /**
     * MicroModule 引用池
     */
    private val connectionLinks = SafeHashSet<Ipc>()


    /**
     * 内部程序与外部程序通讯的方法
     */
    private val ipcConnectedChannel = Channel<IpcConnectArgs>();

    /**
     * 给内部程序自己使用的 onConnect，外部与内部建立连接时使用
     * 因为 NativeMicroModule 的内部程序在这里编写代码，所以这里会提供 onConnect 方法
     * 如果时 JsMicroModule 这个 onConnect 就是写在 WebWorker 那边了
     */
    val onConnect = ipcConnectedChannel.consumeAsFlow().shareIn(mmScope, SharingStarted.Lazily)

    private val connectionMap = SafeHashMap<MMID, Deferred<Ipc>>()

    /**
     * 尝试连接到指定对象
     */
    suspend fun connect(mmid: MMID, reason: PureRequest? = null) = connectionMap.getOrPut(mmid) {
      mmScope.async {
        bootstrapContext.dns.connect(mmid, reason).also {
          beConnect(it, reason)
        }
      }
    }.await()

    /**
     * 收到一个连接，触发相关事件
     */
    suspend fun beConnect(ipc: Ipc, reason: PureRequest?) {
      if (connectionLinks.add(ipc)) {
        ipc.onFork.listen {
          beConnect(it, null)
        }
        onBeforeShutdown.listen {
          ipc.close()
        }
        ipc.onBeforeClose.listen {
          connectionLinks.remove(ipc)
        }
        // 尝试保存到双向连接索引中
        @Suppress("DeferredResultUnused") connectionMap.getOrPut(ipc.remote.mmid) {
          CompletableDeferred(ipc)
        }
        println("QAQ ipcConnectedChannel.send=$ipc")
        ipcConnectedChannel.send(IpcConnectArgs(ipc, reason))
      }
    }
  }

  private val runtimeLock = Mutex()
  var runtimeOrNull: Runtime? = null
    private set
  val isRunning get() = runtimeOrNull != null
  open val runtime get() = runtimeOrNull ?: throw IllegalStateException("$this is no running")
  suspend fun bootstrap(bootstrapContext: BootstrapContext) = runtimeLock.withLock {
    runtimeOrNull ?: createRuntime(bootstrapContext).also {
      runtimeOrNull = it
      it.bootstrap()
    }
  }

  abstract fun createRuntime(bootstrapContext: BootstrapContext): Runtime

  override fun toString(): String {
    return "MicroModule($mmid)"
  }

  open fun toManifest(): CommonAppManifest {
    return manifest.toCommonAppManifest()
  }
}

data class IpcConnectArgs(val ipc: Ipc, val reason: PureRequest?)
