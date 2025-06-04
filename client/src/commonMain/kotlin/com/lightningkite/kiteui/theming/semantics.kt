package com.lightningkite.kiteui.theming

import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.models.Semantic
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.ThemeAndBack
import kotlinx.serialization.Serializable
import kotlin.time.Duration


@Serializable
data class ThemeOperation(
    val key: String,
    val withBackground: Boolean = false,
    val withPadding: Boolean = false,
    val font: Operation<FontStyle>? = null,
    val elevation: FieldOperator<DimensionRem>? = null,
    val cornerRadii: Operation<CornerRadiiSerializable>? = null, // TODO
    val gap: FieldOperator<DimensionRem>? = null,
    val padding: Operation<Padding>? = null,
    val foreground: FieldOperator<Paint>? = null,
    val background: FieldOperator<Paint>? = null,
    val iconOverride: FieldOperator<Paint>? = null,
    val seperatorOverride: FieldOperator<Paint>? = null,
    val outline: FieldOperator<Paint>? = null,
    val outlineWidth: FieldOperator<DimensionRem>? = null,
    val transitionDuration: Operation<Duration>? = null,
    val derivations: List<ThemeOperation> = emptyList()
) : Operation<ThemeData> {
    @Serializable
    data class FieldOperator<T>(
        val value: ThemeData.Getter<T>,
        val operation: Operation<T> = Operation.Identity()
    ) {
        operator fun invoke(on: ThemeData) = operation(on[value])
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
        transitionDuration = transitionDuration?.invoke(on.transitionDuration) ?: on.transitionDuration
    )

    fun invoke(on: Theme): ThemeAndBack = ThemeAndBack(
        on.copy(
            id = key,
            font = font?.invoke(on.font) ?: on.font,
            elevation = elevation?.invoke()
        )
    )
}

fun List<ThemeOperation>.toSemanticMap(): Map<Semantic, Semantic.(Theme) -> ThemeAndBack> = associate { operation ->
    val semantic = SemanticRegistry[operation.key]

    semantic to { theme ->
        val altered = theme.alter(
            font =
        )
    }
}

object SemanticRegistry {
    private val registry = HashMap<String, Semantic>()
    fun register(semantic: Semantic) {
        registry[semantic.key] = semantic
    }
    operator fun get(key: String) = registry[key] ?: object : Semantic(key)
}