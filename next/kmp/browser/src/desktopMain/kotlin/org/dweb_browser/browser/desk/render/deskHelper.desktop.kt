package org.dweb_browser.browser.desk.render

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp


actual fun desktopGridLayout(): DesktopGridLayout =
  DesktopGridLayout(GridCells.Adaptive(100.dp), 20.dp, 40.dp)

actual fun desktopTap(): Dp = 20.dp

actual fun desktopBgCircleCount(): Int = 12

actual fun desktopIconSize(): IntSize = IntSize(64, 64)

actual fun taskBarCloseButtonLineWidth() = 2f

actual fun taskBarCloseButtonUsePopUp() = false

@Composable
@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.desktopAppItemActions(onTap: () -> Unit, onMenu: () -> Unit) =
  this.onClick(
    matcher = PointerMatcher.mouse(PointerButton.Primary),
    onClick = onTap,
    onLongClick = onMenu,
  ).onClick(
    matcher = PointerMatcher.mouse(PointerButton.Secondary),
    onClick = onMenu,
  )
