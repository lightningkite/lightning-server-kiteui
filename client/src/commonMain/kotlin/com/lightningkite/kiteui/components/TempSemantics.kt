package com.lightningkite.kiteui.components

import com.lightningkite.kiteui.models.CornerRadii
import com.lightningkite.kiteui.models.Semantic
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.ThemeAndBack
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.models.systemDefaultFixedWidthFont

/**
 * Semantic for markdown code blocks.
 * Applies a monospace font with contrasting background and rounded corners.
 */
data object CodeBlockSemantic : Semantic("mdc") {
    override fun default(theme: Theme): ThemeAndBack = theme.withBack(
        font = theme.font.copy(font = systemDefaultFixedWidthFont),
        background = theme.background.closestColor().highlight(-0.1f),
        cornerRadii = CornerRadii.Fixed(0.25.rem),
    )
}