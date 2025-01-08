package org.dweb_browser.browser.web.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Diversity3
import androidx.compose.material.icons.twotone.Http
import androidx.compose.material.icons.twotone.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import org.dweb_browser.browser.BrowserI18nResource
import org.dweb_browser.browser.web.model.LocalBrowserViewModel
import org.dweb_browser.browser.web.model.page.BrowserWebPage

internal data class TabInfo(
  val title: String,
  val icon: @Composable () -> Unit,
  val content: @Composable () -> Unit,
)

/**
 * 输入搜索内容后，显示的搜索建议
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSuggestion(
  searchTextState: TextFieldState,
  modifier: Modifier = Modifier,
  onClose: () -> Unit,
) {
  val searchText = searchTextState.text.toString()
  val viewModel = LocalBrowserViewModel.current
  Column(modifier = modifier) {
    val scope = rememberCoroutineScope()
    val currentWebPage = viewModel.focusedPage?.let { if (it is BrowserWebPage) it else null }

    // TODO FIX ME 本地搜索会用到??
    LaunchedEffect(searchText) { viewModel.getInjectList(searchText) }
    val tabs = mutableListOf(
      TabInfo(
        BrowserI18nResource.browser_search_ai(),
        icon = { Icon(Icons.TwoTone.AutoAwesome, "") },
      ) {
        SearchAi(viewModel, searchText, onDismissRequest = onClose)
      },
      TabInfo(
        BrowserI18nResource.browser_search_web2(),
        icon = { Icon(Icons.TwoTone.TravelExplore, "") },
      ) {
        SearchWeb2(
          viewModel,
          searchTextState,
          onDismissRequest = onClose,
        )
      },
      TabInfo(
        BrowserI18nResource.browser_search_web3(),
        icon = { Icon(Icons.TwoTone.Diversity3, "") },
      ) {
        SearchWeb3(viewModel, searchText, onDismissRequest = onClose)
      },
    )
    if (currentWebPage != null) {
      val webPageTabInfo = TabInfo(BrowserI18nResource.browser_search_web_page(), icon = {
        Icon(Icons.TwoTone.Http, "")
      }) {
        SearchWebPageInfo(
          viewModel = viewModel,
          webPage = currentWebPage,
          searchTextState = searchTextState,
          onDismissRequest = onClose,
        )
      }
      tabs.add(1, webPageTabInfo)
    }
    val state = rememberPagerState(1) { tabs.size }

    SecondaryTabRow(
      state.currentPage, Modifier.fillMaxWidth(), containerColor = Color.Transparent
    ) {
      tabs.forEachIndexed { index, tab ->
        Tab(
          selected = state.currentPage == index,
          onClick = { scope.launch { state.scrollToPage(index) } },
          icon = { tab.icon() },
          text = { Text(tab.title) },
        )
      }
    }
    HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) {
      Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart // 内容置顶显示
      ) {
        tabs[it].content()
      }
    }
  }
}

