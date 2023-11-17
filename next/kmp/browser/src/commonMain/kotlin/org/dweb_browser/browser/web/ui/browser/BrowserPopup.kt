package org.dweb_browser.browser.web.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Alignment.Companion.TopStart
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dweb_browser.browser.BrowserI18nResource
import org.dweb_browser.browser.util.isSystemUrl
import org.dweb_browser.browser.web.model.BrowserBaseView
import org.dweb_browser.browser.web.model.BrowserMainView
import org.dweb_browser.browser.web.model.BrowserWebView
import org.dweb_browser.browser.web.model.WebSiteInfo
import org.dweb_browser.browser.web.model.WebSiteType
import org.dweb_browser.browser.web.ui.browser.bottomsheet.BrowserModalBottomSheet
import org.dweb_browser.browser.web.ui.browser.bottomsheet.LocalModalBottomSheet
import org.dweb_browser.browser.web.ui.browser.bottomsheet.SheetState
import org.dweb_browser.browser.web.ui.browser.model.BrowserViewModel
import org.dweb_browser.browser.web.ui.browser.model.toWebSiteInfo
import org.dweb_browser.browser.web.ui.browser.search.CustomTextField
import org.dweb_browser.helper.PrivacyUrl
import org.dweb_browser.helper.compose.rememberScreenSize
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.platform.getCornerRadiusTop
import org.dweb_browser.helper.platform.rememberPureViewBox
import org.dweb_browser.helper.platform.theme.DimenBottomBarHeight
import org.dweb_browser.sys.window.render.LocalWindowController

enum class PopupViewState(
  private val height: Dp = 0.dp,
  private val percentage: Float? = null,
  val title: String,
  val imageVector: ImageVector,
  val index: Int,
) {
  Options(height = 120.dp, title = "选项", imageVector = Icons.Default.Menu, index = 0),
  BookList(percentage = 0.9f, title = "书签列表", imageVector = Icons.Default.Book, index = 1),
  HistoryList(
    percentage = 0.9f,
    title = "历史记录",
    imageVector = Icons.Default.History,
    index = 2
  ),
  // Share(percentage = 0.5f, title = "分享"),
  ;

  fun getLocalHeight(screenHeight: Dp? = null): Dp {
    return screenHeight?.let {
      percentage?.let { percentage ->
        screenHeight * percentage
      }
    } ?: height
  }
}

@Composable
internal fun BrowserBottomSheet(viewModel: BrowserViewModel) {
  val bottomSheetModel = LocalModalBottomSheet.current
  val scope = rememberCoroutineScope()
  val win = LocalWindowController.current

  if (bottomSheetModel.show.value) {
    win.GoBackHandler {
      if (bottomSheetModel.state.value != SheetState.Hidden) {
        scope.launch { bottomSheetModel.hide() }
      }
    }

    val density = LocalDensity.current.density
    val topLeftRadius = getCornerRadiusTop(rememberPureViewBox(), density, 16f)
    BrowserModalBottomSheet(
      onDismissRequest = { scope.launch { bottomSheetModel.hide() } },
      shape = RoundedCornerShape(
        topStart = topLeftRadius * density,
        topEnd = topLeftRadius * density
      )
    ) {
      BrowserPopView(viewModel)
    }
  }
}

/**
 * 弹出主界面，包括了三个tab和一个书签管理界面 TODO 目前缺少切换到书签管理界面后的展开问题
 */
@Composable
internal fun BrowserPopView(viewModel: BrowserViewModel) {
  val selectedTabIndex = LocalModalBottomSheet.current.tabIndex
  val pageIndex = LocalModalBottomSheet.current.pageIndex
  val webSiteInfo = LocalModalBottomSheet.current.webSiteInfo

  AnimatedContent(targetState = pageIndex, label = "",
    transitionSpec = {
      if (targetState.intValue > initialState.intValue) {
        // 数字变大时，进入的界面从右向左变深划入，退出的界面从右向左变浅划出
        (slideInHorizontally { fullWidth -> fullWidth } + fadeIn()).togetherWith(
          slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut())
      } else {
        // 数字变小时，进入的数字从左向右变深划入，退出的数字从左向右变浅划出
        (slideInHorizontally { fullWidth -> -fullWidth } + fadeIn()).togetherWith(
          slideOutHorizontally { fullWidth -> fullWidth } + fadeOut())
      }
    }
  ) { targetPage ->
    when (targetPage.intValue) {
      0 -> {
        PopTabRowContent(
          viewModel = viewModel,
          selectedTabIndex = selectedTabIndex,
          openBookManager = {
            webSiteInfo.value = it
            pageIndex.intValue = 1
          }
        )
      }

      1 -> {
        PopBookManagerView(viewModel) { pageIndex.intValue = 0 }
      }

      else -> {}
    }
  }
}

/**
 * 书签管理界面
 */
@Composable
private fun PopBookManagerView(viewModel: BrowserViewModel, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val webSiteInfo = LocalModalBottomSheet.current.webSiteInfo
  val inputTitle = remember { mutableStateOf(webSiteInfo.value?.title ?: "") }
  val inputUrl = remember { mutableStateOf(webSiteInfo.value?.url ?: "") }
  Box(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp), verticalAlignment = CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.ArrowBack,// ImageVector.vectorResource(R.drawable.ic_main_back),
        contentDescription = "Back",
        modifier = Modifier
          .clickable { onBack() }
          .padding(horizontal = 16.dp)
          .size(22.dp),
        tint = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = BrowserI18nResource.browser_options_editBook(),
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.Center,
        fontSize = 18.sp
      )
      Text(
        text = BrowserI18nResource.browser_options_store(),
        modifier = Modifier
          .clickable {
            webSiteInfo.value?.apply {
              title = inputTitle.value
              url = inputUrl.value
              scope.launch(ioAsyncExceptionHandler) {
                viewModel.changeBookLink()
              }
              onBack()
            }
          }
          .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 18.sp
      )
    }
    val item = webSiteInfo.value ?: return
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
      delay(100)
      focusRequester.requestFocus()
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 56.dp)
    ) {
      RowItemTextField(
        leadingBitmap = item.icon,
        leadingIcon = Icons.Default.Book,
        inputText = inputTitle,
        focusRequester = focusRequester
      )
      Spacer(modifier = Modifier.height(16.dp))
      RowItemTextField(leadingIcon = Icons.Default.Link, inputText = inputUrl)
      Spacer(modifier = Modifier.height(16.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .clickable {
            scope.launch(ioAsyncExceptionHandler) {
              viewModel.changeBookLink(del = item)
              onBack()
            }
          },
        contentAlignment = Center
      ) {
        Text(
          text = BrowserI18nResource.browser_options_delete(),
          color = MaterialTheme.colorScheme.error,
          fontSize = 16.sp,
          fontWeight = FontWeight(400)
        )
      }
    }
  }
}

@Composable
fun RowItemTextField(
  leadingBitmap: ImageBitmap? = null,
  leadingIcon: ImageVector,
  inputText: MutableState<String>,
  focusRequester: FocusRequester? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(50.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surface),
    verticalAlignment = CenterVertically
  ) {
    val modifier = focusRequester?.let { Modifier.focusRequester(focusRequester) } ?: Modifier

    CustomTextField(
      value = inputText.value,
      onValueChange = { inputText.value = it },
      modifier = modifier,
      spacerWidth = 0.dp,
      leadingIcon = {
        leadingBitmap?.let {
          Image(
            bitmap = it,
            contentDescription = "Icon",
            modifier = Modifier
              .padding(horizontal = 12.dp, vertical = 11.dp)
              .size(28.dp)
          )
        } ?: run {
          Icon(
            imageVector = leadingIcon,
            contentDescription = "Icon",
            modifier = Modifier
              .padding(horizontal = 12.dp, vertical = 11.dp)
              .size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    )
  }
}

/**
 * 三个标签页主界面
 */
@Composable
private fun PopTabRowContent(
  viewModel: BrowserViewModel,
  selectedTabIndex: MutableState<PopupViewState>,
  openBookManager: (WebSiteInfo) -> Unit
) {
  val popupViewState = remember { mutableStateOf(PopupViewState.Options) }

  LaunchedEffect(selectedTabIndex) {
    snapshotFlow { selectedTabIndex.value }.collect {
      popupViewState.value = it
    }
  }

  Column {
    TabRow(
      selectedTabIndex = selectedTabIndex.value.index,
      containerColor = MaterialTheme.colorScheme.background,
      divider = {}
    ) {
      PopupViewState.values().forEachIndexed { index, tabItem ->
        Tab(
          selected = selectedTabIndex.value == tabItem,
          onClick = { selectedTabIndex.value = tabItem },
          icon = {
            Icon(
              imageVector = tabItem.imageVector,
              contentDescription = tabItem.title,
              modifier = Modifier.size(24.dp)
            )
          },
        )
      }
    }
    PopContentView(popupViewState, viewModel, openBookManager)
  }
}

// 显示具体内容部分，其中又可以分为三个部分类型，操作页，书签列表，历史列表
@Composable
private fun PopContentView(
  popupViewState: MutableState<PopupViewState>,
  viewModel: BrowserViewModel,
  openBookManager: (WebSiteInfo) -> Unit
) {
  val scope = rememberCoroutineScope()
  val bottomSheetModel = LocalModalBottomSheet.current

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    when (popupViewState.value) {
      PopupViewState.BookList -> BrowserListOfBook(viewModel,
        onOpenSetting = { openBookManager(it) },
        onSearch = {
          scope.launch {
            bottomSheetModel.hide()
            viewModel.searchWebView(it)
          }
        }
      )

      PopupViewState.HistoryList -> BrowserListOfHistory(viewModel) {
        scope.launch {
          bottomSheetModel.hide()
          viewModel.searchWebView(it)
        }
      }

      else -> PopContentOptionItem(viewModel)
    }
  }
}

@Composable
private fun PopContentOptionItem(viewModel: BrowserViewModel) {
  val scope = rememberCoroutineScope()
  val bottomSheetModel = LocalModalBottomSheet.current
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        RowItemMenuView(
          text = BrowserI18nResource.browser_options_addToBook(), // stringResource(id = R.string.browser_options_book),
          trailingIcon = Icons.Default.Book
        ) {
          viewModel.currentTab?.let {
            scope.launch {
              viewModel.changeBookLink(add = it.viewItem.webView.toWebSiteInfo(WebSiteType.Book))
            }
          }
        } // 添加书签

        Spacer(modifier = Modifier.height(12.dp))
        RowItemMenuView(
          text = BrowserI18nResource.browser_options_share(),
          trailingIcon = Icons.Default.Share
        ) {
          scope.launch { viewModel.shareWebSiteInfo() }
        } // 分享

        Spacer(modifier = Modifier.height(12.dp))
        RowItemMenuView(
          text = BrowserI18nResource.browser_options_noTrace(),
          trailingContent = { modifier ->
            Switch(
              modifier = modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .size(width = 50.dp, height = 30.dp),
              checked = viewModel.isNoTrace.value,
              onCheckedChange = {
                scope.launch { viewModel.saveBrowserMode(it) }
              }
            )
          }) {} // 无痕浏览

        Spacer(modifier = Modifier.height(12.dp))
        RowItemMenuView(
          text = BrowserI18nResource.browser_options_privacy(), // stringResource(id = R.string.browser_options_privacy),
          trailingContent = { modifier ->
            Icon(
              imageVector = Icons.Default.ExpandMore, // ImageVector.vectorResource(R.drawable.ic_more),
              contentDescription = "Manager",
              modifier = modifier
                .padding(horizontal = 12.dp, vertical = 15.dp)
                .size(20.dp)
                .graphicsLayer(rotationZ = -90f),
              tint = MaterialTheme.colorScheme.outlineVariant
            )
          }
        ) {
          scope.launch {
            bottomSheetModel.hide()
            viewModel.searchWebView(PrivacyUrl)
          }
        } // 隐私政策
      }
    }
  }
}

@Composable
private fun RowItemMenuView(
  text: String,
  trailingIcon: ImageVector? = null,
  trailingContent: (@Composable (Modifier) -> Unit)? = null,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(50.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surface)
      .clickable { onClick() }
  ) {
    Text(
      text = text,
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.CenterStart)
        .padding(start = 16.dp, end = 52.dp),
      textAlign = TextAlign.Start,
      fontSize = 16.sp,
      fontWeight = FontWeight(400),
      color = MaterialTheme.colorScheme.onSurface
    )

    trailingIcon?.let { icon ->
      Icon(
        imageVector = icon,
        contentDescription = "Icon",
        modifier = Modifier
          .align(CenterEnd)
          .padding(horizontal = 12.dp, vertical = 11.dp)
          .size(28.dp),
        tint = MaterialTheme.colorScheme.onSurface
      )
    } ?: trailingContent?.let { view -> view(Modifier.align(CenterEnd)) }
  }
}

/**
 * 显示多视图窗口
 */
@Composable
internal fun BrowserMultiPopupView(viewModel: BrowserViewModel) {
  val scope = rememberCoroutineScope()
  val win = LocalWindowController.current
  AnimatedVisibility(visibleState = viewModel.showMultiView) {
    win.GoBackHandler {
      if (viewModel.showMultiView.targetState) {
        viewModel.updateMultiViewState(false)
      }
    }
    // 高斯模糊做背景
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
      //if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
      viewModel.currentTab?.bitmap?.let { bitmap ->
        Image(
          bitmap = bitmap,
          contentDescription = "BackGround",
          alignment = TopStart,
          contentScale = ContentScale.FillWidth,
          modifier = Modifier
            .fillMaxSize()
            .blur(radius = 16.dp)
        )
        //}
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .clickable(enabled = false) {}
    ) {
      if (viewModel.listSize == 1) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          Box(
            modifier = Modifier
              .align(TopCenter)
              .padding(top = 20.dp)
          ) {
            MultiItemView(viewModel, viewModel.getBrowserViewOrNull(0)!!, true)
          }
        }
      } else {
        val lazyGridState = rememberLazyGridState()
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          modifier = Modifier.weight(1f),
          state = lazyGridState,
          contentPadding = PaddingValues(vertical = 20.dp, horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
          items(viewModel.listSize) {
            MultiItemView(viewModel, viewModel.getBrowserViewOrNull(it)!!, index = it)
          }
        }
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(DimenBottomBarHeight)
          .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Add, // ImageVector.vectorResource(id = R.drawable.ic_main_add),
          contentDescription = "Add",
          modifier = Modifier
            .padding(start = 8.dp, end = 8.dp)
            .size(28.dp)
            .align(CenterVertically)
            .clickable { scope.launch { viewModel.addNewMainView() } },
          tint = MaterialTheme.colorScheme.primary,
        )
        val content = BrowserI18nResource.browser_multi_count()
        Text(
          text = "${viewModel.listFilter()} $content",
          modifier = Modifier.weight(1f),
          textAlign = TextAlign.Center
        )
        val done = BrowserI18nResource.browser_multi_done()
        Text(
          text = done,
          modifier = Modifier
            .padding(start = 8.dp, end = 8.dp)
            .clickable { scope.launch { viewModel.updateMultiViewState(false) } },
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MultiItemView(
  viewModel: BrowserViewModel,
  browserBaseView: BrowserBaseView,
  onlyOne: Boolean = false,
  index: Int = 0
) {
  // 未解决
  // val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val screenSize = rememberScreenSize()
  val scope = rememberCoroutineScope()
  val sizeTriple = if (onlyOne) {
    val with = screenSize.screenWidth.dp - 120.dp
    Triple(with, with * 9 / 6 - 60.dp, with * 9 / 6)
  } else {
    val with = (screenSize.screenWidth.dp - 60.dp) / 2
    Triple(with, with * 9 / 6 - 40.dp, with * 9 / 6)
  }
  Box(modifier = Modifier.size(width = sizeTriple.first, height = sizeTriple.third)) {
    Column(horizontalAlignment = CenterHorizontally) {
      Image(
        painter = browserBaseView.bitmap?.let {
          remember(it) {
            BitmapPainter(it, filterQuality = FilterQuality.Medium)
          }
        } ?: rememberVectorPainter(Icons.Default.BrokenImage),
        contentDescription = null,
        modifier = Modifier
          .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp))
          .size(width = sizeTriple.first, height = sizeTriple.second)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surface)
          .clickable { scope.launch { viewModel.updateMultiViewState(false, index) } }
          .align(CenterHorizontally),
        contentScale = ContentScale.FillWidth, //ContentScale.FillBounds,
        alignment = if (browserBaseView is BrowserMainView) Center else TopStart
      )
      // var contentPair: Pair<String?, ImageBitmap?> by remember { mutableStateOf(Pair(null, null)) }
      var contentPair: Pair<String?, ImageVector?> by remember { mutableStateOf(Pair(null, null)) }
      val homePageTitle = BrowserI18nResource.browser_multi_startup()
      val homePageIcon =
        Icons.Default.Star //ImageBitmap.imageResource(context.resources, R.drawable.ic_main_star)
      LaunchedEffect(browserBaseView) {
        when (browserBaseView) {
          is BrowserMainView -> {
            contentPair = Pair(
              homePageTitle,
              homePageIcon
            )
          }

          is BrowserWebView -> {
            contentPair = if (browserBaseView.viewItem.webView.getUrl().isSystemUrl()) {
              Pair(
                homePageTitle,
                homePageIcon
              )
            } else {
              Pair(
                browserBaseView.viewItem.webView.getTitle(),
                //browserBaseView.viewItem.webView.getIconBitmap() // 图片渲染问题
                null
              )
            }
          }
        }
      }
      Row(
        modifier = Modifier
          .width(sizeTriple.first)
          .align(CenterHorizontally)
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = CenterVertically
      ) {
        contentPair.second?.let { imageBitmap ->
          /*Image(
            bitmap = imageBitmap, contentDescription = null, modifier = Modifier.size(12.dp)
          )*/
          Icon(imageBitmap, contentDescription = null, modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(2.dp))
        }
        Text(
          text = contentPair.first ?: BrowserI18nResource.browser_multi_no_title(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 12.sp
        )
      }
    }

    if (!onlyOne || browserBaseView is BrowserWebView) {
      Image(
        imageVector = Icons.Default.Close, //ImageVector.vectorResource(R.drawable.ic_circle_close),
        contentDescription = "Close",
        modifier = Modifier
          .padding(8.dp)
          .clip(CircleShape)
          .align(Alignment.TopEnd)
          .clickable {
            scope.launch { viewModel.removeBrowserWebView(browserBaseView as BrowserWebView) }
          }
          .size(20.dp)
      )
    }
  }
}