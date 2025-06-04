package com.lightningkite.kiteui.theming

import com.lightningkite.kiteui.models.Color
import com.lightningkite.kiteui.models.CornerRadii
import com.lightningkite.kiteui.models.Dimension
import com.lightningkite.kiteui.models.Edges
import com.lightningkite.kiteui.models.FontAndStyle
import com.lightningkite.kiteui.models.LinearGradient
import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.models.RadialGradient
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.models.systemDefaultFont
import com.lightningkite.lightningdb.MySealedClassSerializer
import com.lightningkite.lightningdb.MySealedClassSerializerInterface
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias DimensionRem = Double

// I'm not even going to try to serialize a font.
@Serializable
data class FontStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val size: DimensionRem = 1.0
) {
    operator fun invoke(font: FontAndStyle) = font.copy(
        weight = if (bold) 700 else 400,
        italic = italic,
        underline = underlined,
        strikethrough = strikethrough,
        size = size.rem
    )
}

object PaintSerializer : MySealedClassSerializerInterface<Paint> by MySealedClassSerializer(
    serialName = "com.lightningkite.kiteui.models.Paint",
    options = {
        listOf(
            MySealedClassSerializer.Option(Color.serializer()) { it is Color },
            MySealedClassSerializer.Option(LinearGradient.serializer()) { it is LinearGradient },
            MySealedClassSerializer.Option(RadialGradient.serializer()) { it is RadialGradient }
        )
    }
)


@Serializable(CornerRadiiSerializable.Serializer::class)
sealed interface CornerRadiiSerializable {
    fun toCornerRadii(): CornerRadii

    @Serializable
    data class Constant(val value: DimensionRem) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.Constant(value.rem)
    }
    @Serializable
    data class RatioOfSpacing(val value: Float) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.RatioOfSpacing(value)
    }
    @Serializable
    data class ForceConstant(val value: DimensionRem) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.ForceConstant(value.rem)
    }
    @Serializable
    data class RatioOfSize(val ratio: Float = 0.5f) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.RatioOfSize(ratio)
    }
    @Serializable
    data class PerCorner(
        val value: DimensionRem,
        val topLeft: Boolean = false,
        val topRight: Boolean = false,
        val bottomLeft: Boolean = false,
        val bottomRight: Boolean = false,
    ) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.PerCorner(value.rem, topLeft, topRight, bottomLeft, bottomRight)
    }

    object Serializer : MySealedClassSerializerInterface<CornerRadiiSerializable> by MySealedClassSerializer(
        serialName = "com.lightningkite.kiteui.models.CornerRadii",
        options = {
            listOf(
                MySealedClassSerializer.Option(Constant.serializer()) { it is Constant },
                MySealedClassSerializer.Option(ForceConstant.serializer()) { it is ForceConstant },
                MySealedClassSerializer.Option(RatioOfSpacing.serializer()) { it is RatioOfSpacing },
                MySealedClassSerializer.Option(RatioOfSize.serializer()) { it is RatioOfSize },
                MySealedClassSerializer.Option(PerCorner.serializer()) { it is PerCorner },
            )
        }
    )
}

@Serializable
data class Padding(
    val left: DimensionRem = 1.0,
    val top: DimensionRem = 1.0,
    val right: DimensionRem = 1.0,
    val bottom: DimensionRem = 1.0
) {
    constructor(horizontal: DimensionRem, vertical: DimensionRem) : this(left = horizontal, right = horizontal, top = vertical, bottom = vertical)
    constructor(dimension: DimensionRem) : this(dimension, dimension)

    operator fun minus(other: Padding) = Padding(left - other.left, top - other.top, right - other.right, bottom - other.bottom)
    operator fun times(other: Int) = Padding(left * other, top * other, right * other, bottom * other)
    operator fun div(other: Int) = Padding(left / other, top / other, right / other, bottom / other)
    operator fun times(other: Float) = Padding(left * other, top * other, right * other, bottom * other)
    operator fun div(other: Float) = Padding(left / other, top / other, right / other, bottom / other)
    operator fun times(other: Double) = Padding(left * other, top * other, right * other, bottom * other)
    operator fun div(other: Double) = Padding(left / other, top / other, right / other, bottom / other)

    fun toEdges() = Edges(left.rem, top.rem, right.rem, bottom.rem)
}