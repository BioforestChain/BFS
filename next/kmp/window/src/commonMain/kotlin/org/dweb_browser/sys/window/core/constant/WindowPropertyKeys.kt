package org.dweb_browser.sys.window.core.constant

/**
 * 可变属性名称集合
 */
enum class WindowPropertyKeys(val fieldName: String) {
  Constants("constants"),
  Title("title"),
  IconUrl("iconUrl"),
  IconMaskable("iconMaskable"),
  IconMonochrome("iconMonochrome"),
  Mode("mode"),
  Visible("visible"),
  CanGoBack("canGoBack"),
  CanGoForward("canGoForward"),
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
  TopBarContentDarkColor("topBarContentDarkColor"),
  TopBarBackgroundColor("topBarBackgroundColor"),
  TopBarBackgroundDarkColor("topBarBackgroundDarkColor"),
  BottomBarContentColor("bottomBarContentColor"),
  BottomBarContentDarkColor("bottomBarContentDarkColor"),
  BottomBarBackgroundColor("bottomBarBackgroundColor"),
  BottomBarBackgroundDarkColor("bottomBarBackgroundDarkColor"),
  BottomBarTheme("bottomBarTheme"),
  ThemeColor("themeColor"),
  ThemeDarkColor("themeDarkColor"),
  Bounds("bounds"),
  KeyboardInsetBottom("keyboardInsetBottom"),
  KeyboardOverlaysContent("keyboardOverlaysContent"),
  CloseTip("closeTip"),
  ShowCloseTip("showCloseTip"),
  ShowMenuPanel("showMenuPanel"),
  ColorScheme("colorScheme"),
}