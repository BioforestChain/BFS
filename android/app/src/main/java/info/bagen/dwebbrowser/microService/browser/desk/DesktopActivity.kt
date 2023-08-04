package info.bagen.dwebbrowser.microService.browser.desk

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
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

  private val taskbarViewModel by taskAppViewModels<TaskbarViewModel>() // 用于记录taskbar的
  private fun initTaskbarViewModel(sessionId: String?) {
    DesktopNMM.taskBarControllers[sessionId]?.also {
      taskbarViewModel.initViewModel(it, sessionId!!)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val deskController = bindController(intent.getStringExtra("deskSessionId"))
    initTaskbarViewModel(intent.getStringExtra("taskBarSessionId")) // taskbarViewModel初始化
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
        val scope = rememberCoroutineScope()
        deskController.effect(activity = this@DesktopActivity)
        BackHandler {
          this@DesktopActivity.moveTaskToBack(false) // 将界面移动到后台，避免重新点击又跑SplashActivity
        }

        CompositionLocalProvider(
          LocalInstallList provides deskController.getInstallApps(),
          LocalOpenList provides deskController.getOpenApps(),
          LocalDesktopView provides deskController.createMainDwebView(),
        ) {
          Box {
            /// 桌面视图
            val desktopView = LocalDesktopView.current
            WebView(
              state = rememberWebViewState(url = deskController.getDesktopUrl().toString()),
              modifier = Modifier.fillMaxSize(),
            ) {
              desktopView
            }
            /// 窗口视图
            desktopWindowsManager.Render()
            /// 浮窗
            FloatTaskbarView(
              state = taskbarViewModel.floatViewState,
              url = taskbarViewModel.taskBarController.getTaskbarUrl().toString()
            )
          }
        }
      }
    }
  }
}

