package info.bagen.dwebbrowser.microService.sys.helper

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.Insets
import info.bagen.dwebbrowser.App

data class ColorJson(val red: Int, val blue: Int, val green: Int, val alpha: Int) {
  fun toColor() = Color(red = red, blue = blue, green = green, alpha = alpha)
}

fun Color.toJsonAble(): ColorJson = convert(ColorSpaces.Srgb).let {
  ColorJson(
    (it.red * 255).toInt(),
    (it.blue * 255).toInt(),
    (it.green * 255).toInt(),
    (it.alpha * 255).toInt()
  )
}

fun Color.toCssRgba(): String = convert(ColorSpaces.Srgb).let {
  "rgb(${it.red * 255} ${it.blue * 255} ${it.green * 255} ${if (it.alpha >= 1f) "" else "/ ${it.alpha}"})"
}

fun Float.to2Hex() = (this * 255).toInt().toString(16).padStart(2, '0')
fun Color.toHex(alpha: Boolean = true): String = convert(ColorSpaces.Srgb).let {
  "#(${it.red.to2Hex()}${it.blue.to2Hex()}${it.green.to2Hex()}${if (alpha && it.alpha < 1f) it.alpha.to2Hex() else ""})"
}

fun String.asColorHex(start: Int = 0, len: Int = 2): Int {
  var hex = this.slice(start..(start + len))
  if (len == 1) {
    hex += hex
  }
  return hex.toInt(16)
}

fun Color.Companion.hex(hex: String) = if (hex[0] == '#') when (hex.length) {
  // #RGB
  4 -> Color(hex.asColorHex(1, 1), hex.asColorHex(2, 1), hex.asColorHex(3, 1))
  // #RGBA
  5 -> Color(hex.asColorHex(1, 1), hex.asColorHex(2, 1), hex.asColorHex(3, 1), hex.asColorHex(4, 1))
  // #RRGGBB
  7 -> Color(hex.asColorHex(1), hex.asColorHex(3), hex.asColorHex(5))
  // #RRGGBBAA
  9 -> Color(hex.asColorHex(1), hex.asColorHex(3), hex.asColorHex(5), hex.asColorHex(7))
  else -> null
} else null


data class RectJson(val x: Float, val y: Float, val width: Float, val height: Float)
data class InsetsJson(val top: Float, val left: Float, val right: Float, val bottom: Float)

fun WindowInsets.toJsonAble(
  density: Density = Density(App.appContext), direction: LayoutDirection = LayoutDirection.Ltr
) = InsetsJson(
  top = getTop(density).toFloat(),
  left = getLeft(density, direction).toFloat(),
  right = getRight(density, direction).toFloat(),
  bottom = getBottom(density).toFloat(),
)

fun Insets.toJsonAble() = InsetsJson(
  top = top.toFloat(),
  left = left.toFloat(),
  right = right.toFloat(),
  bottom = bottom.toFloat(),
)
