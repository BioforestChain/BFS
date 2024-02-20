package org.dweb_browser.browser.search

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.dweb_browser.browser.BrowserIconResource
import org.dweb_browser.browser.getIconResource
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.core.std.file.ext.createStore
import org.dweb_browser.helper.ImageResource
import org.dweb_browser.helper.platform.toImageBitmap

@Serializable
data class SearchEngine(
  val host: String, // 域名 如：baidu.com, cn.bing.com, www.google.com,
  val keys: String, // 名称，多个可以使用“逗号”分隔，如 "baidu,百度", "google,谷歌"
  val name: String,
  val searchLink: String,
  val homeLink: String,
  val icon: List<ImageResource> = listOf(ImageResource("")),
  var enable: Boolean = false
) {
  fun fit(url: String): Boolean {
    val current = Url("${searchLink}test")
    val query = current.parameters.names().first()
    val uri = Url(url)
    return uri.host == current.host && uri.parameters[query] != null /*&& uri.fullPath == current.fullPath*/
  }
}

private val SearchEngineList = mutableStateListOf(
  SearchEngine(
    host = "baidu.com",
    keys = "baidu,百度",
    name = "百度",
    searchLink = "https://www.baidu.com/s?wd=",
    homeLink = "https://www.baidu.com",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_baidu.xml", type = "image/svg+xml")
    )
  ),
  SearchEngine(
    host = "bing.com",
    keys = "bing,必应",
    name = "Bing",
    searchLink = "https://www.bing.com/search?q=",
    homeLink = "https://www.bing.com",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_bing.png", type = "image/png")
    )
  ),
  SearchEngine(
    host = "sogou.com",
    keys = "sogou,搜狗",
    name = "搜狗",
    searchLink = "https://www.sogou.com/web?query=",
    homeLink = "https://www.sogou.com",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_sogou.xml", type = "image/svg+xml")
    )
  ),
  SearchEngine(
    host = "so.com",
    keys = "360",
    name = "360",
    searchLink = "https://www.so.com/s?q=",
    homeLink = "https://www.so.com/",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_360.xml", type = "image/svg+xml")
    )
  ),
  SearchEngine(
    host = "google.com",
    keys = "Google,谷歌",
    name = "Google",
    searchLink = "https://www.google.com/search?q=",
    homeLink = "https://www.google.com",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_baidu.xml", type = "image/svg+xml")
    )
  ),
  SearchEngine(
    host = "duckduckgo.com",
    keys = "DuckDuckGo",
    name = "DuckDuckGo",
    searchLink = "https://duckduckgo.com/?q=",
    homeLink = "https://duckduckgo.com",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_baidu.xml", type = "image/svg+xml")
    )
  ),
  SearchEngine(
    host = "yahoo.com",
    keys = "yahoo,雅虎",
    name = "雅虎",
    searchLink = "https://sg.search.yahoo.com/search;?p=",
    homeLink = "https://sg.search.yahoo.com/",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_baidu.xml", type = "image/svg+xml")
    )
  ),
  SearchEngine(
    host = "m.sm.cn",
    keys = "神马",
    name = "神马",
    searchLink = "https://so.m.sm.cn/s?q=",
    homeLink = "https://so.m.sm.cn",
    icon = listOf(
      ImageResource(src = "file:///sys/engines/ic_engine_baidu.xml", type = "image/svg+xml")
    )
  ),
)

@Serializable
data class SearchInject(
  val name: String = "unKnow", // 表示应用名称
  val icon: ByteArray? = null, // 表示应用的图标
  val url: String, // ipc 链接、https 链接
) {
  @Transient
  val iconRes: ImageBitmap?
    get() = icon?.toImageBitmap() ?: getIconResource(BrowserIconResource.WebEngineDefault)
}

class SearchStore(mm: MicroModule) {
  private val keyInject = "key_inject"
  private val storeEngine = mm.createStore("engines_state", false)
  private val storeInject = mm.createStore("inject_engine", false)

  suspend fun getAllEnginesState(): MutableList<SearchEngine> {
    val save = storeEngine.getAll<Boolean>()
    return SearchEngineList.onEach { item ->
      item.enable = save[item.host] ?: false
    }
  }

  suspend fun saveEngineState(searchEngine: SearchEngine) {
    storeEngine.set(searchEngine.host, searchEngine.enable)
  }

  suspend fun getAllInjects(): MutableList<SearchInject> {
    return storeInject.getOrPut<MutableList<SearchInject>>(keyInject) {
      mutableStateListOf()
    }
  }

  suspend fun saveInject(list: MutableList<SearchInject>) {
    storeInject.set(keyInject, list)
  }
}