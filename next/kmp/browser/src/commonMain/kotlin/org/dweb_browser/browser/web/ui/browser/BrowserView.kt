package org.dweb_browser.browser.web.ui.browser

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.Filter1
import androidx.compose.material.icons.rounded.Filter2
import androidx.compose.material.icons.rounded.Filter3
import androidx.compose.material.icons.rounded.Filter4
import androidx.compose.material.icons.rounded.Filter5
import androidx.compose.material.icons.rounded.Filter6
import androidx.compose.material.icons.rounded.Filter7
import androidx.compose.material.icons.rounded.Filter8
import androidx.compose.material.icons.rounded.Filter9
import androidx.compose.material.icons.rounded.Filter9Plus
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dweb_browser.browser.BrowserI18nResource
import org.dweb_browser.browser.util.isSystemUrl
import org.dweb_browser.browser.web.model.BrowserBaseView
import org.dweb_browser.browser.web.model.BrowserWebView
import org.dweb_browser.browser.web.ui.browser.bottomsheet.LocalModalBottomSheet
import org.dweb_browser.browser.web.ui.browser.bottomsheet.ModalBottomModel
import org.dweb_browser.browser.web.ui.browser.bottomsheet.SheetState
import org.dweb_browser.browser.web.ui.browser.model.BrowserViewModel
import org.dweb_browser.browser.web.ui.browser.model.LocalBrowserPageState
import org.dweb_browser.browser.web.ui.browser.model.LocalInputText
import org.dweb_browser.browser.web.ui.browser.model.LocalShowIme
import org.dweb_browser.browser.web.ui.browser.model.LocalShowSearchView
import org.dweb_browser.browser.web.ui.browser.model.LocalWebViewInitialScale
import org.dweb_browser.browser.web.ui.browser.model.parseInputText
import org.dweb_browser.browser.web.ui.browser.search.SearchView
import org.dweb_browser.dwebview.Render
import org.dweb_browser.dwebview.rememberCanGoBack
import org.dweb_browser.dwebview.rememberLoadingProgress
import org.dweb_browser.helper.compose.clickableWithNoEffect
import org.dweb_browser.sys.window.core.WindowRenderScope
import org.dweb_browser.sys.window.render.LocalWindowController

internal val dimenTextFieldFontSize = 16.sp
internal val dimenSearchHorizontalAlign = 5.dp
internal val dimenSearchVerticalAlign = 10.dp
internal val dimenSearchRoundedCornerShape = 8.dp
internal val dimenShadowElevation = 4.dp
internal val dimenHorizontalPagerHorizontal = 20.dp
internal val dimenBottomHeight = 100.dp
internal val dimenSearchHeight = 40.dp
internal val dimenNavigationHeight = 40.dp
internal val dimenMinBottomHeight = 20.dp

private val bottomEnterAnimator = slideInVertically(animationSpec = tween(300),//动画时长1s
  initialOffsetY = {
    it//初始位置在负一屏的位置，也就是说初始位置我们看不到，动画动起来的时候会从负一屏位置滑动到屏幕位置
  })
private val bottomExitAnimator = slideOutVertically(animationSpec = tween(300),//动画时长1s
  targetOffsetY = {
    it//初始位置在负一屏的位置，也就是说初始位置我们看不到，动画动起来的时候会从负一屏位置滑动到屏幕位置
  })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserViewForWindow(
  viewModel: BrowserViewModel, modifier: Modifier, windowRenderScope: WindowRenderScope
) {
  val scope = rememberCoroutineScope()
  val browserPagerState = viewModel.rememberBrowserPagerState()
  val initialScale =
    (LocalDensity.current.density * windowRenderScope.scale * 100).toInt() // 用于WebView缩放，避免点击后位置不对
  val modalBottomModel = remember { ModalBottomModel(mutableStateOf(SheetState.PartiallyExpanded)) }

  LaunchedEffect(browserPagerState) {
    viewModel.onPagerStateChange { page ->
      browserPagerState.pagerStateContent.scrollToPage(page)
      browserPagerState.pagerStateNavigator.scrollToPage(page)
    }
  }

  CompositionLocalProvider(
    LocalModalBottomSheet provides modalBottomModel,
    LocalWebViewInitialScale provides initialScale,
    LocalBrowserPageState provides browserPagerState,
  ) {
    val win = LocalWindowController.current
    win.GoBackHandler {
      val watcher = viewModel.currentTab?.closeWatcher
      if (watcher?.canClose == true) {
        scope.launch {
          watcher.close()
        }
      } else {
        viewModel.currentTab?.viewItem?.webView?.let { webView ->
          if (webView.canGoBack()) {
            webView.goBack()
          }
        }
      }
    }

    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = dimenBottomHeight * windowRenderScope.scale)
      ) {
        BrowserViewContent(viewModel)   // 中间主体部分
      }
      Box(modifier = with(windowRenderScope) {
        Modifier
          .requiredSize((width / scale).dp, (height / scale).dp) // 原始大小
          .scale(scale)
      }) {
        BrowserViewBottomBar(viewModel) // 工具栏，包括搜索框和导航栏
        BrowserMultiPopupView(viewModel)// 用于显示多界面
        BrowserSearchView(viewModel)
        BrowserBottomSheet(viewModel)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserViewContent(viewModel: BrowserViewModel) {
  val localFocusManager = LocalFocusManager.current
  val browserPagerState = LocalBrowserPageState.current
  LaunchedEffect(browserPagerState.pagerStateNavigator.currentPageOffsetFraction) {
    browserPagerState.pagerStateContent.scrollToPage(
      browserPagerState.pagerStateNavigator.currentPage,
      browserPagerState.pagerStateNavigator.currentPageOffsetFraction
    )
  }
  LaunchedEffect(browserPagerState.pagerStateContent.currentPage) {
    viewModel.updateCurrentBrowserView(browserPagerState.pagerStateContent.currentPage)
  }

  Box(modifier = Modifier
    .fillMaxSize()
    .clickableWithNoEffect { localFocusManager.clearFocus() }
  ) {
    HorizontalPager(modifier = Modifier,
      state = browserPagerState.pagerStateContent,
      pageSpacing = 0.dp,
      userScrollEnabled = false,
      reverseLayout = false,
      contentPadding = PaddingValues(0.dp),
      beyondBoundsPageCount = 5,
      pageContent = { currentPage ->
        BrowserViewContentWeb(viewModel, viewModel.getBrowserViewOrNull(currentPage)!!)
      })
  }
}

@Composable
fun ColumnScope.MiniTitle(viewModel: BrowserViewModel) {
  val browserBaseView = viewModel.currentTab
  val inputText = parseInputText(browserBaseView?.viewItem?.webView?.getUrl() ?: "")

  Text(
    text = inputText, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
  )
}

@Composable
private fun BoxScope.BrowserViewBottomBar(viewModel: BrowserViewModel) {
  Box(modifier = Modifier.align(Alignment.BottomCenter)) {
    Column(modifier = Modifier.fillMaxWidth()) {
      BrowserViewSearch(viewModel)
      BrowserViewNavigatorBar(viewModel)
    }
    // 小标题暂时不需要，先屏蔽
    /*Column(modifier = Modifier
      .fillMaxWidth()
      .height(dimenMinBottomHeight)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .align(Alignment.BottomCenter)
      .clickable { viewModel.handleIntent(BrowserIntent.UpdateBottomViewState(true)) }) {
      MiniTitle(viewModel)
    }

    AnimatedVisibility(
      visibleState = viewModel.uiState.showBottomBar,
      enter = bottomEnterAnimator,
      exit = bottomExitAnimator,
      modifier = Modifier.background(MaterialTheme.colorScheme.background) // surfaceVariant
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        BrowserViewSearch(viewModel)
        BrowserViewNavigatorBar(viewModel)
      }
    }*/
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserViewSearch(viewModel: BrowserViewModel) {
  val pagerStateNavigator = LocalBrowserPageState.current.pagerStateNavigator
  val localShowIme = LocalShowIme.current

  LaunchedEffect(pagerStateNavigator.settledPage) { // 为了修复隐藏搜索框后，重新加载时重新显示的问题，会显示第一页
    delay(100)
    pagerStateNavigator.scrollToPage(pagerStateNavigator.settledPage)
  }
  val localFocus = LocalFocusManager.current
  LaunchedEffect(Unit) {
    if (!localShowIme.value && !viewModel.showSearchEngine.targetState) {
      localFocus.clearFocus()
    }
  }

  // 增加判断是否有传入需要检索的内容，如果有，就进行显示搜索界面
  val showSearchView = LocalShowSearchView.current
  LaunchedEffect(showSearchView) {
    snapshotFlow { viewModel.dwebLinkSearch.value }.collect {
      showSearchView.value = it.isNotEmpty()
    }
  }

  HorizontalPager(modifier = Modifier,
    state = pagerStateNavigator,
    pageSpacing = 0.dp,
    userScrollEnabled = true,
    reverseLayout = false,
    contentPadding = PaddingValues(horizontal = dimenHorizontalPagerHorizontal),
    beyondBoundsPageCount = 0,
    pageContent = { currentPage ->
      SearchBox(viewModel.getBrowserViewOrNull(currentPage)!!)
    })
}

@Composable
private fun BrowserViewNavigatorBar(viewModel: BrowserViewModel) {
  val scope = rememberCoroutineScope()
  val bottomSheetModel = LocalModalBottomSheet.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(dimenNavigationHeight)
  ) {
    val webView = viewModel.currentTab?.viewItem?.webView ?: return
    val canGoBack = webView.rememberCanGoBack()

    NavigatorButton(
      imageVector = Icons.Rounded.AddHome,
      name = "AddHome",
      show = webView.hasUrl()
    ) {
      scope.launch { viewModel.addUrlToDesktop() }
    }
    NavigatorButton(
      imageVector = if (canGoBack) Icons.Rounded.Add else Icons.Rounded.QrCodeScanner,
      name = if (canGoBack) "Add" else "Scan",
      show = true
    ) {
      scope.launch {
        if (canGoBack) {
          viewModel.addNewMainView()
        } else {
          viewModel.openQRCodeScanning()
        }
      }
    }
    NavigatorButton(
      imageVector = getMultiImageVector(viewModel.listSize), // resId = R.drawable.ic_main_multi,
      name = "MultiView", show = true
    ) {
      scope.launch { viewModel.updateMultiViewState(true) }
    }
    NavigatorButton(
      imageVector = Icons.Rounded.Menu, name = "Options", show = true
    ) {
      scope.launch { bottomSheetModel.show() }
    }
  }
}

private fun getMultiImageVector(size: Int) = when (size) {
  1 -> Icons.Rounded.Filter1
  2 -> Icons.Rounded.Filter2
  3 -> Icons.Rounded.Filter3
  4 -> Icons.Rounded.Filter4
  5 -> Icons.Rounded.Filter5
  6 -> Icons.Rounded.Filter6
  7 -> Icons.Rounded.Filter7
  8 -> Icons.Rounded.Filter8
  9 -> Icons.Rounded.Filter9
  else -> Icons.Rounded.Filter9Plus
}

@Composable
private fun RowScope.NavigatorButton(
  imageVector: ImageVector, name: String, show: Boolean, onClick: () -> Unit
) {
  Box(modifier = Modifier
    .weight(1f)
    .fillMaxHeight()
    .padding(horizontal = 2.dp)
    .clickable(enabled = show) { onClick() }) {
    Column(modifier = Modifier.align(Alignment.Center)) {
      Icon(
        modifier = Modifier.size(28.dp),
        imageVector = imageVector, //ImageVector.vectorResource(id = resId),//ImageBitmap.imageResource(id = resId),
        contentDescription = name,
        tint = if (show) {
          MaterialTheme.colorScheme.onSecondaryContainer
        } else {
          MaterialTheme.colorScheme.outlineVariant
        }
      )
    }
  }
}

@Composable
private fun BrowserViewContentWeb(viewModel: BrowserViewModel, browserWebView: BrowserWebView) {
  key(browserWebView.viewItem.webviewId) {
    BrowserWebView(viewModel = viewModel, browserWebView = browserWebView)
  }
}

@Composable
private fun SearchBox(baseView: BrowserBaseView) {
  var showSearchView by LocalShowSearchView.current
  val searchHint = BrowserI18nResource.browser_search_hint()

  Box(modifier = Modifier
    .padding(
      horizontal = dimenSearchHorizontalAlign, vertical = dimenSearchVerticalAlign
    )
    .fillMaxWidth()
    .shadow(
      elevation = dimenShadowElevation, shape = RoundedCornerShape(dimenSearchRoundedCornerShape)
    )
    .height(dimenSearchHeight)
    .clip(RoundedCornerShape(dimenSearchRoundedCornerShape))
    .background(MaterialTheme.colorScheme.surface)
    .clickable {
      showSearchView = true;
    }) {
    val inputText = when (baseView) {
      is BrowserWebView -> {
        ShowLinearProgressIndicator(baseView)
        mutableStateOf(baseView.viewItem.webView.getUrl())
      }

      else -> mutableStateOf("")
    }
    val search = if (inputText.value.isEmpty() || inputText.value.isSystemUrl()) {
      Triple(searchHint, TextAlign.Start, Icons.Default.Search)
    } else {
      Triple(
        parseInputText(inputText.value), TextAlign.Center, Icons.Default.FormatSize
      )
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp)
        .align(Alignment.Center),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(search.third, contentDescription = "Search")
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = search.first,
        textAlign = search.second,
        fontSize = dimenTextFieldFontSize,
        maxLines = 1,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

/**
 * 用于显示 WebView 加载进度
 */
@Composable
private fun BoxScope.ShowLinearProgressIndicator(browserWebView: BrowserWebView?) {
  browserWebView?.let {
    when (val loadingProgress = it.viewItem.webView.rememberLoadingProgress()) {
      0f, 1f -> {}
      else -> {
        LinearProgressIndicator(
          progress = loadingProgress,
          modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .align(Alignment.BottomCenter),
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

/**
 * 提供给外部调用的  搜索界面，可以含有BrowserViewModel
 */
@Composable
fun BrowserSearchView(viewModel: BrowserViewModel) {
  val scope = rememberCoroutineScope()
  var showSearchView by LocalShowSearchView.current
  val searchHint = BrowserI18nResource.browser_search_hint()
  if (showSearchView) {
    val inputText = viewModel.dwebLinkSearch.value.ifEmpty {
      viewModel.currentTab?.viewItem?.webView?.getUrl() ?: ""
    }
    val text =
      if (inputText.isSystemUrl() || inputText == searchHint) {
        ""
      } else {
        inputText
      }

    val inputTextState = LocalInputText.current

    SearchView(text = text, homePreview = { onMove ->
      HomeWebviewPage(viewModel, onMove)
    }, onClose = {
      showSearchView = false
    }, onSearch = { url -> // 第一个是搜索关键字，第二个是搜索地址
      scope.launch {
        showSearchView = false
        viewModel.saveLastKeyword(inputTextState, url)
        viewModel.searchWebView(url)
      }
    })
  }
}

@Composable
internal fun HomeWebviewPage(viewModel: BrowserViewModel, onClickOrMove: (Boolean) -> Unit) {
  var _webView by remember {
    mutableStateOf<BrowserWebView?>(null)
  }
  LaunchedEffect(Unit) {
    _webView = viewModel.searchBackBrowserView.await()
  }
  val webView = _webView ?: return
  val background = MaterialTheme.colorScheme.background
  webView.viewItem.webView.Render(
    Modifier
      .fillMaxSize()
      .background(background),
  )
}