package org.dweb_browser.helper.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import org.dweb_browser.helper.compose.reorder.ItemPosition
import org.dweb_browser.helper.compose.reorder.ReorderAbleItem
import org.dweb_browser.helper.compose.reorder.detectReorderAfterLongPress
import org.dweb_browser.helper.compose.reorder.rememberReorderAbleLazyListState
import org.dweb_browser.helper.compose.reorder.reorderAble

@Composable
fun <T> LazyReorderColumn(
  items: List<T>,
  key: (item: T) -> Any,
  onDragMove: (ItemPosition, ItemPosition) -> Unit,
  onDragEnd: (startIndex: Int, endIndex: Int) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable RowScope.(item: T) -> Unit,
) {
  val state = rememberReorderAbleLazyListState(onMove = onDragMove, onDragEnd = onDragEnd)
  LazyColumn(
    state = state.listState,
    modifier = Modifier.fillMaxSize().reorderAble(state)
  ) {
    items(items, key = { item -> key(item) }) { item ->
      ReorderAbleItem(
        reorderAbleState = state,
        key = key(item),
        modifier = modifier,
      ) { dragging ->
        val elevation = animateDpAsState(if (dragging) 8.dp else 0.dp, label = "")
        Row(
          modifier = Modifier.detectReorderAfterLongPress(state).shadow(elevation.value),
          content = { content(item) }
        )
      }
    }
  }
}

@Composable
fun <T> LazySwipeColumn(
  items: List<T>,
  key: (item: T) -> Any,
  onRemove: (item: T) -> Unit,
  modifier: Modifier = Modifier,
  background: @Composable RowScope.() -> Unit,
  content: @Composable RowScope.(item: T) -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(items, key = { item -> key(item) }) { item ->
      ListSwipeItem(
        modifier = modifier,
        onRemove = { onRemove(item) },
        background = background,
        content = { Row(modifier = Modifier.fillMaxSize()) { content(item) } }
      )
    }
  }
}

/**
 * 可以左右滑动删除和上下拖动排序
 */
@Composable
fun <T> LazySwipeAndReorderColumn(
  items: List<T>,
  key: (item: T) -> Any,
  onDragMove: (ItemPosition, ItemPosition) -> Unit,
  onDragEnd: (startIndex: Int, endIndex: Int) -> Unit,
  onRemove: (item: T) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable (item: T) -> Unit,
) {
  val state = rememberReorderAbleLazyListState(onMove = onDragMove, onDragEnd = onDragEnd)
  LazyColumn(
    state = state.listState,
    modifier = Modifier.fillMaxSize().reorderAble(state)
  ) {
    items(items, key = { item -> key(item) }) { item ->
      ReorderAbleItem(
        reorderAbleState = state,
        key = key(item),
        modifier = modifier,
      ) { dragging ->
        val elevation = animateDpAsState(if (dragging) 8.dp else 0.dp, label = "")
        ListSwipeItem(
          modifier = Modifier.detectReorderAfterLongPress(state).shadow(elevation.value),
          onRemove = { onRemove(item) },
          content = { content(item) }
        )
      }
    }
  }
}

/**
 * 可以左右滑动删除和上下拖动排序
 */
@Composable
fun <T> LazySwipeAndReorderList(
  items: List<T>,
  key: (item: T) -> Any,
  onDragMove: (ItemPosition, ItemPosition) -> Unit,
  onDragEnd: (startIndex: Int, endIndex: Int) -> Unit,
  onRemove: (item: T) -> Unit,
  modifier: Modifier = Modifier,
  headlineContent: @Composable (item: T) -> Unit,
  overlineContent: @Composable ((item: T) -> Unit)? = null,
  supportingContent: @Composable ((item: T) -> Unit)? = null,
  leadingContent: @Composable ((item: T) -> Unit)? = null,
  trailingContent: @Composable ((item: T) -> Unit)? = null,
  colors: ListItemColors = ListItemDefaults.colors(),
) {
  val state = rememberReorderAbleLazyListState(onMove = onDragMove, onDragEnd = onDragEnd)
  LazyColumn(
    state = state.listState,
    modifier = Modifier.fillMaxSize().reorderAble(state)
  ) {
    items(items, key = { item -> key(item) }) { item ->
      ReorderAbleItem(
        reorderAbleState = state,
        key = key(item),
        modifier = modifier,
      ) { dragging ->
        val elevation = animateDpAsState(if (dragging) 8.dp else 0.dp, label = "")
        ListSwipeItem(
          modifier = Modifier.detectReorderAfterLongPress(state).shadow(elevation.value),
          onRemove = { onRemove(item) }
        ) {
          ListItem(
            modifier = Modifier.fillMaxSize(),
            headlineContent = { headlineContent(item) },
            overlineContent = overlineContent?.let { { overlineContent(item) } },
            supportingContent = supportingContent?.let { { supportingContent(item) } },
            leadingContent = leadingContent?.let { { leadingContent(item) } },
            trailingContent = trailingContent?.let { { trailingContent(item) } },
            colors = colors
          )
        }
      }
    }
  }
}