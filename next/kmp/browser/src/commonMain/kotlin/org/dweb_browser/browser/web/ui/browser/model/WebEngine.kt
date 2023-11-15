package org.dweb_browser.browser.web.ui.browser.model

import io.ktor.http.Url
import io.ktor.http.fullPath
import kotlinx.serialization.Serializable

/**
 * 该文件主要定义搜索引擎和引擎默认值，以及配置存储
 */

@Serializable
data class WebEngine(
  val name: String,
  val host: String,
  val start: String,
  var timeMillis: String = "",
  val iconRes: Int = 0,// = R.drawable.ic_web,
) {
  fun fit(url: String): Boolean {
    val current = Url("${start}test")
    val query = current.parameters.names().first()
    val uri = Url(url)
    return uri.host == current.host && uri.fullPath == current.fullPath && uri.parameters[query] != null
  }

  fun queryName(): String {
    val current = Url("${start}test")
    return current.parameters.names().first()
  }
}

val DefaultSearchWebEngine: List<WebEngine>
  get() = listOf(
    WebEngine(
      name = "百度",
      host = "m.baidu.com",
      //iconRes = R.drawable.ic_engine_baidu,
      start = "https://m.baidu.com/s?word=%s"
    ),
    WebEngine(
      name = "搜狗",
      host = "wap.sogou.com",
      //iconRes = R.drawable.ic_engine_sougou,
      start = "https://wap.sogou.com/web/searchList.jsp?keyword=%s"
    ),
    WebEngine(
      name = "360",
      host = "m.so.com",
      //iconRes = R.drawable.ic_engine_360,
      start = "https://m.so.com/s?q=%s"
    ),
  )

val DefaultAllWebEngine: List<WebEngine>
  get() = listOf(
    WebEngine(name = "必应", host = "cn.bing.com", start = "https://cn.bing.com/search?q="),
    WebEngine(name = "百度", host = "m.baidu.com", start = "https://m.baidu.com/s?word="),
    WebEngine(name = "百度", host = "www.baidu.com", start = "https://www.baidu.com/s?wd="),
    WebEngine(
      name = "谷歌", host = "www.google.com", start = "https://www.google.com/search?q="
    ),
    WebEngine(
      name = "搜狗",
      host = "wap.sogou.com",
      start = "https://wap.sogou.com/web/searchList.jsp?keyword="
    ),
    WebEngine(name = "搜狗", host = "www.sogou.com", start = "https://www.sogou.com/web?query="),
    WebEngine(name = "360搜索", host = "m.so.com", start = "https://m.so.com/s?q="),
    WebEngine(name = "360搜索", host = "www.so.com", start = "https://www.so.com/s?q="),
    WebEngine(
      name = "雅虎", host = "search.yahoo.com", start = "https://search.yahoo.com/search?p="
    )
  )

/**
 * 根据内容来判断获取引擎
 */
internal fun findWebEngine(url: String): WebEngine? {
  for (item in DefaultAllWebEngine) {
    if (item.fit(url)) return item
  }
  return null
}