package info.bagen.dwebbrowser.microService.browser.jmm.render

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.dweb_browser.microservice.help.types.JmmAppInstallManifest

/**
 * 应用介绍的图片展示部分
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureListView(
  jmmAppInstallManifest: JmmAppInstallManifest, onSelectPic: (Int, LazyListState) -> Unit
) {
  jmmAppInstallManifest.images?.let { images ->
    val lazyListState = rememberLazyListState()
    LazyRow(
      modifier = Modifier.padding(vertical = VerticalPadding),
      state = lazyListState,
      contentPadding = PaddingValues(horizontal = HorizontalPadding)
    ) {
      itemsIndexed(images) { index, item ->
        Card(
          onClick = { onSelectPic(index, lazyListState) },
          modifier = Modifier
            .padding(end = 16.dp)
            .size(ImageWidth, ImageHeight)
        ) {
          AsyncImage(model = item, contentDescription = null)
        }
      }
    }
  }
}