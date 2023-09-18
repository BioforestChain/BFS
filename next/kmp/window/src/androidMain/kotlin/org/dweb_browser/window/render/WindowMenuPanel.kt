package org.dweb_browser.window.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.RichTooltipColors
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dweb_browser.window.core.WindowController

@Composable
internal fun WindowMenuPanel(
  win: WindowController,
) {
  WindowMenuPanelByAlert(win)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WindowMenuPanelByRichTooltip(
  win: WindowController,
) {
  val winPadding = LocalWindowPadding.current
  val winTheme = LocalWindowControllerTheme.current
  val isShowMenuPanel by win.watchedState { showMenuPanel }
  val tooltipState = rememberTooltipState(isPersistent = true)
  val scope = rememberCoroutineScope()

  LaunchedEffect(tooltipState.isVisible) {
    scope.launch {
      if (!tooltipState.isVisible) {
        win.hideMenuPanel()
      }
    }
  }
  LaunchedEffect(isShowMenuPanel) {
    scope.launch {
      if (isShowMenuPanel) {
        tooltipState.show()
      } else {
        tooltipState.dismiss()
      }
    }
  }
  val isMaximized by win.watchedIsMaximized()
  val shape = remember(winPadding, isMaximized) {
    if (isMaximized) winPadding.contentRounded.toRoundedCornerShape()
    else winPadding.boxRounded.toRoundedCornerShape()
  }
  val colors = remember(winTheme) {
    RichTooltipColors(
      containerColor = winTheme.themeColor,
      contentColor = winTheme.themeContentColor,
      titleContentColor = winTheme.themeContentColor,
      actionContentColor = winTheme.themeContentColor,
    )
  }
  val maxHeight = win.state.bounds.height.dp
  TooltipBox(
    modifier = if (isMaximized) Modifier else Modifier.sizeIn(
      maxWidth = win.state.bounds.width.dp, maxHeight = maxHeight
    ),
    positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
    tooltip = {
      RichTooltip(
        title = {
          Row {
            val owner = win.state.constants.owner
            win.IconRender(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = owner, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        },
        colors = colors,
        shape = shape,
        text = {
          Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            WindowControlPanel(win)

            ElevatedButton(onClick = {
              scope.launch {
                win.hideMenuPanel()
                win.close() // 增加关闭窗口
              }
            }) {
              Text("退出应用")
            }
          }
        },
      )
    },
    state = tooltipState,
  ) {
    Box(modifier = Modifier.fillMaxSize()) // 为了保证弹出的时候，不会覆盖工具栏
  }
}


@Composable
fun WindowControllerTheme.toButtonColors() = ButtonDefaults.buttonColors(
  contentColor = themeContentColor,
  containerColor = themeColor,
  disabledContentColor = themeContentDisableColor
)

@Composable
internal fun WindowMenuPanelByAlert(
  win: WindowController,
) {
  val scope = rememberCoroutineScope()
  val isShowMenuPanel by win.watchedState { showMenuPanel }
  val toggleMenu = { show: Boolean ->
    scope.launch {
      win.toggleMenuPanel(show)
    }
  }
  if (isShowMenuPanel) {
    val winTheme = LocalWindowControllerTheme.current
    val buttonColors = winTheme.toButtonColors()
    AlertDialog(
      onDismissRequest = {
        toggleMenu(false)
      },
      containerColor = winTheme.themeColor,
      iconContentColor = winTheme.themeContentColor,
      textContentColor = winTheme.themeContentColor,
      titleContentColor = winTheme.themeContentColor,
      icon = {
        win.IconRender(modifier = Modifier.size(36.dp))
      },
      title = {
        val owner = win.state.constants.owner
        Text(text = owner, maxLines = 1, overflow = TextOverflow.Ellipsis)
      },
      text = {
        WindowControlPanel(win)
      },
      confirmButton = {
        ElevatedButton(
          onClick = {
            scope.launch {
              win.hideMenuPanel()
              win.close() // 增加关闭窗口
            }
          },
          colors = buttonColors,
        ) {
          Text("退出应用")
        }
      },
      dismissButton = {
        Button(
          onClick = {
            toggleMenu(false)
          },
          colors = buttonColors,
        ) {
          Text("关闭面板")
        }
      },
    )// AlertDialog

  }
}