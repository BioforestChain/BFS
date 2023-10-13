package org.dweb_browser.sys.window.core

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import org.dweb_browser.helper.Observable
import org.dweb_browser.helper.platform.PlatformViewController
import org.dweb_browser.sys.window.core.constant.WindowManagerPropertyKeys
import org.dweb_browser.sys.window.render.LocalWindowController
import org.dweb_browser.sys.window.render.LocalWindowPadding
import org.dweb_browser.sys.window.render.LocalWindowsManager
import org.dweb_browser.sys.window.render.watchedBounds
import kotlin.math.max

class ManagerState(
  val viewController: PlatformViewController,
) {
//  val viewHeight get() = activity.window.decorView.height
//  val viewWidth get() = activity.window.decorView.width
//  val displayDensity get() = activity.resources.displayMetrics.density

  val viewHeightDp get() = with(viewController) { getViewHeightPx() / getDisplayDensity() }
  val viewWidthDp get() = with(viewController) { getViewWidthPx() / getDisplayDensity() }

  companion object {
    /// 一些提供给 WindowsManager 的扩展函数
    @Composable
    fun <T, W : WindowController> WindowsManager<W>.watchedState(
      key: Any? = null,
      policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
      filter: ((change: Observable.Change<WindowManagerPropertyKeys, *>) -> Boolean)? = null,
      watchKey: WindowManagerPropertyKeys? = null,
      watchKeys: Set<WindowManagerPropertyKeys>? = null,
      getter: ManagerState.() -> T,
    ): State<T> = remember(key) {
      val rememberState = mutableStateOf(getter.invoke(state), policy)
      val off = state.observable.onChange {
        if ((if (watchKey != null) watchKey == it.key else true) && (if (watchKeys != null) watchKeys.contains(
            it.key
          ) else true) && filter?.invoke(it) != false
        ) {
          rememberState.value = getter.invoke(state)
        }
      }
      Pair(rememberState, off)
    }.let { (rememberState, off) ->
      DisposableEffect(off) {
        onDispose {
          off()
        }
      }
      rememberState
    }

    @Composable
    fun <T : WindowController> WindowsManager<T>.watchedImeBounds() =
      watchedState(WindowManagerPropertyKeys.ImeBounds) { this.imeBounds }

    fun Modifier.windowImeOutsetBounds() = composed {
      composed {
        this
          .runCatching {
            val wsm = LocalWindowsManager.current
            val win = LocalWindowController.current
            val imeVisible by wsm.watchedState { imeVisible }
            val modifierOffsetY: Float
            val keyboardInsetBottom: Float
            // 键盘不显示
            if (!imeVisible) {
              modifierOffsetY = 0f
              keyboardInsetBottom = 0f
            } else {
              val winBounds by win.watchedBounds()
              val imeBounds by wsm.watchedImeBounds()
              val winOuterY = winBounds.top + winBounds.height

              if (winOuterY <= imeBounds.top) {
                // 两个矩形没有交集
                modifierOffsetY = 0f
                keyboardInsetBottom = 0f
              }
              /// 尝试进行偏移修饰
              else {
                val offsetY = winOuterY - imeBounds.top
                // 窗口可以通过向上偏移来确保键盘与窗口同时显示
                if (offsetY <= winBounds.top) {
                  modifierOffsetY = -offsetY
                  keyboardInsetBottom = 0f
                } else {
                  modifierOffsetY = -winBounds.top
                  val winPadding = LocalWindowPadding.current
                  val offsetY2 = offsetY - winPadding.bottom
                  // 窗口可以牺牲底部区域的显示，多出来的就是键盘的插入高度
                  keyboardInsetBottom = max(offsetY2 - winBounds.top, 0f)
                }
              }
            }
            win.state.keyboardInsetBottom = keyboardInsetBottom
            this.offset(y = modifierOffsetY.dp)
          }
          .getOrDefault(this)
      }
    }
  }

  /**
   * 以下是可变属性，所以这里提供一个监听器，来监听所有的属性变更
   */
  val observable = Observable<WindowManagerPropertyKeys>()

  /**
   * IME(input method editor 输入法) 的位置和大小
   */
  var imeBounds by observable.observe(
    WindowManagerPropertyKeys.ImeBounds, WindowBounds(0f, 0f, 0f, 0f)
  )

  var imeVisible by observable.observe(WindowManagerPropertyKeys.ImeVisible, false)

}