package org.dweb_browser.sys.window.ext

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.dweb_browser.core.ipc.Ipc
import org.dweb_browser.core.ipc.helper.IpcEvent
import org.dweb_browser.core.module.NativeMicroModule
import org.dweb_browser.helper.SafeHashMap
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.WeakHashMap
import org.dweb_browser.helper.collectIn
import org.dweb_browser.helper.getOrPut
import org.dweb_browser.helper.listen

/**
 * Renderer：窗口由 window.sys.dweb 被创建后，要求窗口拥有着对内容进行渲染
 */
private const val RENDERER_EVENT_NAME = "renderer"
private const val RENDERER_DESTROY_EVENT_NAME = "renderer-destroy"
fun IpcEvent.Companion.createRenderer(wid: String) = fromUtf8(RENDERER_EVENT_NAME, wid)
fun IpcEvent.Companion.createRendererDestroy(wid: String) =
  fromUtf8(RENDERER_DESTROY_EVENT_NAME, wid)

fun IpcEvent.isRenderer() = name == RENDERER_EVENT_NAME
fun IpcEvent.isRendererDestroy() = name == RENDERER_DESTROY_EVENT_NAME

private val mainWindowIdWM = WeakHashMap<NativeMicroModule.NativeRuntime, CompletableDeferred<String>>()
private fun getMainWindowIdWMDeferred(mm: NativeMicroModule.NativeRuntime) =
  mainWindowIdWM.getOrPut(mm) { CompletableDeferred() }

suspend fun NativeMicroModule.NativeRuntime.getMainWindowId() = getMainWindowIdWMDeferred(this).await()
suspend fun NativeMicroModule.NativeRuntime.getOrOpenMainWindowId() =
  if (!hasMainWindow) openMainWindow().id else getMainWindowId()

val NativeMicroModule.NativeRuntime.hasMainWindow
  get() = getMainWindowIdWMDeferred(this).isCompleted

fun NativeMicroModule.NativeRuntime.onRenderer(cb: suspend RendererContext.() -> Unit) =
  onConnect.listen { (ipc) ->
    ipc.onEvent.collectIn(mmScope) { ipcEvent ->
      if (ipcEvent.isRenderer()) {
        val context = RendererContext.get(ipcEvent, ipc, this@onRenderer)
        context.cb()
        mmScope.launch {
          ipc.awaitClosed()
          context.emitDispose()
        }
      } else if (ipcEvent.isRendererDestroy()) {
        val context = RendererContext.get(ipcEvent, ipc, this@onRenderer)
        context.emitDispose()
      }
    }
  }

class RendererContext(val wid: String, val ipc: Ipc, internal val mm: NativeMicroModule.NativeRuntime) {
  companion object {
    private val windowRendererContexts = SafeHashMap<String, RendererContext>()
    fun get(ipcEvent: IpcEvent, ipc: Ipc, mm: NativeMicroModule.NativeRuntime) = getWid(ipcEvent).let { wid ->
      windowRendererContexts.getOrPut(wid) { RendererContext(wid, ipc, mm) }
    }

    private fun getWid(args: IpcEvent) = args.text
  }

  internal val disposeSignal = lazy { SimpleSignal() }
  val onDispose by lazy { disposeSignal.value.toListener() }

  init {
    getMainWindowIdWMDeferred(mm).complete(wid)
  }

  internal suspend fun emitDispose() {
    mainWindowIdWM.remove(mm)?.cancel()
    if (disposeSignal.isInitialized()) {
      disposeSignal.value.emitAndClear()
    }
  }
}