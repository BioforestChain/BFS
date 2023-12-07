package org.dweb_browser.browser.desk

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dweb_browser.helper.Rect
import org.dweb_browser.helper.platform.IPureViewBox
import org.dweb_browser.helper.platform.IPureViewController
import org.dweb_browser.helper.platform.from
import org.dweb_browser.helper.platform.theme.DwebBrowserAppTheme

fun Rect.toModifier(
  modifier: Modifier = Modifier,
) = modifier.offset(x.dp, y.dp).size(width.dp, height.dp)

class DesktopViewControllerCore(val controller: IPureViewController) {
  private var desktopController: DesktopController? = null
  private suspend fun bindController(sessionId: String?): DeskNMM.Companion.DeskControllers {
    /// 解除上一个 controller的activity绑定
    desktopController?.activity = null

    return DeskNMM.controllersMap[sessionId]?.also { controllers ->
      controllers.desktopController.activity = controller
      this.desktopController = controllers.desktopController
      controllers.activityPo.resolve(IPureViewBox.from(controller))
    } ?: throw Exception("no found controller by sessionId: $sessionId")
  }
  private val resumeState = mutableStateOf(false) // 增加字段，为了恢复 taskbarFloatView

  init {
    controller.onCreate { params ->
      val (desktopController, taskbarController, microModule) = bindController(params.getString("deskSessionId"))
      controller.addContent {
        DwebBrowserAppTheme {
          LaunchedEffect(resumeState) {
            snapshotFlow { resumeState.value }.collect {
              // 增加字段，为了恢复 taskbarFloatView
              if (it) taskbarController.toggleFloatWindow(false)
            }
          }
          desktopController.Render(taskbarController, microModule)
        }
      }
    }

    controller.onResume {
      resumeState.value = true
    }

    controller.onPause {
      resumeState.value = false
    }

    controller.onDestroy {
      desktopController?.activity = null
    }
  }
}
