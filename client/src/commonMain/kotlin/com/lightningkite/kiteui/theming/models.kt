package com.lightningkite.kiteui.theming

import com.lightningkite.kiteui.models.Color
import com.lightningkite.kiteui.models.CornerRadii
import com.lightningkite.kiteui.models.Edges
import com.lightningkite.kiteui.models.FontAndStyle
import com.lightningkite.kiteui.models.LinearGradient
import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.models.RadialGradient
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.lightningdb.MySealedClassSerializer
import com.lightningkite.lightningdb.MySealedClassSerializerInterface
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

typealias DimensionPx = Double

fun DimensionPx.toDimension() = this.roundToInt().px

// I'm not even going to try to serialize a font.
@Serializable
data class FontStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val size: DimensionPx = 1.0
) {
    constructor(font: FontAndStyle) : this(font.bold, font.italic, font.underline, font.strikethrough, font.size.px)
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
    data class Constant(val value: DimensionPx) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.Constant(value.rem)
    }
    @Serializable
    data class RatioOfSpacing(val value: Float) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.RatioOfSpacing(value)
    }
    @Serializable
    data class ForceConstant(val value: DimensionPx) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.ForceConstant(value.rem)
    }
    @Serializable
    data class RatioOfSize(val ratio: Float = 0.5f) : CornerRadiiSerializable {
        override fun toCornerRadii() = CornerRadii.RatioOfSize(ratio)
    }
    @Serializable
    data class PerCorner(
        val value: DimensionPx,
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

fun CornerRadii.serializable(): CornerRadiiSerializable = when(this) {
    is CornerRadii.Constant -> CornerRadiiSerializable.Constant(value.px)
    is CornerRadii.ForceConstant -> CornerRadiiSerializable.ForceConstant(value.px)
    is CornerRadii.PerCorner -> CornerRadiiSerializable.PerCorner(value.px, topLeft, topRight, bottomLeft, bottomRight)
    is CornerRadii.RatioOfSize -> CornerRadiiSerializable.RatioOfSize(ratio)
    is CornerRadii.RatioOfSpacing -> CornerRadiiSerializable.RatioOfSpacing(value)
}

@Serializable
data class Padding(
    val left: DimensionPx = 1.0,
    val top: DimensionPx = 1.0,
    val right: DimensionPx = 1.0,
    val bottom: DimensionPx = 1.0
) {
    constructor(horizontal: DimensionPx, vertical: DimensionPx) : this(left = horizontal, right = horizontal, top = vertical, bottom = vertical)
    constructor(dimension: DimensionPx) : this(dimension, dimension)
    constructor(edges: Edges) : this(edges.left.px, edges.top.px, edges.right.px, edges.bottom.px)

    operator fun minus(other: Padding) = Padding(left - other.left, top - other.top, right - other.right, bottom - other.bottom)
    operator fun times(other: Int) = Padding(left * other, top * other, right * other, bottom * other)
    operator fun div(other: Int) = Padding(left / other, top / other, right / other, bottom / other)
    operator fun times(other: Float) = Padding(left * other, top * other, right * other, bottom * other)
    operator fun div(other: Float) = Padding(left / other, top / other, right / other, bottom / other)
    operator fun times(other: Double) = Padding(left * other, top * other, right * other, bottom * other)
    operator fun div(other: Double) = Padding(left / other, top / other, right / other, bottom / other)

    fun toEdges() = Edges(left.toDimension(), top.toDimension(), right.toDimension(), bottom.toDimension())
}