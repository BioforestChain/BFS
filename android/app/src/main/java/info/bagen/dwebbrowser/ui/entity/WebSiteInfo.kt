package info.bagen.dwebbrowser.ui.entity

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.web.WebViewNavigator
import com.google.accompanist.web.WebViewState
import info.bagen.dwebbrowser.microService.webview.DWebView
import info.bagen.dwebbrowser.ui.view.CaptureController
import kotlinx.coroutines.CoroutineScope

data class WebSiteInfo(
  var title: String,
  var url: String,
  val icon: ImageBitmap? = null,
  var timeMillis: String = "",
  var index: Int = 0 // 用于标识位置，方便定位弹出框位置
) {
  //val expand: MutableState<Boolean> = mutableStateOf(false)
}

data class BookWebSiteInfo(
  val webSiteInfo: WebSiteInfo,
  val expand: MutableState<Boolean> = mutableStateOf(false)
)

interface BrowserBaseView {
  val show: MutableState<Boolean> // 用于首页是否显示遮罩
  val focus: MutableState<Boolean> // 用于搜索框显示的内容，根据是否聚焦来判断
  val controller: CaptureController
  var bitmap: ImageBitmap?
}

data class BrowserMainView(
  override val show: MutableState<Boolean> = mutableStateOf(true),
  override val focus: MutableState<Boolean> = mutableStateOf(false),
  override val controller: CaptureController = CaptureController(),
  override var bitmap: ImageBitmap? = null,
) : BrowserBaseView

data class BrowserWebView(
  override val show: MutableState<Boolean> = mutableStateOf(true),
  override val focus: MutableState<Boolean> = mutableStateOf(false),
  override val controller: CaptureController = CaptureController(),
  override var bitmap: ImageBitmap? = null,
  val webView: DWebView,
  val webViewId: String,
  val state: WebViewState,
  val navigator: WebViewNavigator,
  val coroutineScope: CoroutineScope
) : BrowserBaseView

enum class PopupViewState(
  private val height: Dp = 0.dp,
  private val percentage: Float? = null,
  val title: String,
) {
  Options(height = 120.dp, title = "选项"),
  BookList(percentage = 0.9f, title = "书签列表"),
  HistoryList(percentage = 0.9f, title = "历史记录"),
  Share(percentage = 0.5f, title = "分享");

  fun getLocalHeight(screenHeight: Dp? = null): Dp {
    return screenHeight?.let { screenHeight ->
      percentage?.let { percentage ->
        screenHeight * percentage
      }
    } ?: height
  }
}

class TabItem(
  @StringRes val title_res: Int,
  @DrawableRes val icon_res: Int,
  val entry: PopupViewState
) {
  val title @Composable get() = stringResource(id = title_res)
  val icon @Composable get() = ImageVector.vectorResource(id = icon_res)

}

data class HotspotInfo(
  val id: Int = 0,
  val name: String,
  val webUrl: String,
  val iconUrl: String = "",
) {
  fun showHotText(): AnnotatedString {
    val color = when (id) {
      1 -> Color.Red
      2 -> Color(0xFFFF6C2D)
      3 -> Color(0xFFFF6C2D)
      else -> Color.LightGray
    }
    return buildAnnotatedString {
      withStyle(
        style = SpanStyle(
          color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
      ) {
        append("$id".padEnd(5, ' '))
      }
      withStyle(
        style = SpanStyle(
          color = Color.Black,
          fontSize = 16.sp
        )
      ) {
        append(name)
      }
    }
  }
}