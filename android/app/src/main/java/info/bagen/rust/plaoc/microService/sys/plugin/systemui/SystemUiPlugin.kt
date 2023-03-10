package info.bagen.rust.plaoc.microService.sys.plugin.systemui


import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.Insets
import info.bagen.rust.plaoc.microService.sys.plugin.systemui.keyboard.VirtualKeyboard
import info.bagen.rust.plaoc.webView.network.getColorHex
import info.bagen.rust.plaoc.webView.network.hexToIntColor

class SystemUiPlugin(
    webView: View,
    private val systemUiController: SystemUiController,
) {

    val virtualKeyboard = VirtualKeyboard(systemUiController.virtualKeyboardController.overlayState, webView)

    /**
     * @TODO 在未来，这里的disable与否，通过更加完善的声明来实现，比如可以声明多个rect
     */
//    @JavascriptInterface
//    fun disableTouchEvent() {
//        hook.onTouchEvent = { false }
//    }

    /**第一个参数是颜色HEX。第二个是图标是否更期望于使用深色*/
    fun setStatusBarBackgroundColor(colorHex: String): Boolean {
        systemUiController.statusBarController.apply {
            colorState.value = Color(hexToIntColor(colorHex))
        }
        return true
    }

    fun setStatusBarStyle(style: String): String {
       val darkIcons = when (style) {
           StatusBarStyle.Dark.type -> true
           StatusBarStyle.Light.type -> false
           else -> true
        }
        systemUiController.statusBarController.apply {
            isDarkIconsState.value = darkIcons
        }
        return style
    }

    /** 获取状态栏背景颜色*/
    fun getStatusBarBackgroundColor(): String {
        val color = systemUiController.statusBarController.colorState.value
        val colorInt = android.graphics.Color.argb(color.alpha, color.red, color.green, color.blue)
        println("getStatusBarBackgroundColor=> ${color} $colorInt")
        return getColorHex(colorInt)
    }

    /**
     * 获取状态栏风格
     * 获取状态栏是否更期望使用深色
     * */
    fun getStatusBarIsDark(): String {
        val isDark = systemUiController.statusBarController.isDarkIconsState.value
            ?: (systemUiController.statusBarController.colorState.value.luminance() > 0.5F)
        if (isDark){
            return  StatusBarStyle.Dark.type
        }
        return StatusBarStyle.Light.type
    }
    fun getStatusBarColor(): String {
        val color = systemUiController.statusBarController.colorState.value
        val colorInt = android.graphics.Color.argb(color.alpha, color.red, color.green, color.blue)
        return getColorHex(colorInt)
    }

    /** 查看状态栏是否可见*/
    fun getStatusBarVisible(): Boolean {
        return systemUiController.statusBarController.visibleState.value
    }

    /** 设置false为透明*/
    fun setStatusBarVisible(visible: Boolean): Boolean {
        systemUiController.statusBarController.visibleState.value = visible
        return visible
    }

    /**获取状态栏是否透明的状态*/
    fun getStatusBarOverlay(): Boolean {
        return systemUiController.statusBarController.overlayState.value
    }

    /**设置状态栏是否透明*/
    fun setStatusBarOverlay(isOverlay: Boolean): Boolean {
        systemUiController.statusBarController.overlayState.value = isOverlay
        return true
    }

    /**设置系统导航栏颜色*/
    fun setNavigationBarColor(
        colorHex: String,
        darkIcons: Boolean,
        isNavigationBarContrastEnforced: Boolean
    ): Boolean {
        systemUiController.navigationBarController.apply {
            colorState.value = Color(hexToIntColor(colorHex))
            isDarkIconsState.value = darkIcons
            isContrastEnforcedState.value = isNavigationBarContrastEnforced
        }
        return true
    }

    /**获取系统导航栏颜色*/
    fun getNavigationBarColor(
    ): String {
        val color = systemUiController.navigationBarController.colorState.value
        val colorInt = android.graphics.Color.argb(color.alpha, color.red, color.green, color.blue)
        return getColorHex(colorInt)
    }

    /**获取系统导航栏可见性*/
    fun getNavigationBarVisible(): Boolean {
        return systemUiController.navigationBarController.visibleState.value
    }

    /**设置系统导航栏是否隐藏*/
    fun setNavigationBarVisible(visible: Boolean): Boolean {
        systemUiController.navigationBarController.visibleState.value =
            visible
        return visible
    }

    /**获取系统导航栏是否透明*/
    fun getNavigationBarOverlay(): Boolean {
        return systemUiController.navigationBarController.overlayState.value
    }

    /**设置系统导航栏是否透明*/
    fun setNavigationBarOverlay(isOverlay: Boolean): Boolean {
        systemUiController.navigationBarController.overlayState.value =
            isOverlay
        return isOverlay
    }

    /** 检索顶级窗口装饰视图（包含标准窗口框架/装饰和其中的客户端内容），可以将其作为窗口添加到窗口管理器 */
//    private val insetsCompat: WindowInsetsCompat by lazy {
//        WindowInsetsCompat.toWindowInsetsCompat(
//            activity.window.decorView.rootWindowInsets
//        )
//    }

    private fun Insets.toJson(): String {
        return """{"top":${top},"left":${left},"bottom":${bottom},"right":${right}}"""
    }

    enum class StatusBarStyle(val type:String) {
        /**
         * Light text for dark backgrounds.
         *
         * @since 1.0.0
         */
        Dark("Dark"),

        /**
         * Dark text for light backgrounds.
         *
         * @since 1.0.0
         */
        Light("Light"),

        /**
         * The style is based on the device appearance.
         * If the device is using Dark mode, the statusbar text will be light.
         * If the device is using Light mode, the statusbar text will be dark.
         * On Android the default will be the one the app was launched with.
         *
         * @since 1.0.0
         */
        Default("Default"),
    }

    class InsetsType {
        val FIRST = 1
        val STATUS_BARS = FIRST
        val NAVIGATION_BARS = 1 shl 1
        val CAPTION_BAR = 1 shl 2

        val IME = 1 shl 3

        val SYSTEM_GESTURES = 1 shl 4
        val MANDATORY_SYSTEM_GESTURES = 1 shl 5
        val TAPPABLE_ELEMENT = 1 shl 6

        val DISPLAY_CUTOUT = 1 shl 7

        val LAST = 1 shl 8
        val SIZE = 9
        val WINDOW_DECOR = LAST
    }

//    @JavascriptInterface
//    fun getInsetsRect(typeMask: Int, ignoreVisibility: BoolInt): String {
//        if (ignoreVisibility.toBoolean()) {
//            return insetsCompat.getInsetsIgnoringVisibility(typeMask).toJson()
//        }
//        return insetsCompat.getInsets(typeMask).toJson()
//    }

//    @JavascriptInterface
//    fun showInsets(typeMask: Int) {
//        return WindowCompat.getInsetsController(activity.window, activity.window.decorView)
//            .show(typeMask)
//    }

}
