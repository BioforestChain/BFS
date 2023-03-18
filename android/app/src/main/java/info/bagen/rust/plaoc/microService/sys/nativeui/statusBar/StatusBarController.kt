package info.bagen.rust.plaoc.microService.sys.nativeui.statusBar

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowInsetsCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import info.bagen.rust.plaoc.microService.helper.ColorJson
import info.bagen.rust.plaoc.microService.helper.InsetsJson
import info.bagen.rust.plaoc.microService.helper.toJsonAble
import info.bagen.rust.plaoc.microService.sys.nativeui.NativeUiController
import info.bagen.rust.plaoc.microService.sys.nativeui.base.BarController
import info.bagen.rust.plaoc.microService.sys.nativeui.helper.BarStyle
import info.bagen.rust.plaoc.microService.sys.nativeui.helper.debugNativeUi
import info.bagen.rust.plaoc.microService.sys.nativeui.helper.toWindowsInsets

class StatusBarController(
    activity: ComponentActivity,
    nativeUiController: NativeUiController,
) : BarController(activity, nativeUiController) {

    /**
     * 使得当前 StatusBarController 生效
     */
    @Composable
    override fun effect(): StatusBarController {
        val systemUiController = rememberSystemUiController()

        insetsState.value =
            nativeUiController.getCurrentInsets(WindowInsetsCompat.Type.statusBars())
                .toWindowsInsets()

        val color by colorState
        val style by styleState
        var visible by visibleState
        DisposableEffect(visible, color, style) {
            debugNativeUi(
                "DisposableEffect", "visible:$visible; color:$color; style:$style"
            )
            systemUiController.isStatusBarVisible = visible
//                systemUiController.statusBarDarkContentEnabled = true
            systemUiController.setStatusBarColor(
                color = color,
                darkIcons = when (style) {
                    BarStyle.Dark -> true
                    BarStyle.Light -> false
                    else -> color.luminance() > 0.5F
                },
            )
            onDispose {}
        }

        observer.stateChanges.also {
            observerWatchStates(it)

            it.effectChange {
                debugNativeUi("StatusBar", "CHANGED")
                observer.notifyObserver()
            }
        }

        return this
    }


    data class StatusBarState(
        override val visible: Boolean,
        override val style: BarStyle,
        override val overlay: Boolean,
        override val color: ColorJson,
        override val insets: InsetsJson,
    ) : BarState


    override fun toJsonAble() = StatusBarState(
        visible = visibleState.value,
        style = styleState.value,
        overlay = overlayState.value,
        color = colorState.value.toJsonAble(),
        insets = insetsState.value.toJsonAble(),
    )
}