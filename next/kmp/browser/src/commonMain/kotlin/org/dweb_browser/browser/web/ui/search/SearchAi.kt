package org.dweb_browser.browser.web.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.dweb_browser.browser.BrowserI18nResource
import org.dweb_browser.browser.web.model.BrowserViewModel

/**
 * AI 搜索
 */
@Composable
internal fun SearchAi(
  viewModel: BrowserViewModel, searchText: String, onDismissRequest: () -> Unit,
) {
  Box(Modifier.fillMaxWidth().heightIn(min = 320.dp), contentAlignment = Alignment.Center) {
    // TODO 接入能访问互联网的AI，让它能搜索当前网页，或者能自己在后台访问当前站点其它的链接
    Column(
      horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(0.6f)
    ) {
      Icon(
        Icons.Default.Engineering,
        contentDescription = "developing",
        modifier = Modifier.size(64.dp),
      )
      Text(
        BrowserI18nResource.browser_search_comingSoon(),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}