package org.dweb_browser.core.ipc

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.dweb_browser.core.ipc.helper.EndpointIpcMessage
import org.dweb_browser.core.ipc.helper.EndpointLifecycle
import org.dweb_browser.core.ipc.helper.EndpointLifecycleInit
import org.dweb_browser.core.ipc.helper.EndpointProtocol
import org.dweb_browser.helper.withScope
import kotlin.math.min


class NativeMessageChannel(parentScope: CoroutineScope, fromId: String, toId: String) {
  /**
   * 默认锁住，当它解锁的时候，意味着通道关闭
   */
  private val messageChannel1 = Channel<EndpointIpcMessage>()
  private val messageChannel2 = Channel<EndpointIpcMessage>()
  private val lifecycleFlow1 = MutableStateFlow(EndpointLifecycle(EndpointLifecycleInit))
  private val lifecycleFlow2 = MutableStateFlow(EndpointLifecycle(EndpointLifecycleInit))
  private var debugId1 = ""
  private var debugId2 = ""

  /// 取最短的前缀，从而做成
  init {
    if (fromId == toId) {
      debugId1 = "$fromId[from]↹"
      debugId2 = "$toId[to]↹"
    } else {
      val from = fromId.split(".")
      val to = toId.split(".")
      val maxFromEndIndex = from.size - 1
      val maxToEndIndex = to.size - 1
      var debugFromId = ""
      var debugToId = ""
      for (endIndex in 1..<min(maxFromEndIndex, maxToEndIndex)) {
        debugFromId = from.subList(0, endIndex).joinToString(".")
        debugToId = to.subList(0, endIndex).joinToString(".")
        if (debugFromId != debugToId) {
          break
        }
      }
      if (debugFromId == debugToId) {
        debugFromId = fromId
        debugToId = toId
      }
      debugId1 = "$debugFromId=>$debugToId"
      debugId2 = "$debugToId=>$debugFromId"
    }
  }

  val port1 =
    NativeEndpoint(parentScope, messageChannel1, messageChannel2, lifecycleFlow1, debugId1)
  val port2 =
    NativeEndpoint(parentScope, messageChannel2, messageChannel1, lifecycleFlow2, debugId2)
}

class NativeEndpoint(
  parentScope: CoroutineScope,
  private val messageIn: Channel<EndpointIpcMessage>,
  private val messageOut: Channel<EndpointIpcMessage>,
  override val lifecycleRemoteFlow: MutableStateFlow<EndpointLifecycle>,
  override val debugId: String,
) : IpcEndpoint() {
  override fun toString() = "NativeEndpoint@$debugId"

  override val scope = parentScope + SupervisorJob() + CoroutineName("$this")

  /**
   * 发送消息
   */
  override suspend fun postIpcMessage(msg: EndpointIpcMessage) {
    awaitOpen("then-postIpcMessage")
    withScope(scope) {
      debugEndpoint("message-out", msg)
      messageOut.send(msg)
    }
  }

  override suspend fun doStart() {
    scope.launch {
      for ((pid, ipcMessage) in messageIn) {
        debugEndpoint("message-in", "pid=$pid ipcMessage=$ipcMessage")
        /**
         * UNDISPATCHED 能确保 orderBy 的顺序
         */
        launch(start = CoroutineStart.UNDISPATCHED) {
          getIpcMessageProducer(pid).also {
            it.producer.trySend(ipcMessage)
          }
        }
      }
    }
  }

  /**
   * 原生通讯，不需要提供任何协商内容
   */
  override fun getLocaleSubProtocols() = setOf<EndpointProtocol>()

  override suspend fun sendLifecycleToRemote(state: EndpointLifecycle) {
    debugEndpoint("lifecycle-out", state)
    lifecycleRemoteFlow.emit(state)
  }
}