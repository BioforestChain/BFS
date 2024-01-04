package org.dweb_browser.browser.web.view

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dweb_browser.browser.BrowserI18nResource
import org.dweb_browser.browser.util.toRequestUrl
import org.dweb_browser.browser.web.model.LocalBrowserModel
import org.dweb_browser.browser.web.model.WebEngine
import org.dweb_browser.browser.web.model.parseInputText
import org.dweb_browser.helper.compose.clickableWithNoEffect
import org.dweb_browser.sys.window.render.LocalWindowsImeVisible

/**
 * 组件： 搜索组件
 */
@Composable
internal fun BoxScope.SearchView(
  text: String,
  modifier: Modifier = Modifier,
  homePreview: (@Composable (onMove: (Boolean) -> Unit) -> Unit)? = null,
  searchPreview: (@Composable () -> Unit)? = null,
  onClose: () -> Unit,
  onSearch: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val inputText = remember(text) { mutableStateOf(parseInputText(text, false)) }
  val searchPreviewState = remember { MutableTransitionState(text.isNotEmpty()) }
  val webEngine = LocalBrowserModel.current.filterFitUrlEngines(text)

  Box(modifier = modifier) {
    homePreview?.let {
      it { moved ->
        focusManager.clearFocus()
        if (!moved) onClose()
      }
    }

    Text(
      text = BrowserI18nResource.button_name_cancel(),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .clip(RoundedCornerShape(16.dp))
        .clickableWithNoEffect { onClose() }
        .padding(20.dp),
      fontSize = 16.sp,
      color = MaterialTheme.colorScheme.primary
    )

    searchPreview?.let { it() } ?: SearchPreview(
      show = searchPreviewState,
      text = inputText,
      onClose = {
        focusManager.clearFocus()
        onClose()
      },
      onSearch = {
        focusManager.clearFocus()
        onSearch(it)
      }
    )
  }

  key(inputText) {
    BrowserTextField(
      text = inputText,
      webEngine = webEngine,
      onSearch = { onSearch(it) },
      onValueChanged = { inputText.value = it; searchPreviewState.targetState = it.isNotEmpty() }
    )
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BoxScope.BrowserTextField(
  text: MutableState<String>,
  webEngine: WebEngine?,
  onSearch: (String) -> Unit,
  onValueChanged: (String) -> Unit
) {
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  var inputText by remember { mutableStateOf(text.value) }
  val browserViewModel = LocalBrowserModel.current

  CustomTextField(
    value = inputText,
    onValueChange = { inputText = it.trim(); onValueChanged(inputText) },
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background)
      .align(Alignment.BottomCenter)
      .padding(
        start = 25.dp,
        end = 25.dp,
        top = 10.dp,
        bottom = 10.dp, // if (localShowIme.value) 0.dp else 50.dp // 为了贴合当前的界面底部工具栏
      )
      .height(dimenSearchHeight)
      .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.background)
      .onKeyEvent {
        if (it.key == Key.Enter) {
          inputText.toRequestUrl()?.let { url ->
            onSearch(url)
          } ?: webEngine?.let { webEngine ->
            onSearch("${webEngine.start}$inputText")
          } ?: focusManager.clearFocus(); keyboardController?.hide()
          true
        } else {
          false
        }
      },
    label = {
      Text(
        text = BrowserI18nResource.browser_search_hint(),
        fontSize = dimenTextFieldFontSize,
        textAlign = TextAlign.Start,
        maxLines = 1
      )
    },
    trailingIcon = {
      Image(
        imageVector = Icons.Default.Close,
        contentDescription = "Close",
        modifier = Modifier
          .clickable { inputText = ""; onValueChanged(inputText) }
          .size(28.dp)
          .padding(4.dp)
      )
    },
    keyboardOptions = KeyboardOptions(
      // 旧版本判断，如果搜索过一次，那么就直接按照之前搜索的来搜索，不进行输入内容是否域名的判断
      // imeAction = if (webEngine != null || inputText.isUrlOrHost()) ImeAction.Search else ImeAction.Done
      imeAction = ImeAction.Search // 增加上面的切换功能，会引起荣耀手机输入法异常多输出一个空格。
    ),
    keyboardActions = KeyboardActions(
      // onDone = { focusManager.clearFocus(); keyboardController?.hide() },
      onSearch = {
        // 如果内容符合地址，直接进行搜索，其他情况就按照如果有搜索引擎就按照搜索引擎来，没有的就隐藏键盘
        inputText.toRequestUrl()?.let { url ->
          onSearch(url)
        } ?: webEngine?.let { webEngine ->
          onSearch("${webEngine.start}$inputText")
        } ?: run {
          focusManager.clearFocus()
          keyboardController?.hide()
          if (browserViewModel.filterShowEngines.isEmpty()) {
            browserViewModel.showToastMessage(BrowserI18nResource.browser_engine_toast_noFound.text)
          }
        }
      }
    )
  )
}

@Composable
fun CustomTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: @Composable (() -> Unit)? = null,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  spacerWidth: Dp = 10.dp,
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val showIme = LocalWindowsImeVisible.current
  val scope = rememberCoroutineScope()

  LaunchedEffect(focusRequester) {
    delay(100)
    focusRequester.requestFocus()
  }

  BasicTextField(
    value = value,
    onValueChange = { onValueChange(it) },
    modifier = modifier.focusRequester(focusRequester),
    maxLines = 1,
    singleLine = true,
    textStyle = TextStyle.Default.copy(
      fontSize = dimenTextFieldFontSize,
      color = MaterialTheme.colorScheme.onSecondaryContainer
    ),
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
  ) { innerTextField ->
    Box(
      modifier = Modifier.fillMaxSize().pointerInput(focusManager) {
        awaitEachGesture { // 在clickable中，会被栏拦截事件，所以只能这么写了，单次是 awaitPointerEventScope
          awaitPointerEvent(PointerEventPass.Initial)
          if (!showIme.value) {
            scope.launch { // 键盘手动隐藏后，再次点击不显示问题
              focusManager.clearFocus()
              focusRequester.requestFocus()
            }
          }
        }
      },
      contentAlignment = Alignment.CenterStart
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(spacerWidth))
        if (leadingIcon != null) {
          leadingIcon()
          Spacer(modifier = Modifier.width(spacerWidth))
        }
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.CenterStart
        ) {
          innerTextField()
          if (label != null && value.isEmpty()) label() // 如果内容是空的才显示
        }
        if (trailingIcon != null) {
          Spacer(modifier = Modifier.width(spacerWidth))
          trailingIcon()
        }
        Spacer(modifier = Modifier.width(spacerWidth))
      }
    }
  }
}

@Composable
internal fun SearchPreview( // 输入搜索内容后，显示的搜索信息
  show: MutableTransitionState<Boolean>,
  text: MutableState<String>,
  onClose: () -> Unit,
  onSearch: (String) -> Unit
) {
  if (show.targetState) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 20.dp)
        .clickableWithNoEffect { }
    ) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
        ) {
          Text(
            text = BrowserI18nResource.browser_search_title(),
            modifier = Modifier.align(Alignment.Center),
            fontSize = 20.sp
          )

          Text(
            text = BrowserI18nResource.browser_search_cancel(),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .clickable { onClose() },
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
      item { // 搜索引擎
        SearchItemEngines(text.value) { onSearch(it) }
      }
    }
  }
}

@Composable
private fun SearchItemEngines(text: String, onSearch: (String) -> Unit) {
  val viewModel = LocalBrowserModel.current
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = BrowserI18nResource.browser_search_engine(),
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier.padding(vertical = 10.dp)
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.background)
    ) {
      val list = viewModel.filterShowEngines
      if (list.isEmpty()) {
        // TODO 提示需要去设置里面配置搜索引擎
        androidx.compose.material3.ListItem(
          headlineContent = {
            Text(text = BrowserI18nResource.browser_engine_tips_noFound())
          },
          leadingContent = {
            Icon(
              imageVector = Icons.Default.Error,
              contentDescription = null,
              modifier = Modifier.size(40.dp)
            )
          }
        )
        return
      }
      list.forEachIndexed { index, webEngine ->
        if (index > 0) Divider(modifier = Modifier.fillMaxWidth().height(1.dp))
        androidx.compose.material3.ListItem(
          headlineContent = {
            Text(text = webEngine.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
          },
          modifier = Modifier.clickable { onSearch("${webEngine.start}$text") },
          supportingContent = {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          },
          leadingContent = {
            webEngine.iconRes?.let { imageBitmap ->
              Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
              )
            }
          }
        )
      }
    }
  }
}