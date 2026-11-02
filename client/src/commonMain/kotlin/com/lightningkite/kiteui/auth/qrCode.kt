package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Color
import com.lightningkite.kiteui.models.ImageVector
import com.lightningkite.kiteui.models.rem
import qrcode.raw.QRCodeProcessor

internal fun qrCode(text: String): ImageVector {
    val modules = QRCodeProcessor(text).encode()
    val quietZone = 4
    val sizeWithMargin = modules.size + 2 * quietZone
    val darkSquares = buildString {
        modules.forEachIndexed { row, squares ->
            squares.forEachIndexed { column, square ->
                if (square.dark) append("M$column ${row}h1v1h-1z")
            }
        }
    }
    // Always dark on light, whatever the theme: not every scanner reads an inverted code.
    return ImageVector(
        width = 16.rem,
        height = 16.rem,
        viewBoxMinX = -quietZone,
        viewBoxMinY = -quietZone,
        viewBoxWidth = sizeWithMargin,
        viewBoxHeight = sizeWithMargin,
        paths = listOf(
            ImageVector.Path(fillColor = Color.white, path = "M-$quietZone -${quietZone}h${sizeWithMargin}v${sizeWithMargin}h-${sizeWithMargin}z"),
            ImageVector.Path(fillColor = Color.black, path = darkSquares),
        ),
    )
}
