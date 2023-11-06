package org.dweb_browser.browser.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
actual fun SingleChoiceSegmentedButtonRow(
  modifier: Modifier,
  content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit
) {

}

@Composable
actual fun SingleChoiceSegmentedButtonRowScope.SegmentedButton(
  selected: Boolean,
  onClick: () -> Unit,
  shape: Shape,
  modifier: Modifier,
  enabled: Boolean,
  icon: @Composable () -> Unit,
  label: @Composable () -> Unit,
) {

}