package org.dweb_browser.browser

import androidx.compose.runtime.Composable
import dweb_browser_kmp.browser.generated.resources.Res
import dweb_browser_kmp.browser.generated.resources.ic_engine_360
import dweb_browser_kmp.browser.generated.resources.ic_engine_baidu
import dweb_browser_kmp.browser.generated.resources.ic_engine_bing_fluent
import dweb_browser_kmp.browser.generated.resources.ic_engine_sogou
import dweb_browser_kmp.browser.generated.resources.ic_launcher_foreground
import dweb_browser_kmp.browser.generated.resources.ic_main_star
import dweb_browser_kmp.browser.generated.resources.ic_web
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource


@OptIn(ExperimentalResourceApi::class)
enum class BrowserDrawResource(val urls: List<String>, val res: DrawableResource) {
  WebEngineDefault("content://drawable/web", Res.drawable.ic_web),//
  BrowserStar("content://drawable/star", Res.drawable.ic_main_star),//
  BrowserLauncher("content://drawable/dweb-browser", Res.drawable.ic_launcher_foreground),//

  WebEngineBaidu("https://www.baidu.com/favicon.ico", Res.drawable.ic_engine_baidu),//
  WebEngineBing(
    "https://www.bing.com/sa/simg/favicon-2x.ico",
    Res.drawable.ic_engine_bing_fluent
  ),//
  WebEngineSogou(
    "https://sogou.com/images/logo/new/favicon.ico?v=4",
    Res.drawable.ic_engine_sogou
  ),//
  WebEngine360("https://www.so.com/favicon.ico", Res.drawable.ic_engine_360),//
  ;

  constructor(url: String, res: DrawableResource) : this(listOf(url), res)

  companion object {
    val ALL_VALUES = mutableMapOf<String, BrowserDrawResource>().also { map ->
      for (item in entries) {
        for (url in item.urls) {
          map[url] = item
        }
      }
    }
  }

  val id = urls.first()

  @Composable
  fun painter() = painterResource(res)
}

