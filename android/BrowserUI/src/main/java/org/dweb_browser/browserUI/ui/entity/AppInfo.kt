package org.dweb_browser.browserUI.ui.entity

import org.dweb_browser.helper.APP_DIR_TYPE

/**
 * version: {Semantic Version} 该文件格式的版本号，用于告知解析器该如何认知接下来的字段。以下字段是 1.0.0 的字段描述（未来默认向下兼容）
 * bfsAppId: {string} 唯一标识，也就是 bfs-app-id，跟文件夹一致。未来该数据需要从链上申请，所以格式需要保持一致：长度为7+1（校验位）的大写英文字母或数字（链就是系统的“证书颁发机构”，资深用户可以配置不同的的链来安装那些未知来源的应用）
 * name: {string} 应用名词（没有i18n的支持）
 * icon: 应用图标（没有不同主题的支持），一般是 file:///sys/icon.png（这里使用 /sys 文件夹，意味着在虚拟文件系统中，顶多只能访问到bfs-app-id/下的文件内容） 或者是 https://example.com/icon.png （第一里程碑可以不支持链接图片，一般该图片下载下来后，放到 tmp/ 缓存文件夹中直接使用），未来可能支持分布式幂等文件的协议 dweb://
 * author: {string[]} 作者名称与TA的链接，用“,”进行分割，比如： ["kzf,kezhaofeng@bnqkl.cn,https://bnqkl.cn/developer/kzf"]
 * homepage: 应用网络主页，一般是https网站。用户可以通过一些特定的操作来访问应用主页了解更多应用信息
 * autoUpdate: 自动更新的相关配置
 *   maxAge: {number} 最大缓存时间，一般6小时更新一次。最快不能快于1分钟，否则按1分钟算。
 *    provider: {"generic"} 该更新的适配器信息，默认使用“通用适配器”
 *    url: {string} 自动更新的链接，一般是https开头，请求该链接可以获得以下“通用适配器”的字段：
 *        version: {string} 版本号
 *        files: 文件列表
 *            url: {string} 链接
 *            size: {number} 大小
 *            sha512: {string} 校验码
 *        releaseNotes: {string} 本次发布的信息，一般存放更新信息
 *        releaseName: {string} 本次发布的标题，用于展示更新信息时的标题
 *        release_date: {Date} 发布日期
 */
data class AutoUpdateInfo(
  val maxAge: Int, // 最大缓存时间，一般6小时更新一次。最快不能快于1分钟，否则按1分钟算。
  val provider: Int, // {Generic}该更新的适配器信息，默认使用“通用适配器”
  val url: String, // 自动更新的链接，一般是https开头，请求该链接可以获得以下“通用适配器”的字段：
)

data class AppInfo(
  val version: String, // 该文件格式的版本号，用于告知解析器该如何认知接下来的字段。以下字段是 1.0.0 的字段描述
  val bfsAppId: String, // 唯一标识，也就是 bfs-app-id，跟文件夹一致。长度为7+1（校验位）的大写英文字母或数字
  val name: String, // 应用名词（没有i18n的支持）
  val icon: String, // 应用图标（没有不同主题的支持），一般是 file:///sys/icon.png
  val author: List<String> = listOf(), // 作者名称与TA的链接，用“,”进行分割，比如： ["kzf,kezhaofeng@bnqkl.cn,https://bnqkl.cn/developer/kzf"]
  val homepage: String = "", // 应用网络主页，一般是https网站。用户可以通过一些特定的操作来访问应用主页了解更多应用信息
  val autoUpdate: AutoUpdateInfo? = null, // 自动更新的相关配置
  var isRecommendApp: Boolean = false, // 判断是recommend-app还是system-app
  var appDirType: APP_DIR_TYPE = APP_DIR_TYPE.SystemApp,
  var iconPath: String = "", // 将icon转为实际路径
  var dAppUrl: String? = null, // 跳转路径
)