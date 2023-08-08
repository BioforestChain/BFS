package info.bagen.dwebbrowser.microService.browser.desk

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.web.WebView
import info.bagen.dwebbrowser.base.BaseActivity
import info.bagen.dwebbrowser.microService.browser.desk.view.Render
import info.bagen.dwebbrowser.microService.core.WindowState
import info.bagen.dwebbrowser.ui.theme.DwebBrowserAppTheme

@SuppressLint("ModifierFactoryExtensionFunction")
fun WindowState.WindowBounds.toModifier(
  modifier: Modifier = Modifier,
) = modifier
  .offset(left.dp, top.dp)
  .size(width.dp, height.dp)

class DesktopActivity : BaseActivity() {
  private var controller: DeskController? = null
  private fun bindController(sessionId: String?): DeskController {
    /// 解除上一个 controller的activity绑定
    controller?.activity = null

    return DesktopNMM.deskControllers[sessionId]?.also { desktopController ->
      desktopController.activity = this
      controller = desktopController
    } ?: throw Exception("no found controller by sessionId: $sessionId")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val deskController = bindController(intent.getStringExtra("deskSessionId"))
    startService(Intent(this@DesktopActivity, TaskbarService::class.java).also {
      it.putExtra("taskBarSessionId", intent.getStringExtra("taskBarSessionId"))
    })
    /*val taskBarSessionId = intent.getStringExtra("taskBarSessionId")

    val context = this@DesktopActivity
    context.startActivity(Intent(context, TaskbarActivity::class.java).also {
      it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      it.putExtras(Bundle().also { b -> b.putString("taskBarSessionId", taskBarSessionId) })
    })*/

    /**
     * 窗口管理器
     */
    val desktopWindowsManager = deskController.desktopWindowsManager

    setContent {
      DwebBrowserAppTheme {
        deskController.effect(activity = this@DesktopActivity)
        BackHandler {
          TaskbarModel.closeFloatWindow()
          this@DesktopActivity.moveTaskToBack(true) // 将界面移动到后台，避免重新点击又跑SplashActivity
        }

        CompositionLocalProvider(
          LocalInstallList provides deskController.getInstallApps(),
          LocalOpenList provides deskController.getOpenApps(),
          LocalDesktopView provides deskController.createMainDwebView(
            "desktop", deskController.getDesktopUrl().toString()
          ),
        ) {
          Box {
            /// 桌面视图
            val desktopView = LocalDesktopView.current
            WebView(
              state = desktopView.state,
              navigator = desktopView.navigator,
              modifier = Modifier.fillMaxSize(),
            ) {
              desktopView.webView
            }
            /// 窗口视图
            desktopWindowsManager.Render()
            /// 浮窗
            /// FloatTaskbarView()
          }
        }
      }
    }
  }

  private var isPause = false

  override fun onResume() {
    TaskbarModel.openFloatWindow()
    isPause = false
    super.onResume()
  }

  override fun onPause() {
    isPause = true
    super.onPause()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    if (!hasFocus && !isPause) { // 验证发现，如果通过系统上滑退出会先执行失焦，然后才走到onPause，其他情况都会先执行onPause
      TaskbarModel.closeFloatWindow()
    }
    super.onWindowFocusChanged(hasFocus)
  }
}

