package info.bagen.dwebbrowser.ui.browser

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import com.google.accompanist.web.LoadingState
import info.bagen.dwebbrowser.R
import info.bagen.dwebbrowser.ui.entity.BrowserBaseView
import info.bagen.dwebbrowser.ui.entity.BrowserWebView
import info.bagen.dwebbrowser.ui.qrcode.QRCodeScanView
import info.bagen.dwebbrowser.ui.view.PermissionSingleView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserView(viewModel: BrowserViewModel) {
  val scope = rememberCoroutineScope()
  BackHandler {
    if (viewModel.uiState.bottomSheetScaffoldState.bottomSheetState.isVisible) {
      scope.launch {
        viewModel.uiState.bottomSheetScaffoldState.bottomSheetState.hide()
      }
    } else {
      val browserWebView = viewModel.uiState.currentBrowserBaseView.value
      if (browserWebView.navigator.canGoBack) {
        browserWebView.navigator.navigateBack()
      }
    }
  }

  BottomSheetScaffold(modifier = Modifier.navigationBarsPadding(),
    scaffoldState = viewModel.uiState.bottomSheetScaffoldState,
    sheetPeekHeight = LocalConfiguration.current.screenHeightDp.dp / 2,
    sheetContent = {
      Box(modifier = Modifier.navigationBarsPadding()) {
        BrowserPopView(viewModel)       // 用于处理弹出框
      }
    }) {
    Box(
      modifier = Modifier
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      BrowserViewContent(viewModel)   // 中间主体部分
      // BrowserSearchPreview(viewModel) // 地址栏输入内容后，上面显示的书签、历史和相应搜索引擎
      BrowserViewBottomBar(viewModel) // 工具栏，包括搜索框和导航栏
      // BrowserPopView(viewModel)    // 用于处理弹出框
      BrowserMultiPopupView(viewModel)// 用于显示多界面
      BrowserSearchView(viewModel)
    }
    // 增加扫码的界面
    QRCodeScanView(
      qrCodeScanState = viewModel.uiState.qrCodeScanState,
      onDataCallback = { data ->
        if (data.startsWith("http://") || data.startsWith("https://")) {
          viewModel.handleIntent(BrowserIntent.SearchWebView(data))
        } else {
          viewModel.handleIntent(BrowserIntent.ShowSnackbarMessage("扫码结果：$data"))
        }
      })
    if (viewModel.uiState.bottomSheetScaffoldState.bottomSheetState.isVisible) {
      Box(modifier = Modifier
        .fillMaxSize()
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() }) {
          scope.launch {
            viewModel.uiState.bottomSheetScaffoldState.bottomSheetState.hide()
          }
        })
    }
  }
  LaunchedEffect(Unit) { // TODO 这个是因为华为鸿蒙系统，运行后，半屏显示了Sheet，这边强制隐藏下
    scope.launch {
      delay(15)
      viewModel.uiState.bottomSheetScaffoldState.bottomSheetState.hide()
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserViewContent(viewModel: BrowserViewModel) {
  val localFocusManager = LocalFocusManager.current
  LaunchedEffect(viewModel.uiState.pagerStateNavigator) {
    snapshotFlow { viewModel.uiState.pagerStateNavigator.currentPageOffsetFraction }.collect { currentPageOffset ->
      viewModel.uiState.pagerStateContent.scrollToPage(
        viewModel.uiState.pagerStateNavigator.currentPage, currentPageOffset
      )
    }
  }
  LaunchedEffect(viewModel.uiState.pagerStateContent) {
    snapshotFlow { viewModel.uiState.pagerStateContent.currentPage }.collect { currentPage ->
      viewModel.handleIntent(BrowserIntent.UpdateCurrentBaseView(currentPage))
    }
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .clickable(indication = null,
        onClick = { localFocusManager.clearFocus() },
        interactionSource = remember { MutableInteractionSource() })
  ) {
    // 创建一个不可滑动的 HorizontalPager , 然后由底下的 Search 来控制滑动效果
    HorizontalPager(
      state = viewModel.uiState.pagerStateContent,
      pageCount = viewModel.uiState.browserViewList.size,
      beyondBoundsPageCount = 5,
      userScrollEnabled = false
    ) { currentPage ->
      BrowserViewContentWeb(viewModel, viewModel.uiState.browserViewList[currentPage])
      /*when (val item = viewModel.uiState.browserViewList[currentPage]) {
        is BrowserMainView -> BrowserViewContentMain(viewModel, item)
        is BrowserWebView -> BrowserViewContentWeb(viewModel, item)
      }*/
    }
  }
}

@Composable
fun ColumnScope.MiniTitle(viewModel: BrowserViewModel) {
  val browserBaseView = viewModel.uiState.currentBrowserBaseView.value
  val inputText = parseInputText(browserBaseView.state.lastLoadedUrl ?: "")

  Text(
    text = inputText, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
  )
}

@Composable
private fun BoxScope.BrowserViewBottomBar(viewModel: BrowserViewModel) {
  Box(modifier = Modifier.align(Alignment.BottomCenter)) {
    Column(modifier = Modifier
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
      modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        BrowserViewSearch(viewModel)
        BrowserViewNavigatorBar(viewModel)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserViewSearch(viewModel: BrowserViewModel) {
  LaunchedEffect(PagerState) { // 为了修复隐藏搜索框后，重新加载时重新显示的问题，会显示第一页
    delay(100)
    viewModel.uiState.pagerStateNavigator.scrollToPage(viewModel.uiState.pagerStateNavigator.settledPage)
  }
  val localFocus = LocalFocusManager.current
  LaunchedEffect(viewModel.isShowKeyboard) {
    snapshotFlow { viewModel.isShowKeyboard }.collect {
      if (!it && !viewModel.uiState.showSearchEngine.targetState) {
        localFocus.clearFocus()
      }
    }
  }
  HorizontalPager(
    state = viewModel.uiState.pagerStateNavigator,
    pageCount = viewModel.uiState.browserViewList.size,
    contentPadding = PaddingValues(horizontal = dimenHorizontalPagerHorizontal),
  ) { currentPage ->
    SearchBox(viewModel, viewModel.uiState.browserViewList[currentPage])
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserViewNavigatorBar(viewModel: BrowserViewModel) {
  val scope = rememberCoroutineScope()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(dimenNavigationHeight)
  ) {
    val navigator = viewModel.uiState.currentBrowserBaseView.value.navigator
    NavigatorButton(
      resId = R.drawable.ic_main_back,
      resName = R.string.browser_nav_back,
      show = navigator.canGoBack
    ) { navigator.navigateBack() }
    NavigatorButton(
      resId = R.drawable.ic_main_forward,
      resName = R.string.browser_nav_forward,
      show = navigator.canGoForward ?: false
    ) { navigator.navigateForward() }
    NavigatorButton(
      resId = if (navigator.canGoBack) R.drawable.ic_main_add else R.drawable.ic_main_qrcode_scan,
      resName = if (navigator.canGoBack) R.string.browser_nav_add else R.string.browser_nav_scan,
      show = true
    ) {
      if (navigator.canGoBack) {
        viewModel.handleIntent(BrowserIntent.AddNewMainView)
      } else {
        viewModel.uiState.qrCodeScanState.show()
      }
    }
    NavigatorButton(
      resId = R.drawable.ic_main_multi, resName = R.string.browser_nav_multi, show = true
    ) {
      viewModel.handleIntent(BrowserIntent.UpdateMultiViewState(true))
    }
    NavigatorButton(
      resId = R.drawable.ic_main_option, resName = R.string.browser_nav_option, show = true
    ) {
      scope.launch {
        viewModel.uiState.bottomSheetScaffoldState.bottomSheetState.show()
      }
    }
  }
}

@Composable
private fun RowScope.NavigatorButton(
  @DrawableRes resId: Int, @StringRes resName: Int, show: Boolean, onClick: () -> Unit
) {
  Box(modifier = Modifier
    .weight(1f)
    .fillMaxHeight()
    .padding(horizontal = 2.dp)
    .clickable(enabled = show) { onClick() }) {
    Column(modifier = Modifier.align(Alignment.Center)) {
      Icon(
        modifier = Modifier.size(28.dp),
        imageVector = ImageVector.vectorResource(id = resId),//ImageBitmap.imageResource(id = resId),
        contentDescription = stringResource(id = resName),
        tint = if (show) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
      )
    }
  }
}

@Composable
private fun BrowserViewContentWeb(viewModel: BrowserViewModel, browserWebView: BrowserWebView) {
  key(browserWebView.webViewId) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = if (viewModel.uiState.showBottomBar.currentState) dimenBottomHeight else dimenHorizontalPagerHorizontal)
    ) {
      BrowserWebView(viewModel = viewModel, browserWebView = browserWebView)
    }
  }
}

@SuppressLint("UnrememberedMutableState")
@Composable
private fun SearchBox(
  viewModel: BrowserViewModel,
  baseView: BrowserBaseView,
) {
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
    .background(MaterialTheme.colorScheme.background)
    .clickable {
      viewModel.uiState.showSearchView.value = true
    }) {
    val inputText = when (baseView) {
      is BrowserWebView -> {
        ShowLinearProgressIndicator(baseView)
        mutableStateOf(baseView.state.lastLoadedUrl ?: "")
      }
      else -> mutableStateOf("")
    }
    //SearchTextField(viewModel, inputText, baseView.focus)
    val search =
      if (inputText.value.isEmpty() || inputText.value.startsWith("file:///android_asset/dweb/")) {
        Triple(
          stringResource(id = R.string.browser_search_hint),
          TextAlign.Start,
          Icons.Default.Search
        )
      } else {
        Triple(
          parseInputText(inputText.value) ?: inputText.value,
          TextAlign.Center,
          Icons.Default.FormatSize
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
    when (val loadingState = it.state.loadingState) {
      is LoadingState.Loading -> {
        LinearProgressIndicator(
          progress = loadingState.progress,
          modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .align(Alignment.BottomCenter),
          color = MaterialTheme.colorScheme.primary
        )
      }
      else -> {}
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchTextField(
  viewModel: BrowserViewModel,
  inputText: MutableState<String>,
  focus: MutableState<Boolean>,
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val currentText = remember {
    mutableStateOf(
      if (focus.value && !inputText.value.startsWith("file:///android_asset/dweb/")) {
        inputText.value
      } else {
        ""
      }
    )
  }

  BasicTextField(
    value = currentText.value,
    onValueChange = {
      currentText.value = it
      viewModel.handleIntent(BrowserIntent.UpdateSearchEngineState(it.isNotEmpty()))
      viewModel.handleIntent(BrowserIntent.UpdateInputText(it))
    },
    readOnly = false,
    enabled = true,
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = dimenSearchVerticalAlign)
      .onFocusChanged {
        focus.value = it.isFocused
        val text = if (!it.isFocused || inputText.value.startsWith("file:///android_asset/dweb/")) {
          ""
        } else {
          parseInputText(inputText.value, needHost = false) ?: inputText.value
        }
        currentText.value = text
        viewModel.handleIntent(BrowserIntent.UpdateInputText(text))
      },
    singleLine = true,
    maxLines = 1,
    textStyle = TextStyle.Default.copy(
      /*color = MaterialTheme.colorScheme.onPrimary, */fontSize = dimenTextFieldFontSize
    ),
    keyboardOptions = KeyboardOptions(
      imeAction = if (currentText.value.isUrlOrHost()) {
        ImeAction.Search
      } else {
        ImeAction.Done
      }
    ),
    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }, onSearch = {
      if (currentText.value.isEmpty()) return@KeyboardActions
      viewModel.handleIntent(BrowserIntent.SearchWebView(currentText.value.toRequestUrl()))
      focusManager.clearFocus()
    })
  ) { innerTextField ->
    Box {
      Surface(modifier = Modifier.align(Alignment.Center)) {
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          AnimatedVisibility(visible = !focus.value) {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = dimenSearchHorizontalAlign)
          ) {
            val pair = if (focus.value && currentText.value.isEmpty()) {
              Pair(stringResource(id = R.string.browser_search_hint), Alignment.CenterStart)
            } else if (!focus.value) {
              if (inputText.value.isEmpty() || inputText.value.startsWith("file:///android_asset/dweb/")) {
                Pair(stringResource(id = R.string.browser_search_hint), Alignment.CenterStart)
              } else {
                parseInputText(inputText.value)?.let { text ->
                  Pair(text, Alignment.Center)
                } ?: Pair(stringResource(id = R.string.browser_search_hint), Alignment.CenterStart)
              }
            } else null
            pair?.let { pair ->
              Text(
                text = pair.first,
                modifier = Modifier.align(pair.second),
                fontSize = dimenTextFieldFontSize,
                maxLines = 1
              )
            }
            innerTextField()
          }

          if (currentText.value.isNotEmpty()) {
            Icon(imageVector = Icons.Outlined.Close,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier
                .size(25.dp)
                .clickable {
                  currentText.value = ""
                  viewModel.handleIntent(BrowserIntent.UpdateInputText(""))
                  viewModel.handleIntent(BrowserIntent.UpdateSearchEngineState(false))
                  if (!viewModel.isShowKeyboard) focusManager.clearFocus() // 补充取消聚焦
                })
          }
        }
      }
    }
  }
}

/**
 * 提供给外部调用的  搜索界面，可以含有BrowserViewModel
 */
@Composable
fun BrowserSearchView(viewModel: BrowserViewModel) {
  if (viewModel.uiState.showSearchView.value) {
    val inputText = viewModel.uiState.currentBrowserBaseView.value.state.lastLoadedUrl ?: ""
    val text = if (inputText.startsWith("file:///android_asset") ||
      inputText == stringResource(id = R.string.browser_search_hint)
    ) {
      ""
    } else {
      inputText
    }
    val imeShowed = remember { mutableStateOf(false) }

    LaunchedEffect(imeShowed) {
      snapshotFlow { viewModel.uiState.currentInsets.value }.collect {
        imeShowed.value = it.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
      }
    }

    SearchView(
      text = text,
      imeShowed = imeShowed,
      onClose = {
        viewModel.uiState.showSearchView.value = false
      },
      onSearch = { url -> // 第一个是搜索关键字，第二个是搜索地址
        viewModel.uiState.showSearchView.value = false
        viewModel.saveLastKeyword(url)
        viewModel.handleIntent(BrowserIntent.SearchWebView(url))
      })
  }
}