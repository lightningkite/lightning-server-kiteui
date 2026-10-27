package com.lightningkite.kiteui.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class QrCodeTest {
    @Test
    fun finderPatternsSitInThreeCornersInsideTheQuietZone() {
        val image = qrCode("otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&digits=6")
        assertEquals(-4, image.viewBoxMinX)
        val size = image.viewBoxWidth - 8
        assertEquals(0, (size - 17) % 4, "QR codes are 17 + 4n modules wide")

        val dark = Regex("""M(\d+) (\d+)h1v1h-1z""").findAll(image.paths[1].path)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toSet()

        fun assertFinderAt(left: Int, top: Int) {
            for (y in 0 until 7) for (x in 0 until 7) {
                val ring = maxOf(kotlin.math.abs(x - 3), kotlin.math.abs(y - 3))
                assertEquals(ring != 2, (left + x to top + y) in dark, "finder at ($left, $top), square ($x, $y)")
            }
        }
        assertFinderAt(0, 0)
        assertFinderAt(size - 7, 0)
        assertFinderAt(0, size - 7)
    }
}
