package info.bagen.dwebbrowser.microService.core

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type

typealias UUID = String;

/**
 * 可变属性名称集合
 */
enum class WindowPropertyKeys(val fieldName: String) {
  Any("*"),
  Title("title"),
  IconUrl("iconUrl"),
  Mode("mode"),
  Resizable("resizable"),
  Focus("focus"),
  ZIndex("zIndex"),
  Children("children"),
  Parent("parent"),
  Flashing("flashing"),
  FlashColor("flashColor"),
  ProgressBar("progressBar"),
  AlwaysOnTop("alwaysOnTop"),
  DesktopIndex("desktopIndex"),
  ScreenId("screenId"),
  TopBarOverlay("topBarOverlay"),
  BottomBarOverlay("bottomBarOverlay"),
  TopBarContentColor("topBarContentColor"),
  TopBarBackgroundColor("topBarBackgroundColor"),
  BottomBarContentColor("bottomBarContentColor"),
  BottomBarBackgroundColor("bottomBarBackgroundColor"),
  BottomBarTheme("bottomBarTheme"),
  ThemeColor("themeColor"),
  Bounds("bounds"),
}

/**
 * 窗口的模式
 */
@JsonAdapter(WindowMode::class)
enum class WindowMode(val mode: String) : JsonSerializer<WindowMode>,
  JsonDeserializer<WindowMode> {

  /**
   * 浮动模式，默认值
   */
  FLOATING("floating"),

  /**
   * 最大化
   */
  MAXIMIZE("maximize"),

  /**
   * 最小化
   */
  MINIMIZE("minimize"),

  /**
   * 全屏
   */
  FULLSCREEN("fullscreen"),

  /**
   * 画中画模式
   *
   * 与原生的 PIP 不同,原生的PIP有一些限制,比如 IOS 上只能渲染 Media。
   * 而在 desk 中的 PIP 原理简单粗暴,就是将视图进行 clip+scale,因此它本质上还是渲染一整个 win-view。
   * 并且此时这个被裁切的窗口将无法接收到任何用户的手势、键盘等输入,却而代之的,接口中允许一些自定义1~4个的 icon-button,这些按钮将会被渲染在 pip-controls-bar (PIP 的下方) 中方便用户操控当前的 PIP。
   *
   * 多个 PIP 视图会被叠在一起,在一个 max-width == max-height 的空间中,所有 PIP 以 contain 的显示形式居中放置。
   *    只会有一个 PIP 视图显示在最前端,其它层叠显示在它后面
   * PIP 可以全局拖动。
   * PIP 会有两个状态:聚焦和失焦。
   *    点击后,进入聚焦模式,视图轻微放大,pip-controlls-bar 会从 PIP 视图的 Bottom 与 End 侧 显示出来;
   *        其中 Bottom 侧显示的是用户自定义的 icon-button,以 space-around 的显示形式水平放置;
   *        同时 End 侧显示的是 PIP 的 dot-scroll-bar(应该是拟物设计,否则用户认知成本可能会不低),桌面端可以点击或者滚轮滚动、移动端可以上下滑动,从而切换最前端的 PIP 视图
   *        聚焦模式下 PIP 仍然可以全局拖动,但是一旦造成拖动,会同时切换成失焦模式。
   *    在聚焦模式下,再次点击 PIP,将会切换到失焦模式,此时 pip-controlls-bar 隐藏,视图轻微缩小;
   * PIP 的视图的 End-Corner 是一个 resize 区域,用户可以随意拖动这个来对视图进行resize,同时开发者会收到resize指令,从而作出“比例响应式”变更。如果开发者不响应该resize,那么 PIP 会保留 win-view 的渲染比例。
   *
   * > 注意:该模式下,alwaysOnTop 为 true,并且将不再显示 win-controls-bar。
   * > 提示:如果不想 PIP 功能把当前的 win-view  吃掉,那么可以打开一个子窗口来申请 PIP 模式。
   */
  PIP("picture-in-picture"),

  /**
   * 窗口关闭
   */
  CLOSED("closed"),
  ;


  companion object {
    fun from(themeName: String) = values().firstOrNull { it.mode == themeName } ?: FLOATING
  }


  override fun serialize(
    src: WindowMode,
    typeOfSrc: Type,
    context: JsonSerializationContext?
  ) = JsonPrimitive(src.mode)

  override fun deserialize(
    json: JsonElement,
    typeOfT: Type?,
    context: JsonDeserializationContext?
  ) = from(json.asString)
}

//  SPLIT_SCREEN, // 分屏模式
//  SNAP_LEFT, // 屏幕左侧对齐
//  SNAP_RIGHT, // 屏幕右侧对齐
//  CASCADE, // 级联模式
//  TILE_HORIZONTALLY, // 水平平铺
//  TILE_VERTICALLY, // 垂直平铺
//  FLOATING, // 浮动模式
//  PIP, // 画中画模式
//
//  CUSTOM // 自定义模式

/**
 * 底部按钮的风格
 */
@JsonAdapter(WindowBottomBarTheme::class)
enum class WindowBottomBarTheme(val themeName: String) : JsonSerializer<WindowBottomBarTheme>,
  JsonDeserializer<WindowBottomBarTheme> {
  /**
   * 导航模式：较高,面向常见的网页,依次提供app-id+version(两行小字显示)、back-bottom、forword-bottom、unmax bottom(1)。点击app-id等效于点击顶部的titlebar展开的菜单栏(显示窗窗口信息、所属应用信息、一些设置功能(比如刷新页面、设置分辨率、设置UA、查看路径))
   */
  Navigation("navigation"),

  /**
   * 沉浸模式：较矮,只提供app-id+version的信息(一行小字)
   */
  Immersion("immersion"),
//  Status("custom"),
//  Status("status"),
  ;


  companion object {
    fun from(themeName: String) = values().firstOrNull { it.themeName == themeName } ?: Navigation
  }

  override fun serialize(
    src: WindowBottomBarTheme,
    typeOfSrc: Type,
    context: JsonSerializationContext?
  ) = JsonPrimitive(src.themeName)

  override fun deserialize(
    json: JsonElement,
    typeOfT: Type?,
    context: JsonDeserializationContext?
  ) = from(json.asString)

}
