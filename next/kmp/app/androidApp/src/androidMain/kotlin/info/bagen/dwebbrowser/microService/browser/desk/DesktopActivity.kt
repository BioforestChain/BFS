package info.bagen.dwebbrowser.microService.browser.desk

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import org.dweb_browser.core.module.BaseThemeActivity
import org.dweb_browser.helper.compose.theme.DwebBrowserAppTheme
import org.dweb_browser.sys.window.render.LocalWindowsImeVisible

class DesktopActivity : BaseThemeActivity() {
  private var desktopController: DesktopController? = null
  private fun bindController(sessionId: String?): DeskNMM.Companion.DeskControllers {
    /// 解除上一个 controller的activity绑定
    desktopController?.activity = null

    return DeskNMM.controllersMap[sessionId]?.also { controllers ->
      controllers.desktopController.activity = this
      this.desktopController = controllers.desktopController
      controllers.activityPo.resolve(this)
    } ?: throw Exception("no found controller by sessionId: $sessionId")
  }

  @OptIn(ExperimentalLayoutApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val (desktopController, taskbarController, microModule) = bindController(intent.getStringExtra("deskSessionId"))
    /// 禁止自适应布局，执行后，可以将我们的内容嵌入到状态栏和导航栏，但是会发现我们的界面呗状态栏和导航栏给覆盖了，这时候就需要systemUiController来改颜色
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContent {
      val imeVisible = LocalWindowsImeVisible.current
      val density = LocalDensity.current
      val ime =
        androidx.compose.foundation.layout.WindowInsets.imeAnimationTarget // 直接使用ime，数据不稳定，会变化，改为imeAnimationTarget就是固定值
      BackHandler {
        this@DesktopActivity.moveTaskToBack(true) // 将界面移动到后台，避免重新点击又跑SplashActivity
      }

      DwebBrowserAppTheme {
        desktopController.Render(taskbarController, microModule)
      }

      LaunchedEffect(Unit) {
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
          imeVisible.value = ime.getBottom(density) != 0
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    desktopController?.activity = null
  }
}