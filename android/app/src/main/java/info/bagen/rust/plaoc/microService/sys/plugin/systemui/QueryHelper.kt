package info.bagen.rust.plaoc.microService.sys.plugin.systemui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Color
import info.bagen.rust.plaoc.microService.core.NativeMicroModule
import info.bagen.rust.plaoc.microService.helper.ColorJson
import info.bagen.rust.plaoc.microService.helper.gson
import info.bagen.rust.plaoc.microService.helper.toJsonAble
import org.http4k.core.Request
import org.http4k.lens.Query
import org.http4k.lens.boolean
import org.http4k.lens.string

class QueryHelper {
    companion object {
        init {
            NativeMicroModule.ResponseRegistry.registryJsonAble(Color::class.java) { it.toJsonAble() }
            NativeMicroModule.ResponseRegistry.registryJsonAble(WindowInsets::class.java) { it.toJsonAble() }
            NativeMicroModule.ResponseRegistry.registryJsonAble(NativeUiController.StatusBarController::class.java) { it.toJsonAble() }
            NativeMicroModule.ResponseRegistry.registryJsonAble(NativeUiController.NavigationBarController::class.java) { it.toJsonAble() }
        }

        fun init() {
            // 确保 init 里头的类被注册
        }

        val query_color = Query.string().optional("color")
        val query_style = Query.string().optional("style")
        val query_visible = Query.boolean().optional("visible")
        val query_overlay = Query.boolean().optional("overlay")
        inline fun color(req: Request) = query_color(req)?.let {
            gson.fromJson(it, ColorJson::class.java).toColor()
        }

        inline fun style(req: Request) = query_style(req)?.let {
            NativeUiController.BarStyle.from(it)
        }

        inline fun visible(req: Request) = query_visible(req)
        inline fun overlay(req: Request) = query_overlay(req)
    }
}



