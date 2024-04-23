package org.dweb_browser.dwebview.messagePort

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dweb_browser.core.ipc.helper.DWebMessage
import org.dweb_browser.core.ipc.helper.IWebMessagePort
import org.dweb_browser.dwebview.DWebView
import org.dweb_browser.helper.launchWithMain
import org.dweb_browser.helper.withMainContext
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.create

class DWebMessagePort(val portId: Int, private val webview: DWebView, parentScope: CoroutineScope) :
  IWebMessagePort {
  init {
    DWebViewWebMessage.allPorts[portId] = this
  }

  internal val _started = lazy {
    val channel = Channel<DWebMessage>(capacity = Channel.UNLIMITED)
    webview.ioScope.launchWithMain {
      webview.engine.evalAsyncJavascript<Unit>(
        "nativeStart($portId)",
        null,
        DWebViewWebMessage.webMessagePortContentWorld
      ).await()
    }
    scope.launch {
      for (msg in channel) {
        messageFlow.emit(msg)
      }
    }
    channel
  }
  private val scope = parentScope + SupervisorJob()

  override suspend fun start() {
    _started.value
  }

  @OptIn(BetaInteropApi::class)
  override suspend fun close(cause: CancellationException?) {
    if (_started.isInitialized()) {
      _started.value.close(cause)
    }
    withMainContext {
      val arguments = mutableMapOf<NSString, NSNumber>().apply {
        put(NSString.create(string = "portId"), NSNumber(portId))
      }

      webview.engine.awaitAsyncJavaScript<Unit>(
        "nativeClose(portId)",
        arguments.toMap(),
        null,
        DWebViewWebMessage.webMessagePortContentWorld
      )
    }
  }

  override suspend fun postMessage(event: DWebMessage) {
    withMainContext {
      if (event is DWebMessage.DWebMessageBytes) {
        val ports = event.ports.map {
          require(it is DWebMessagePort)
          it.portId
        }.joinToString(",")
        webview.engine.evalAsyncJavascript<Unit>(
          "nativePortPostMessage($portId, ${
            Json.encodeToString(event.text)
          }, [$ports])", null, DWebViewWebMessage.webMessagePortContentWorld
        ).await()
      } else if (event is DWebMessage.DWebMessageString) {
        val ports = event.ports.map {
          require(it is DWebMessagePort)
          it.portId
        }.joinToString(",")
        webview.engine.evalAsyncJavascript<Unit>(
          "nativePortPostMessage($portId, ${
            Json.encodeToString(event.text)
          }, [$ports])", null, DWebViewWebMessage.webMessagePortContentWorld
        ).await()
      }
    }
  }


  private val messageFlow = MutableSharedFlow<DWebMessage>()
  internal fun dispatchMessage(message: DWebMessage) = _started.value.trySend(message)
  override val onMessage = messageFlow.asSharedFlow()
}
