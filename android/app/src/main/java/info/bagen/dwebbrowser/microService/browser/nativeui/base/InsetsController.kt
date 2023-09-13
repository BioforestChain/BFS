package info.bagen.dwebbrowser.microService.browser.nativeui.base

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import info.bagen.dwebbrowser.helper.InsetsJson
import info.bagen.dwebbrowser.helper.StateObservable
import info.bagen.dwebbrowser.microService.browser.nativeui.NativeUiController
import info.bagen.dwebbrowser.util.IsChange
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

abstract class InsetsController(
  val activity: ComponentActivity,
  val nativeUiController: NativeUiController,
) {
  /**
   * 是否层叠渲染
   */
  val overlayState = mutableStateOf(true)

  /**
   * 插入空间
   */
  val insetsState = mutableStateOf(WindowInsets(0))

  /**
   * 状态监听器
   */
  val observer = StateObservable { Json.encodeToString(toJsonAble()) }

  @Composable
  protected open fun observerWatchStates(stateChanges: IsChange) {
    stateChanges.rememberByState(overlayState)
    stateChanges.rememberByState(insetsState)
  }

  @Composable
  abstract fun effect(): InsetsController

  interface InsetsState {
    val overlay: Boolean
    val insets: InsetsJson
  }

  abstract fun toJsonAble(): InsetsState
}