package org.dweb_browser.core.ipc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.dweb_browser.core.help.types.IMicroModuleManifest
import org.dweb_browser.helper.Debugger
import org.dweb_browser.helper.SuspendOnce1
import org.dweb_browser.helper.UUID
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.randomUUID
import org.dweb_browser.pure.http.PureStream

val debugIpcPool = Debugger("ipcPool")

val kotlinIpcPool = IpcPool()

/**
 * IpcPool跟上下文对应，跟body流对应
 * Context(Kotlin,Worker,Front)
 * */
open class IpcPool {
  companion object {
    private fun randomPoolId() = "kotlin-${randomUUID()}"
  }

  val scope =
    CoroutineScope(CoroutineName("ipc-pool-kotlin") + ioAsyncExceptionHandler + SupervisorJob())

  /**每一个ipcPool都会绑定一个body流池,只有当不在同一个IpcPool的时候才需要互相拉取*/
  val poolId: UUID = randomPoolId()

  override fun toString() = "IpcPool@poolId=$poolId<uid=$poolId>"

  /**
   * 所有的ipc对象实例集合
   */
  private val ipcSet = mutableSetOf<Ipc>()

  /**
   * 所有的委托进来的流的实例集合
   */
  private val streamPool = mutableMapOf<String, PureStream>()
  suspend fun createIpc(
    endpoint: IpcEndpoint,
    pid: Int,
    locale: IMicroModuleManifest,
    remote: IMicroModuleManifest,
    autoStart: Boolean = false,
    startReason: String? = null,
  ) = Ipc(
    pid = pid,
    endpoint = endpoint,
    locale = locale,
    remote = remote,
    pool = this,
  ).also { ipc ->
    safeCreatedIpc(ipc, autoStart, startReason)
  }

  internal suspend fun safeCreatedIpc(
    ipc: Ipc,
    autoStart: Boolean,
    startReason: String?,
  ) {
    /// 保存ipc，并且根据它的生命周期做自动删除
    debugIpcPool("createIpc", ipc)
    ipcSet.add(ipc)
    /// 自动启动
    if (autoStart) {
      scope.launch {
        ipc.start(reason = startReason ?: "autoStart")
      }
    }
    ipc.onClosed {
      ipcSet.remove(ipc)
      debugIpcPool("removeIpc", ipc)
    }
  }

  /**关闭信号*/
  suspend fun awaitDestroyed() = runCatching {
    scope.coroutineContext[Job]!!.join();
    null
  }.getOrElse { it }

  val isDestroyed get() = scope.coroutineContext[Job]!!.isCancelled

  val destroy = SuspendOnce1 { cause: CancellationException ->
    val oldSet = ipcSet.toSet()
    ipcSet.clear()
    oldSet.forEach { ipc ->
      ipc.close()
    }
    scope.cancel(cause)
  }

}