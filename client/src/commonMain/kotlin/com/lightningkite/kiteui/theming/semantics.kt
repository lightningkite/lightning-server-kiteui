package com.lightningkite.kiteui.theming

import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.models.Semantic
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.ThemeAndBack
import com.lightningkite.kiteui.models.px
import kotlinx.serialization.Serializable
import kotlin.time.Duration


@Serializable
data class ThemeOperation(
    val key: String,
    val withBackground: Boolean = false,
    val withPadding: Boolean = false,
    val cascading: Boolean = true,
    val font: Operation<FontStyle>? = null,
    val elevation: DimensionOperation? = null,
    val cornerRadii: Operation<CornerRadiiSerializable>? = null, // TODO
    val gap: DimensionOperation? = null,
    val padding: Operation<Padding>? = null,
    val foreground: PaintOperation? = null,
    val background: PaintOperation? = null,
    val iconOverride: PaintOperation? = null,
    val seperatorOverride: PaintOperation? = null,
    val outline: PaintOperation? = null,
    val outlineWidth: DimensionOperation? = null,
    val transitionDuration: Operation<Duration>? = null,
    val derivations: Map<String, ThemeOperation> = emptyMap()
) : Operation<ThemeData> {
    @Serializable
    data class DimensionOperation(
        val value: DimensionGetter,
        val operation: Operation<DimensionPx> = Operation.Identity()
    ) {
        operator fun invoke(on: ThemeData) = operation(on[value])
        operator fun invoke(on: Theme) = operation(on[value].px).toDimension()
    }

    @Serializable
    data class PaintOperation(
        val value: PaintGetter,
        val operation: Operation<Paint> = Operation.Identity()
    ) {
        operator fun invoke(on: ThemeData) = operation(on[value])
        operator fun invoke(on: Theme) = operation(on[value])
    }

    override fun invoke(on: ThemeData): ThemeData = ThemeData(
        font = font?.invoke(on.font) ?: on.font,
        elevation = elevation?.invoke(on) ?: on.elevation,
        cornerRadii = cornerRadii?.invoke(on.cornerRadii) ?: on.cornerRadii,
        gap = gap?.invoke(on) ?: on.gap,
        padding = padding?.invoke(on.padding) ?: on.padding,
        foreground = foreground?.invoke(on) ?: on.foreground,
        background = background?.invoke(on) ?: on.background,
        iconOverride = iconOverride?.invoke(on) ?: on.iconOverride,
        separatorOverride = seperatorOverride?.invoke(on) ?: on.separatorOverride,
        outline = outline?.invoke(on) ?: on.outline,
        outlineWidth = outlineWidth?.invoke(on) ?: on.outlineWidth,
        transitionDuration = transitionDuration?.invoke(on.transitionDuration) ?: on.transitionDuration,
        derivations = on.derivations + derivations
    )

    operator fun invoke(on: Theme): ThemeAndBack = on.copy(
        id = key,
        cascading = cascading,
        font = font?.invoke(on.font),
        elevation = elevation?.invoke(on),
        cornerRadii = cornerRadii?.invoke(on.cornerRadii.serializable())?.toCornerRadii(),
        gap = gap?.invoke(on),
        padding = padding?.invoke(Padding(on.padding))?.toEdges(),
        foreground = foreground?.invoke(on),
        iconOverride = iconOverride?.invoke(on),
        outline = outline?.invoke(on),
        outlineWidth = outlineWidth?.invoke(on),
        separatorOverride = seperatorOverride?.invoke(on),
        background = background?.invoke(on),
        transitionDuration = transitionDuration?.invoke(on.transitionDuration),
        derivations = derivations.values.toSemanticMap()
    ).with(withBackground, withPadding)
}

fun Collection<ThemeOperation>.toSemanticMap(): Map<Semantic, Semantic.(Theme) -> ThemeAndBack> =
    associate { operation ->
        val semantic = Semantic.Registry[operation.key]
            ?: object : Semantic(operation.key) {}

        semantic to { operation(it) }
    }