package org.dweb_browser.browser.desk.render

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp


actual fun desktopGridLayout(): DesktopGridLayout =
  DesktopGridLayout(
    cells = GridCells.Adaptive(64.dp),
    insets = WindowInsets(left = 28.dp, right = 28.dp),
    horizontalSpace = 8.dp,
    verticalSpace = 16.dp,
  )

actual fun desktopBgCircleCount(): Int = 8

actual fun desktopIconSize(): IntSize = IntSize(50, 50)

actual fun taskBarCloseButtonLineWidth() = 5f

actual fun taskBarCloseButtonUsePopUp() = true

@Composable
@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.desktopAppItemActions(onOpenApp: () -> Unit, onOpenAppMenu: () -> Unit) =
  this.combinedClickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() },
    onClick = onOpenApp,
    onLongClick = onOpenAppMenu
  )

