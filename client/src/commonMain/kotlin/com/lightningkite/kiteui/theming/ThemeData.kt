package com.lightningkite.kiteui.theming

import com.lightningkite.kiteui.models.Color
import com.lightningkite.kiteui.models.Dimension
import com.lightningkite.kiteui.models.FontAndStyle
import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.models.systemDefaultFont
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


@Serializable
data class ThemeData(
    val font: FontStyle = FontStyle(),
    val elevation: DimensionPx = 0.0,
    val cornerRadii: CornerRadiiSerializable = CornerRadiiSerializable.RatioOfSpacing(1f),
    val gap: DimensionPx = 1.0,
    val padding: Padding = Padding(gap),

    @Serializable(PaintSerializer::class) val foreground: Paint = Color.black,
    @Serializable(PaintSerializer::class) val background: Paint = Color.white,

    @Serializable(PaintSerializer::class) val iconOverride: Paint? = null,
    @Serializable(PaintSerializer::class) val separatorOverride: Paint? = null,

    @Serializable(PaintSerializer::class) val outline: Paint = Color.black,
    val outlineWidth: DimensionPx = 0.0,

    val transitionDuration: Duration = 0.25.seconds,

    val derivations: Map<String, ThemeOperation> = emptyMap()
) {
    interface Getter<D, S> {
        val displayName: String
        val themeData: (ThemeData) -> D
        val rawTheme: (Theme) -> S
    }

    operator fun <T> get(value: Getter<T, *>) = value.themeData(this)

    fun toTheme(id: String) = Theme(
        id,
        font(FontAndStyle(systemDefaultFont)),
        elevation.rem,
        cornerRadii.toCornerRadii(),
        gap.rem,
        padding.toEdges(),
        foreground,
        iconOverride,
        outline,
        outlineWidth.rem,
        separatorOverride,
        background,
        transitionDuration = transitionDuration,
        derivations = derivations.values.toSemanticMap()
    )
}

operator fun <T> Theme.get(getter: ThemeData.Getter<*, T>): T = getter.rawTheme(this)

@Serializable
enum class PaintGetter(
    override val displayName: String,
    override val themeData: (ThemeData) -> Paint,
    override val rawTheme: (Theme) -> Paint
) : ThemeData.Getter<Paint, Paint> {
    Foreground("Foreground", ThemeData::foreground, Theme::foreground),
    Background("Background", ThemeData::background, Theme::background),
    Outline("Outline", ThemeData::outline, Theme::outline),
    IconOverride("Icon Override", { it.iconOverride ?: it.foreground }, { it.iconOverride ?: it.foreground }),
    SeperatorOverride("Seperator Override", { it.separatorOverride ?: it.foreground }, { it.separatorOverride ?: it.foreground })
}

@Serializable
enum class DimensionGetter(
    override val displayName: String,
    override val themeData: (ThemeData) -> DimensionPx,
    override val rawTheme: (Theme) -> Dimension
) : ThemeData.Getter<DimensionPx, Dimension> {
    Elevation("Elevation", ThemeData::elevation, Theme::elevation),
    Gap("Gap", ThemeData::gap, Theme::gap),
    OutlineWidth("Outline Width", ThemeData::outlineWidth, Theme::outlineWidth)
}