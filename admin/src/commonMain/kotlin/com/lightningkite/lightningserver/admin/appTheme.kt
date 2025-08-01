package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.models.*
import com.lightningkite.reactive.core.Constant

//fun default(color: Color?): Theme {
//    val baseColor = Color.fromHex(0x2D3237).toHSP()
//
//    val hue: Angle = baseColor.hue
//    val accentHue: Angle = hue + Angle.halfTurn
//    val saturation: Float = baseColor.saturation
//    val baseBrightness: Float = baseColor.brightness - 0.1f
//    val brightnessStep: Float = 0.05f
//    val title: FontAndStyle = FontAndStyle(Resources.livvic, weight = 600, allCaps = true)
//    val body: FontAndStyle = FontAndStyle(Resources.livvic)
//    return Theme(
//        id = "default-${color?.toInt()}",
//        font = body,
//        elevation = 0.dp,
//        cornerRadii = CornerRadii.RatioOfSpacing(0.8f),
//        gap = 0.75.rem,
//        outlineWidth = 0.px,
//        foreground = if (baseBrightness > 0.6f) Color.black else Color.white,
//        iconOverride = color,
//        background = HSPColor(hue = hue, saturation = saturation, brightness = baseBrightness).toRGB(),
//        outline = HSPColor(hue = hue, saturation = saturation, brightness = 0.4f).toRGB(),
//        derivations = mapOf(
//            HeaderSemantic to {
//                it.withoutBack(font = title)
//            },
//            ImportantSemantic to {
//                val existing = it.background.closestColor().toHSP()
//                if (abs(existing.brightness - 0.5f) > brightnessStep * 3) {
//                    val b = existing.copy(brightness = 0.3f).toRGB()
//                    it.copy(
//                        id = "imp",
//                        foreground = b.highlight(1f),
//                        background = b,
//                        outline = b,
//                    )
//                } else {
//                    val closerToAccent =
//                        (existing.hue angleTo hue).turns.absoluteValue > (existing.hue angleTo accentHue).turns.absoluteValue
//                    val b = HSPColor(
//                        hue = if (closerToAccent) hue else accentHue,
//                        saturation = saturation,
//                        brightness = 0.5f
//                    ).toRGB()
//                    it.copy(
//                        id = "imp",
//                        foreground = b.highlight(1f),
//                        background = b,
//                        outline = b,
//                    )
//                }.withBack
//            },
//            CardSemantic to {
//                it.copy(
//                    id = "crd",
//                    background = it.background.closestColor().toHSP().let {
//                        it.copy(brightness = it.brightness + brightnessStep)
//                    }.toRGB(),
//                    outline = it.outline.closestColor().toHSP().let {
//                        it.copy(brightness = it.brightness + brightnessStep)
//                    }.toRGB()
//                ).withBack
//            },
//            HoverSemantic to {
//                it.copy(id = "hov", background = it.background.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep)
//                }.toRGB(), outline = it.outline.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep)
//                }.toRGB(), outlineWidth = it.outlineWidth * 2).withBack
//            },
//            FocusSemantic to {
//                val o = it.outline.closestColor()
//                val b = it.background.closestColor()
//                if (b.alpha == 0f || abs(o.perceivedBrightness - b.perceivedBrightness) > 0.4) {
//                    it.copy(
//                        id = "fcs",
//                        outlineWidth = it.outlineWidth + 3.dp,
//                    )
//                } else {
//                    it.copy(
//                        id = "fcs",
//                        outlineWidth = it.outlineWidth + 3.dp,
//                        outline = Color.gray(baseBrightness).highlight(1f)
//                    )
//                }.withBack
//            },
//            DownSemantic to {
//                it.copy(id = "dwn", background = it.background.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep * 3)
//                }.toRGB(), outline = it.outline.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep * 3)
//                }.toRGB(), outlineWidth = it.outlineWidth * 2).withBack
//            },
//
//            FieldSemantic to {
//                it.copy(
//                    id = "fld",
//                    outlineWidth = 1.px,
//                    background = it.background.closestColor(),
//                    revert = true,
////                gap = it.gap / 2,
//                    cornerRadii = when (val base = it.cornerRadii) {
//                        is CornerRadii.Constant -> CornerRadii.ForceConstant(base.value)
//                        is CornerRadii.ForceConstant -> base
//                        is CornerRadii.RatioOfSize -> base
//                        is CornerRadii.RatioOfSpacing -> CornerRadii.ForceConstant(it.gap * base.value)
//                        is CornerRadii.PerCorner -> base
//                    }
//                ).withBack
//            },
//
//            BarSemantic to { it.withBack },
//            NavSemantic to { it.withBack },
//            OuterSemantic to { it.withBack(cascading = false, gap = 1.px, padding = Edges.ZERO, background = Color.gray(0.3f)) },
//            MainContentSemantic to { it.withBack },
//
//            DialogSemantic to {
//                it.copy(id = "dlg", outlineWidth = 1.dp, gap = 2.rem, revert = true).withBack
//            },
//        )
//    )
//}
//
fun lk(): Theme {
    val background = Color.fromHex(0x08181D)
    val foreground = Color.white
    val card = Color.fromHex(0x133C4A)
    val titleColor = Color.fromHex(0xF4B61B)
    val title: FontAndStyle = FontAndStyle(Resources.barlow, allCaps = true)
    val title2: FontAndStyle = FontAndStyle(Resources.barlow, weight = 600, allCaps = true)
    val body: FontAndStyle = FontAndStyle(Resources.lato)

    return Theme(
        id = "lk",
        font = body,
        elevation = 0.dp,
        cornerRadii = CornerRadii.Constant(0.5.rem),
        gap = 0.75.rem,
        outlineWidth = 0.px,
        iconOverride = titleColor,
        foreground = foreground,
        background = background,
        outline = foreground,
        derivations = mapOf(
            BarSemantic to { it.withBack },
            NavSemantic to { it.withBack },
            OuterSemantic to { it.withBack(cascading = false, gap = 1.px, padding = Edges.ZERO, background = Color.gray(0.3f)) },
            MainContentSemantic to { it.withBack },
            HeaderSemantic to {
                it.withoutBack(font = title, foreground = titleColor)
            },
            CardSemantic to {
                it.withBack(
                    background = if(it.background == background) card else it.background.lighten(0.05f),
                    foreground = foreground
                )
            },
            ImportantSemantic to {
                it.withBack(
                    background = if(it.background == background) card else it.background.lighten(0.05f),
                    foreground = foreground
                )
            },
            CriticalSemantic to {
                it.withBack(
                    background = titleColor,
                    foreground = titleColor.highlight(1f)
                )
            },
            DialogSemantic to {
                it.withBack(outlineWidth = 1.dp, gap = 2.rem, cascading = false)
            },
            ListSemantic to {
                it.withoutBack(gap = 2.dp, cascading = false)
            }
        )
    )
}
//
//fun elsie(): Theme {
//    val baseColor = Color.fromHex(0x446A6A)
//    val baseColorLight = Color.fromHex(0x7BA7A7)
//    val back = Color.fromHex(0x0e1114)
//    val accentColor = Color.fromHex(0x619292)
//
//    val id: String = "elsie"
//    val title: FontAndStyle = FontAndStyle(Resources.livvic, weight = 600, allCaps = true)
//    val body: FontAndStyle = FontAndStyle(Resources.livvic)
//    return Theme(
//        id = id,
//        font = body,
//        elevation = 0.dp,
//        cornerRadii = CornerRadii.Constant(0.5.rem),
//        gap = 0.75.rem,
//        outlineWidth = 0.px,
//        foreground = baseColorLight,
//        background = back,
//        outline = baseColorLight,
//        derivations = mapOf(
//            HeaderSemantic to {
//                it.withoutBack(font = title)
//            },
//            ImportantSemantic to {
//                when(it.background) {
//                    baseColor -> it.withBack(background = accentColor, foreground = Color.white)
//                    accentColor -> it.withBack(background = back, foreground = baseColorLight)
//                    else -> it.withBack(background = baseColor, foreground = Color.white)
//                }
//            },
//            CardSemantic to {
//                when(it.background) {
//                    baseColor -> it.withBack(background = accentColor, foreground = Color.white)
//                    accentColor -> it.withBack(background = back, foreground = baseColorLight)
//                    else -> it.withBack(background = baseColor, foreground = Color.white)
//                }
//            },
//            ListSemantic to {
//                val d: Map<Semantic, Semantic.(Theme) -> ThemeAndBack> = mapOf(
//                    CardSemantic to { it.withBack }
//                )
//                when(it.background) {
//                    baseColor -> it.withBack(
//                        background = accentColor,
//                        foreground = Color.white,
//                        cornerRadii = CornerRadii.ForceConstant(0.5.rem),
//                        derivations = d)
//                    accentColor -> it.withBack(
//                        background = back,
//                        foreground = baseColorLight,
//                        cornerRadii = CornerRadii.ForceConstant(0.5.rem),
//                        derivations = d)
//                    else -> it.withBack(
//                        background = baseColor,
//                        foreground = Color.white,
//                        cornerRadii = CornerRadii.ForceConstant(0.5.rem),
//                        derivations = d)
//                }
//            },
//            FieldSemantic to {
//                it.withBack(
//                    outlineWidth = 1.dp
//                )
//            },
//            BarSemantic to { it.withBack },
//            NavSemantic to { it.withBack },
//            OuterSemantic to { it.withBack(cascading = false, gap = 1.px, padding = Edges.ZERO, background = Color.gray(0.3f)) },
//            MainContentSemantic to { it.withBack },
//
//            DialogSemantic to {
//                it.withBack(outlineWidth = 1.dp, gap = 2.rem, cascading = false)
//            },
//        )
//    )
//}
//
//fun elsieCalm(): Theme {
//    val baseColor = Color.fromHex(0x446A6A).darken(0.5f)
//    val baseColorLight = Color.fromHex(0x7BA7A7)
//    val back = Color.fromHex(0x0e1114)
//    val accentColor = Color.fromHex(0x619292)
//
//    val id: String = "elsie-calm"
//    val title: FontAndStyle = FontAndStyle(Resources.livvic, weight = 600, allCaps = true)
//    val body: FontAndStyle = FontAndStyle(Resources.livvic)
//    return Theme(
//        id = id,
//        font = body,
//        elevation = 0.dp,
//        cornerRadii = CornerRadii.Constant(0.5.rem),
//        gap = 0.75.rem,
//        outlineWidth = 0.px,
//        foreground = baseColorLight,
//        background = back,
//        outline = baseColorLight,
//        derivations = mapOf(
//            HeaderSemantic to {
//                it.withoutBack(font = title)
//            },
//            ImportantSemantic to {
//                when(it.background) {
//                    baseColor -> it.withBack(background = accentColor, foreground = Color.white)
//                    accentColor -> it.withBack(background = back, foreground = baseColorLight)
//                    else -> it.withBack(background = baseColor, foreground = Color.white)
//                }
//            },
//            CardSemantic to {
//                when(it.background) {
//                    baseColor -> it.withBack(background = accentColor, foreground = Color.white)
//                    accentColor -> it.withBack(background = back, foreground = baseColorLight)
//                    else -> it.withBack(background = baseColor, foreground = Color.white)
//                }
//            },
//            ListSemantic to {
//                val d: Map<Semantic, Semantic.(Theme) -> ThemeAndBack> = mapOf(
//                    CardSemantic to { it.withBack }
//                )
//                when(it.background) {
//                    baseColor -> it.withBack(
//                        background = accentColor,
//                        foreground = Color.white,
//                        cornerRadii = CornerRadii.ForceConstant(0.5.rem),
//                        derivations = d)
//                    accentColor -> it.withBack(
//                        background = back,
//                        foreground = baseColorLight,
//                        cornerRadii = CornerRadii.ForceConstant(0.5.rem),
//                        derivations = d)
//                    else -> it.withBack(
//                        background = baseColor,
//                        foreground = Color.white,
//                        cornerRadii = CornerRadii.ForceConstant(0.5.rem),
//                        derivations = d)
//                }
//            },
//            FieldSemantic to {
//                it.withBack(
//                    outlineWidth = 1.dp
//                )
//            },
//            BarSemantic to { it.withBack },
//            NavSemantic to { it.withBack },
//            OuterSemantic to { it.withBack(cascading = false, gap = 1.px, padding = Edges.ZERO, background = Color.gray(0.3f)) },
//            MainContentSemantic to { it.withBack },
//
//            DialogSemantic to {
//                it.withBack(outlineWidth = 1.dp, gap = 2.rem, cascading = false)
//            },
//        )
//    )
//}
//
//fun hackerman(color: Color, secondary: Color = color.toHSP().let { it.copy(hue = it.hue + 0.5.turns) }.toRGB()): Theme =
//    run {
//        Theme(
//            id = "hackermin-${color.toInt()}-${secondary.toInt()}",
//            font = FontAndStyle(systemDefaultFixedWidthFont),
//            background = Color.black,
//            foreground = color,
//            outline = color,
//            outlineWidth = 0.px,
//            gap = 0.5.rem,
//            padding = Edges(0.5.rem),
//            cornerRadii = CornerRadii.ForceConstant(0.px),
//            derivations = mapOf(
//                BarSemantic to { it.withBack },
//                NavSemantic to { it.withBack },
//                OuterSemantic to { it.withBack(cascading = false, gap = 1.px, padding = Edges.ZERO, background = color) },
//                FieldSemantic to { it.withBack(outlineWidth = 1.px, outline = color) },
//                CardSemantic to { it.withBack(outlineWidth = 1.px, outline = color.darken(0.7f)) },
//                MainContentSemantic to { it.withBack },
//                HoverSemantic to { it.withBack(outlineWidth = 1.px, outline = color) },
//                DownSemantic to { it.withBack(outlineWidth = 1.px, outline = color) },
//                SelectedSemantic to { it.withBack(outlineWidth = 1.px, outline = color) },
//                DialogSemantic to { it.withBack(outlineWidth = 1.px, outline = color, cascading = false) },
//                UnselectedSemantic to { it.withBack },
//
//                ImportantSemantic to {
//                    it.withBack(
//                        outline = secondary,
//                        outlineWidth = 1.px,
//                        foreground = secondary,
//                    )
//                },
//                WorkingSemantic to { it.withoutBack(foreground = FadingColor(color, color.darken(.2f))) },
//                LoadingSemantic to { it.withoutBack(foreground = FadingColor(color, color.darken(.2f))) },
//            )
//        )
//    }
//
//fun clouds(primary: Color): Theme = run {
//    Theme(
//        id = "clouds-${primary.toInt()}",
//        font = FontAndStyle(),
//        foreground = Color.gray(0.2f),
//        background = Color.gray(0.95f),
//        outlineWidth = 0.px,
//        elevation = 0.px,
//        cornerRadii = CornerRadii.ForceConstant(1.rem),
//        derivations = mapOf(
//            CardSemantic to { it.withBack(elevation = 1.dp, background = Color.white) },
//            BarSemantic to { it[CardSemantic] },
//            NavSemantic to { it[CardSemantic] },
//            MainContentSemantic to { it.withBack },
//            ImportantSemantic to {
//                val primaryFixed = primary.darken(0.3f)
//                it.withBack(
//                    background = primaryFixed,
//                    foreground = if (primaryFixed.perceivedBrightness > 0.4f) Color.white else Color.black,
//                )
//            },
//        )
//    )
//}
//
//fun obsidian(primary: Color): Theme = run {
//    fun g(l: Float = 0.05f) = LinearGradient(
//        listOf(
//            GradientStop(0f, Color.gray(l)),
//            GradientStop(0.25f, Color.gray(l + 0.03f)),
//            GradientStop(0.5f, Color.gray(l + 0.02f)),
//            GradientStop(0.57f, Color.gray(l + 0.06f)),
//            GradientStop(0.6f, Color.gray(l + 0.02f)),
//            GradientStop(0.7f, Color.gray(l + 0.08f)),
//            GradientStop(0.85f, Color.gray(l + 0.02f)),
//            GradientStop(1f, Color.gray(l)),
//        ),
//        angle = 0.05.turns,
//        screenStatic = false
//    )
//    Theme(
//        id = "obsidian-${primary.toInt()}",
//        font = FontAndStyle(),
//        foreground = Color.gray(0.90f),
//        background = g(0.10f),
//        outline = Color.gray(0.2f),
//        outlineWidth = 1.dp,
//        elevation = 0.px,
//        gap = 0.75.rem,
//        padding = Edges(0.75.rem),
//        cornerRadii = CornerRadii.ForceConstant(0.3.rem),
//        derivations = mapOf(
//            CardSemantic to { it.withBack(outlineWidth = 1.dp, background = g(0f), foreground = Color.gray(0.90f)) },
//            OuterSemantic to { it.withBack(cascading = false, gap = 0.px, padding = Edges.ZERO) },
//            BarSemantic to { it[CardSemantic] },
//            NavSemantic to { it[CardSemantic] },
//            MainContentSemantic to { it.withBack },
//            ImportantSemantic to {
//                val primaryFixed = primary.darken(0.3f)
//                it.withBack(
//                    background = primaryFixed,
//                    foreground = if (primaryFixed.perceivedBrightness > 0.4f) Color.white else Color.black,
//                )
//            },
//        )
//    )
//}
//
//fun defaultGradient(color: Color): Theme = run {
//    val baseColor = color.toHSP()
//    val hue: Angle = baseColor.hue
//    val accentHue: Angle = hue + Angle.halfTurn
//    val saturation: Float = baseColor.saturation
//    val baseBrightness: Float = baseColor.brightness - 0.1f
//    val brightnessStep: Float = 0.05f
//    val title: FontAndStyle = FontAndStyle()
//    val body: FontAndStyle = FontAndStyle()
//    fun g(color: Color, strength: Float = 0.1f) = LinearGradient(
//        listOf(
//            GradientStop(0f, color.lighten(strength)),
//            GradientStop(1f, color),
//        ),
//        angle = 0.25.turns,
//        screenStatic = false
//    )
//
//    fun Color.withBrightness(b: Float) = toHSP().copy(brightness = b).toRGB()
//    Theme(
//        id = "gradient-${color.toInt()}",
//        font = body,
//        elevation = 0.dp,
//        cornerRadii = CornerRadii.RatioOfSpacing(0.8f),
//        gap = 0.75.rem,
//        padding = Edges(0.75.rem),
//        outlineWidth = 0.px,
//        foreground = if (baseBrightness > 0.6f) Color.black else Color.white,
//        background = g(color.withBrightness(baseBrightness), 0.2f),
//        outline = HSPColor(hue = hue, saturation = saturation, brightness = 0.4f).toRGB(),
//        derivations = mapOf(
//            HeaderSemantic to {
//                it.withoutBack(font = title)
//            },
//            ImportantSemantic to {
//                val existing = it.background.closestColor().toHSP()
//                if (abs(existing.brightness - 0.5f) > brightnessStep * 3) {
//                    val b = existing.copy(brightness = 0.3f).toRGB()
//                    it.copy(
//                        id = "imp",
//                        foreground = b.highlight(1f),
//                        background = g(b),
//                        outline = b,
//                    )
//                } else {
//                    val closerToAccent =
//                        (existing.hue angleTo hue).turns.absoluteValue > (existing.hue angleTo accentHue).turns.absoluteValue
//                    val b = HSPColor(
//                        hue = if (closerToAccent) hue else accentHue,
//                        saturation = saturation,
//                        brightness = 0.5f
//                    ).toRGB()
//                    it.copy(
//                        id = "imp",
//                        foreground = b.highlight(1f),
//                        background = g(b),
//                        outline = b,
//                    )
//                }.withBack
//            },
//            CardSemantic to {
//                it.copy(
//                    id = "crd",
//                    background = it.background.closestColor().toHSP().let {
//                        it.copy(brightness = it.brightness + brightnessStep)
//                    }.toRGB().let(::g),
//                    outline = it.outline.closestColor().toHSP().let {
//                        it.copy(brightness = it.brightness + brightnessStep)
//                    }.toRGB().let(::g)
//                ).withBack
//            },
//            HoverSemantic to {
//                it.copy(id = "hov", background = it.background.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep)
//                }.toRGB().let(::g), outline = it.outline.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep)
//                }.toRGB(), outlineWidth = it.outlineWidth * 2).withBack
//            },
//            FocusSemantic to {
//                val o = it.outline.closestColor()
//                val b = it.background.closestColor()
//                if (b.alpha == 0f || abs(o.perceivedBrightness - b.perceivedBrightness) > 0.4) {
//                    it.copy(
//                        id = "fcs",
//                        outlineWidth = it.outlineWidth + 3.dp,
//                    )
//                } else {
//                    it.copy(
//                        id = "fcs",
//                        outlineWidth = it.outlineWidth + 3.dp,
//                        outline = Color.gray(baseBrightness).highlight(1f)
//                    )
//                }.withBack
//            },
//            DownSemantic to {
//                it.copy(id = "dwn", background = it.background.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep * 3)
//                }.toRGB().let(::g), outline = it.outline.closestColor().toHSP().let {
//                    it.copy(brightness = it.brightness + brightnessStep * 3)
//                }.toRGB(), outlineWidth = it.outlineWidth * 2).withBack
//            },
//
//            FieldSemantic to {
//                it.copy(
//                    id = "fld",
//                    outlineWidth = 1.px,
//                    background = it.background.closestColor(),
//                    revert = true,
////                gap = it.gap / 2,
//                    cornerRadii = when (val base = it.cornerRadii) {
//                        is CornerRadii.Constant -> CornerRadii.ForceConstant(base.value)
//                        is CornerRadii.ForceConstant -> base
//                        is CornerRadii.RatioOfSize -> base
//                        is CornerRadii.RatioOfSpacing -> CornerRadii.ForceConstant(it.gap * base.value)
//                        is CornerRadii.PerCorner -> base
//                    }
//                ).withBack
//            },
//            BarSemantic to { it[CardSemantic] },
//            NavSemantic to { it[CardSemantic] },
//            OuterSemantic to { it.withoutBack },
//            MainContentSemantic to { it.withoutBack },
//            DialogSemantic to {
//                it.copy(id = "dlg", outlineWidth = 1.dp, gap = 2.rem, revert = true).withBack
//            },
//        )
//    )
//}
//
//fun clean(primary: Color?): Theme = run {
//    val back = Color(red = 229f / 255f, green = 229f / 255f, blue = 234f / 255f, alpha = 1f)
//    val defaultColor = Color(red = 0 / 255f, green = 122 / 255f, blue = 255 / 255f, alpha = 1f)
//    val highlight = primary ?: defaultColor
//    val separator = back.darken(0.1f)
//    fun Paint.backInvert() = if (this == Color.white) back else Color.white
//    Theme(
//        id = "clean-${highlight.toInt()}",
//        font = FontAndStyle(font = Resources.helvetica, additionalLetterSpacing = (-0.5).dp),
//        foreground = Color.black,
//        background = Color.white,
//        elevation = 0.px,
//        cornerRadii = CornerRadii.Constant(0.5.rem),
//        gap = 0.75.rem,
//        padding = Edges(0.75.rem),
//        derivations = mapOf(
//            CardSemantic to { it.withBack(background = it.background.backInvert(), foreground = Color.black) },
//            FieldSemantic to {
//                it.withBack(
//                    outline = separator,
//                    outlineWidth = 1.px,
//                    foreground = Color.black,
//                    cornerRadii = CornerRadii.ForceConstant(0.5.rem)
//                )
//            },
//            BarSemantic to { it.withBack },
//            NavSemantic to { it.withBack },
//            OuterSemantic to { it.withBack(cascading = false, gap = 1.px, padding = Edges.ZERO, background = separator) },
//            MainContentSemantic to { it.withBack },
//            InsetSemantic to { it.withBack(background = it.background.backInvert()) },
//            UnselectedSemantic to { it.withBack },
//            SelectedSemantic to { it[CardSemantic] },
//            DialogSemantic to {
//                it.withBack(
//                    cascading = false,
//                    outline = separator,
//                    background = Color.white,
//                    foreground = Color.black,
//                    elevation = 4.dp
//                )
//            },
//            ImportantSemantic to {
//                it.withBack(
//                    background = highlight,
//                    foreground = if (highlight.perceivedBrightness > 0.4f) Color.white else Color.black,
//                )
//            },
//            ListSemantic to {
//                it.copy(id = "lsts", background = back).withBack(
//                    cascading = false,
//                    cornerRadii = CornerRadii.ForceConstant(0.75.rem),
//                    gap = 1.px,
//                    padding = Edges(0.px)
//                )
//            }
//        )
//    )
//}
//fun material(primary: Color): Theme = run {
//    Theme(
//        id = "material-${primary.toInt()}",
//        font = FontAndStyle(),
//        foreground = Color.gray(0.2f),
//        background = Color.gray(0.95f),
//        outline = Color.gray(0.75f),
//        separatorOverride = Color.gray(0.75f),
//        outlineWidth = 0.px,
//        elevation = 0.px,
//        gap = 0.75.rem,
//        padding = Edges(0.75.rem),
//        cornerRadii = CornerRadii.Constant(0.75.rem),
//        derivations = mapOf(
//            FieldSemantic to { it.withBack(outlineWidth = 1.dp, background = Color.white, foreground = Color.gray(0.2f)) },
//            CardSemantic to { it.withBack(elevation = 1.dp, background = Color.white) },
//            BarSemantic to { it[ImportantSemantic] },
//            NavSemantic to { it.withBack(cornerRadii = CornerRadii.ForceConstant(0.px)) },
//            MainContentSemantic to { it.withBack },
//            OuterSemantic to { it.withBack(cascading = false, gap = 1.dp, padding = Edges.ZERO, background = it.separator) },
//            ImportantSemantic to {
//                it.withBack(
//                    background = primary,
//                    foreground = if (primary.perceivedBrightness > 0.4f) Color.white else Color.black,
//                )
//            },
//        )
//    )
//}
//
//@Serializable
//enum class ThemePreference(val display: String, val theme: (Color?) -> Theme) {
//    Default(display = "Default", theme = { default(it) }),
//    LightningKite(display = "Lightning Kite", theme = { lk() }),
//    Elsie(display = "Elsie", theme = { elsie() }),
//    ElsieCalm(display = "Elsie Calm", theme = { elsieCalm() }),
//    DefaultGradient(display = "Gradient", theme = { defaultGradient((it ?: Color.blue).darken(0.7f)) }),
//    DefaultGradientLight(display = "GradientLight", theme = { defaultGradient((it ?: Color.blue).lighten(0.8f)) }),
//    Material(display = "Material", theme = { material(it ?: HSPColor(hue = 0.7.turns, saturation = 0.8f, brightness = 0.8f).toRGB()) }),
//    Hackerman(display = "Hackerman", theme = { hackerman(it ?: Color.green) }),
//    Clouds(
//        display = "Clouds",
//        theme = { clouds(it ?: HSPColor(hue = 0.7.turns, saturation = 0.8f, brightness = 0.8f).toRGB()) }),
//    Clean(
//        display = "Clean",
//        theme = { clean(it ?: HSPColor(hue = 0.7.turns, saturation = 0.8f, brightness = 0.8f).toRGB()) }),
//    Obsidian(
//        display = "Obsidian",
//        theme = { obsidian(it ?: HSPColor(hue = 0.7.turns, saturation = 0.5f, brightness = 0.8f).toRGB()) }),
//}
//
//val themePreferenceColor = PersistentProperty<Int?>("theme-preference-color", null).lens(
//    get = { it?.let { Color.fromInt(it) } },
//    set = { it?.toInt() }
//)
//val themePreference = PersistentProperty("theme-preference", ThemePreference.Default, ThemePreference.serializer())
//val appTheme = remember { themePreference().theme(themePreferenceColor()) }
//val appTheme = Constant(Theme.clean(null))
val appTheme = Constant(lk())