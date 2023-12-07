package org.dweb_browser.helper.platform

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.android.BaseActivity
import org.dweb_browser.helper.platform.theme.DwebBrowserAppTheme

open class PureViewController : BaseActivity(), IPureViewController {
  override val lifecycleScope: CoroutineScope
    get() = (this as LifecycleOwner).lifecycleScope
  private val createSignal = Signal<IPureViewCreateParams>()
  override val onCreate = createSignal.toListener()
  private val destroySignal = SimpleSignal()
  override val onDestroy = destroySignal.toListener()
  private val stopSignal = SimpleSignal()
  override val onStop = stopSignal.toListener()
  private val resumeSignal = SimpleSignal()
  override val onResume = resumeSignal.toListener()
  private val pauseSignal = SimpleSignal()
  override val onPause = pauseSignal.toListener()
  private val touchSignal = Signal<TouchEvent>()
  override val onTouch = touchSignal.toListener()
  final override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch {
      createSignal.emit(PureViewCreateParams(intent))
    }
    setContent {
      CompositionLocalProvider(
        LocalPureViewController provides this,
        LocalPureViewBox provides PureViewBox(this)
      ) {
        DwebBrowserAppTheme {
          for (content in contents) {
            content()
          }
        }
      }
    }
  }

  final override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      resumeSignal.emit()
    }
  }

  final override fun onPause() {
    super.onPause()
    lifecycleScope.launch {
      pauseSignal.emit()
    }
  }

  final override fun onStop() {
    lifecycleScope.launch {
      stopSignal.emit()
    }
    super.onStop()
  }

  final override fun onDestroy() {
    lifecycleScope.launch {
      destroySignal.emit()
    }
    super.onDestroy()
  }

  final override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    lifecycleScope.launch {
      touchSignal.emit(
        TouchEvent(
          ev.x,
          ev.y,
          window.decorView.width.toFloat(),
          window.decorView.height.toFloat()
        )
      )
    }
    return super.dispatchTouchEvent(ev)
  }

  private val contents = mutableStateListOf<@Composable () -> Unit>()
  override fun getContents(): MutableList<() -> Unit> {
    return contents
  }
}

fun IPureViewController.asAndroid(): PureViewController {
  require(this is PureViewController)
  return this
}

fun Bundle.toMap() = mapOf(*keySet().map {
  Pair(it, get(it))
}.toTypedArray())

class PureViewCreateParams(
  val intent: Intent
) : Map<String, Any?> by (intent.extras ?: Bundle()).toMap(), IPureViewCreateParams {
  override fun getString(key: String): String? = get(key).let { require(it is String?);it }
  override fun getInt(key: String): Int? = get(key).let { require(it is Int?);it }
  override fun getFloat(key: String): Float? = get(key).let { require(it is Float?);it }
  override fun getBoolean(key: String): Boolean? = get(key).let { require(it is Boolean?);it }
}