package org.dweb_browser.browser.jmm.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dweb_browser.browser.BrowserI18nResource
import org.dweb_browser.browser.jmm.JmmStatus
import org.dweb_browser.browser.jmm.JmmStatusEvent
import org.dweb_browser.browser.jmm.JsMicroModule
import org.dweb_browser.browser.jmm.LocalJmmInstallerController
import org.dweb_browser.helper.compose.produceEvent
import org.dweb_browser.helper.toSpaceSize

@Composable
internal fun BoxScope.BottomDownloadButton() {
  val background = MaterialTheme.colorScheme.surface
  val jmmInstallerController = LocalJmmInstallerController.current
  val jmmState = jmmInstallerController.installMetadata.state
  val canSupportTarget =
    jmmInstallerController.installMetadata.metadata.canSupportTarget(JsMicroModule.VERSION)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .align(Alignment.BottomCenter)
      .background(
        brush = Brush.verticalGradient(listOf(background.copy(0f), background))
      )
      .padding(16.dp), contentAlignment = Alignment.Center
  ) {
    val showLinearProgress =
      jmmState.state == JmmStatus.Downloading || jmmState.state == JmmStatus.Paused

    val modifier = Modifier
      .requiredSize(height = 50.dp, width = 300.dp)
      .fillMaxWidth()
      .clip(ButtonDefaults.elevatedShape)
    val m2 = if (showLinearProgress) {
      val percent = if (jmmState.total == 0L) {
        0f
      } else {
        jmmState.current * 1.0f / jmmState.total
      }
      modifier.background(
        Brush.horizontalGradient(
          0.0f to MaterialTheme.colorScheme.primary,
          maxOf(percent - 0.02f, 0.0f) to MaterialTheme.colorScheme.primary,
          minOf(percent + 0.02f, 1.0f) to MaterialTheme.colorScheme.outlineVariant,
          1.0f to MaterialTheme.colorScheme.outlineVariant
        )
      )
    } else {
      modifier.background(MaterialTheme.colorScheme.primary)
    }

    ElevatedButton(
      onClick = produceEvent(jmmState) {
        when (jmmState.state) {
          JmmStatus.Init, JmmStatus.Failed, JmmStatus.Canceled -> {
            jmmInstallerController.createAndStartDownload()
          }

          JmmStatus.NewVersion -> {
            jmmInstallerController.closeApp()
            jmmInstallerController.createAndStartDownload()
          }

          JmmStatus.Paused -> {
            jmmInstallerController.startDownload()
          }

          JmmStatus.Downloading -> {
            jmmInstallerController.pause()
          }

          JmmStatus.Completed -> {}
          JmmStatus.VersionLow -> {} // 版本偏低时，不响应按键
          JmmStatus.INSTALLED -> {
            jmmInstallerController.openApp()
          }
        }
      },
      modifier = m2,
      colors = ButtonDefaults.elevatedButtonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
      ),
      enabled = canSupportTarget && jmmState.state != JmmStatus.VersionLow // 版本太低，按键置灰
    ) {
      if (canSupportTarget) {
        val (text, total, current) = JmmStatusText(jmmState)
        current?.let { size ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            ResizeSingleText(text = text, textAlign = TextAlign.Center)
            Text(text = size, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text(text = total, modifier = Modifier.weight(1f))
          }
        } ?: Text(text = "$text $total")
      } else {
        Text(text = BrowserI18nResource.install_button_incompatible())
      }
    }
  }
}

/**
 * 为了显示所有内容而修改字体大小
 */
@Composable
fun ResizeSingleText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontStyle: FontStyle? = null,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily = FontFamily.Default,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  textAlign: TextAlign = TextAlign.Start,
  lineHeight: TextUnit = TextUnit.Unspecified,
) {
  var remFontSize by remember { mutableStateOf(fontSize) }
  val textMeasurer = rememberTextMeasurer() // 用于计算文本的实际长度
  val textStyle = TextStyle(
    color = color,
    fontSize = remFontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight
  )
  Text(
    text = text,
    modifier = modifier,
    overflow = TextOverflow.Ellipsis,
    maxLines = 1,
    style = textStyle,
    onTextLayout = { layoutResult ->
      val isLineEllipsized = layoutResult.isLineEllipsized(0)
      val measureWidth = textMeasurer.measure(text, textStyle).size.width
      if (isLineEllipsized && measureWidth > layoutResult.size.width) {
        remFontSize = (remFontSize.value - 1f).sp
      }
    }
  )
}

@Composable
fun JmmStatusText(state: JmmStatusEvent): Triple<String, String, String?> {
  return when (state.state) {
    JmmStatus.Init, JmmStatus.Canceled -> Triple(
      first = BrowserI18nResource.install_button_install(),
      second = " " + state.total.toSpaceSize(),
      third = null
    )

    JmmStatus.NewVersion -> Triple(
      first = BrowserI18nResource.install_button_update(),
      second = " " + state.total.toSpaceSize(),
      third = null
    )

    JmmStatus.Downloading -> Triple(
      first = BrowserI18nResource.install_button_downloading(),
      second = " / " + state.total.toSpaceSize(),
      third = state.current.toSpaceSize()
    )

    JmmStatus.Paused -> Triple(
      first = BrowserI18nResource.install_button_paused(),
      second = " / " + state.total.toSpaceSize(),
      third = state.current.toSpaceSize()
    )

    JmmStatus.Completed -> Triple(
      first = BrowserI18nResource.install_button_installing(),
      second = "",
      third = null
    )

    JmmStatus.INSTALLED -> Triple(
      first = BrowserI18nResource.install_button_open(),
      second = "",
      third = null
    )

    JmmStatus.Failed -> Triple(
      first = BrowserI18nResource.install_button_retry(),
      second = "",
      third = null
    )

    JmmStatus.VersionLow -> Triple(
      first = BrowserI18nResource.install_button_lower(),
      second = "",
      third = null
    )
  }
}