package org.dweb_browser.browser.web.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivateConnectivity
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.dweb_browser.browser.BrowserI18nResource
import org.dweb_browser.browser.web.data.page.BrowserWebPage
import org.dweb_browser.browser.web.model.LocalBrowserViewModel
import org.dweb_browser.helper.PrivacyUrl

@Composable
internal fun BrowserMenu() {
  val uiScope = rememberCoroutineScope()
  val viewModel = LocalBrowserViewModel.current
  val scope = LocalBrowserViewModel.current.browserNMM.ioAsyncScope

  val hide = remember(viewModel) {
    {
      viewModel.showMore = false
    }
  }

  DropdownMenu(viewModel.showMore, {
    hide()
  }) {
    val page = viewModel.focusPage
    // 添加书签
    if (page is BrowserWebPage) {
      val added = page.isInBookmark
      if (added) {
        SettingListItem(
          BrowserI18nResource.browser_options_addToBook(),
          Icons.Default.BookmarkAdd,
          {
            hide()
            scope.launch {
              viewModel.removeBookmark(page.url)
            }
          })
      } else {
        SettingListItem(BrowserI18nResource.browser_options_addToBook(), Icons.Default.Bookmark, {
          hide()
          scope.launch {
            viewModel.addBookmark(page)
          }
        })
      }
    }
    SettingListItem(
      BrowserI18nResource.browser_bookmark_page_title(), Icons.Default.Bookmark, {
        hide()
        scope.launch { viewModel.tryOpenUrl("about:bookmarks") }
      }, Icons.AutoMirrored.Filled.ArrowForwardIos
    )
    // 分享
    if (page is BrowserWebPage) {
      HorizontalDivider()
      SettingListItem(BrowserI18nResource.browser_options_share(), Icons.Default.Share, {
        hide()
        scope.launch { viewModel.shareWebSiteInfo(page) }
      })
    }

    HorizontalDivider()
    // 无痕浏览
    SettingListItem(
      BrowserI18nResource.browser_options_noTrace(),
      Icons.Default.PrivateConnectivity,
    ) {
      Switch(checked = viewModel.isNoTrace.value, onCheckedChange = {
        uiScope.launch { viewModel.saveBrowserMode(it) }
      }, thumbContent = if (viewModel.isNoTrace.value) {
        {
          Icon(
            imageVector = Icons.Filled.PrivateConnectivity,
            contentDescription = null,
            modifier = Modifier.size(SwitchDefaults.IconSize),
          )
        }
      } else null)
    }

    // 隐私政策
    SettingListItem(
      BrowserI18nResource.browser_options_privacy(), // stringResource(id = R.string.browser_options_privacy),
      Icons.Default.Policy, {
        hide()
        scope.launch {
          viewModel.tryOpenUrl(PrivacyUrl)
        }
      }, Icons.AutoMirrored.Filled.ArrowForwardIos
    )

    HorizontalDivider()
    // 搜索引擎
//        RowItemMenuView(
//          text = BrowserI18nResource.browser_options_search_engine(),
//          trailingIcon = Icons.Default.Settings
//        ) { openEngineManage() }

    // 下载管理界面
    Spacer(modifier = Modifier.height(12.dp))
    SettingListItem(
      BrowserI18nResource.browser_options_download(), Icons.Default.FileDownload, {
        hide()
        scope.launch {
          viewModel.tryOpenUrl("about:downloads")
        }
      }, Icons.AutoMirrored.Filled.ArrowForwardIos
    )
  }
}


@Composable
private fun SettingListItem(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit = {},
  trailingContent: (@Composable (() -> Unit))? = null
) {
  DropdownMenuItem(
    text = { Text(title) },
    onClick = onClick,
    leadingIcon = { Icon(icon, contentDescription = title) },
    trailingIcon = trailingContent
  )
}

@Composable
private fun SettingListItem(
  title: String, icon: ImageVector, onClick: () -> Unit = {}, trailingIcon: ImageVector
) {
  SettingListItem(title, icon, onClick) {
    Icon(trailingIcon, contentDescription = title)
  }
}

/**
 * 管理页通用的标题
 */
@Composable
internal fun BrowserManagerTitle(
  title: String, onBack: () -> Unit, onDone: (() -> Unit)? = null
) {
  Row(
    modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = CenterVertically
  ) {
    Icon(
      imageVector = Icons.Default.ArrowBack,// ImageVector.vectorResource(R.drawable.ic_main_back),
      contentDescription = "Back",
      modifier = Modifier.clickable { onBack() }.padding(horizontal = 16.dp).size(24.dp),
      tint = MaterialTheme.colorScheme.onBackground
    )
    Text(
      text = title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp
    )
    Box(
      modifier = Modifier.clickable { onDone?.let { it() } }.padding(horizontal = 16.dp)
        .width(48.dp), contentAlignment = Center
    ) {
      onDone?.let {
        Text(
          text = BrowserI18nResource.browser_options_store(),
          color = MaterialTheme.colorScheme.primary,
          fontSize = 18.sp
        )
      }
    }
  }
}
