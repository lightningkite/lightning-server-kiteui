package com.lightningkite.kiteui.theming

import com.lightningkite.kiteui.models.FontAndStyle
import com.lightningkite.kiteui.models.Paint
import com.lightningkite.kiteui.models.applyAlpha
import com.lightningkite.kiteui.models.darken
import com.lightningkite.kiteui.models.lighten
import com.lightningkite.lightningdb.DisplayName
import com.lightningkite.lightningdb.MySealedClassSerializer
import com.lightningkite.lightningdb.MySealedClassSerializerInterface
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable(OperationSerializer::class)
sealed interface Operation<T> {
    operator fun invoke(on: T): T

    @Serializable
    @DisplayName("")
    class Identity<T>() : Operation<T> {
        override fun invoke(on: T): T = on
    }

    @Serializable
    @DisplayName("Set")
    data class Set<T>(val value: T) : Operation<T> {
        override fun invoke(on: T): T = value
    }

    @Serializable
    @DisplayName("Chain")
    data class Chain<T>(val operations: List<Operation<T>>) : Operation<T> {
        override fun invoke(on: T): T = operations.fold(on) { acc, operation -> operation(acc) }
    }

    companion object {
        fun <T> commonOptions(inner: KSerializer<T>): List<MySealedClassSerializer.Option<Operation<T>, out Operation<T>>> = listOf(
            MySealedClassSerializer.Option(Chain.serializer(inner)) { it is Chain },
            MySealedClassSerializer.Option(Identity.serializer(inner)) { it is Identity },
            MySealedClassSerializer.Option(Set.serializer(inner)) { it is Set }
        )
    }
}

internal data object PaintOperations {
    @Serializable
    @DisplayName("Lighten")
    data class Lighten(val ratio: Float) : Operation<Paint> {
        override fun invoke(on: Paint): Paint = on.lighten(ratio)
    }

    @Serializable
    @DisplayName("Darken")
    data class Darken(val ratio: Float) : Operation<Paint> {
        override fun invoke(on: Paint): Paint = on.darken(ratio)
    }

    @Serializable
    @DisplayName("Apply Alpha")
    data class ApplyAlpha(val alpha: Float) : Operation<Paint> {
        override fun invoke(on: Paint): Paint = on.applyAlpha(alpha)
    }

    val options: List<MySealedClassSerializer.Option<Operation<Paint>, out Operation<Paint>>> =
        Operation.commonOptions(PaintSerializer) + listOf(
            MySealedClassSerializer.Option(Lighten.serializer()) { it is Lighten },
            MySealedClassSerializer.Option(Darken.serializer()) { it is Darken },
            MySealedClassSerializer.Option(ApplyAlpha.serializer()) { it is ApplyAlpha },
        )
}

internal data object DoubleOperations {
    @Serializable
    @DisplayName("Plus")
    data class Add(val other: Double) : Operation<Double> {
        override fun invoke(on: Double): Double = on.plus(other)
    }

    @Serializable
    @DisplayName("Minus")
    data class Subtract(val other: Double) : Operation<Double> {
        override fun invoke(on: Double): Double = on.minus(other)
    }

    @Serializable
    @DisplayName("Times")
    data class Multiply(val other: Double) : Operation<Double> {
        override fun invoke(on: Double): Double = on.times(other)
    }

    @Serializable
    @DisplayName("Divide")
    data class Divide(val other: Double) : Operation<Double> {
        override fun invoke(on: Double): Double = on.div(other)
    }

    val options: List<MySealedClassSerializer.Option<Operation<Double>, out Operation<Double>>> =
        Operation.commonOptions(Double.serializer()) + listOf(
            MySealedClassSerializer.Option(Add.serializer()) { it is Add },
            MySealedClassSerializer.Option(Subtract.serializer()) { it is Subtract },
            MySealedClassSerializer.Option(Multiply.serializer()) { it is Multiply },
            MySealedClassSerializer.Option(Divide.serializer()) { it is Divide },
        )
}

internal data object FontStyleOperations {
    @Serializable
    @DisplayName("Set Size")
    data class SetSize(val size: DimensionPx) : Operation<FontStyle> {
        override fun invoke(on: FontStyle): FontStyle = on.copy(size = size)
    }

    @Serializable
    @DisplayName("Bold")
    data object Bold : Operation<FontStyle> {
        override fun invoke(on: FontStyle): FontStyle = on.copy(bold = true)
    }

    @Serializable
    @DisplayName("Italic")
    data object Italic : Operation<FontStyle> {
        override fun invoke(on: FontStyle): FontStyle = on.copy(italic = true)
    }

    @Serializable
    @DisplayName("Underlined")
    data object Underlined : Operation<FontStyle> {
        override fun invoke(on: FontStyle): FontStyle = on.copy(underlined = true)
    }

    val options: List<MySealedClassSerializer.Option<Operation<FontStyle>, out Operation<FontStyle>>> =
        Operation.commonOptions(FontStyle.serializer()) + listOf(
            MySealedClassSerializer.Option(SetSize.serializer()) { it is SetSize },
            MySealedClassSerializer.Option(Bold.serializer()) { it === Bold },
            MySealedClassSerializer.Option(Italic.serializer()) { it === Italic },
            MySealedClassSerializer.Option(Underlined.serializer()) { it === Underlined },
        )
}

operator fun Operation<FontStyle>.invoke(font: FontAndStyle): FontAndStyle {
    val style = FontStyle(font)
    val modifiedStyle = this.invoke(style)
    return modifiedStyle(font)
}

class OperationSerializer<T>(val inner: KSerializer<T>) : MySealedClassSerializerInterface<Operation<T>> by MySealedClassSerializer<Operation<T>>(
    serialName = "com.lightningkite.kiteui.theming.Operation",
    options = {
        when (inner.descriptor.serialName) {
            "com.lightningkite.kiteui.models.Paint" -> PaintOperations.options
            "kotlin.Double" -> DoubleOperations.options
            "com.lightningkite.kiteui.theming.FontStyle" -> FontStyleOperations.options

            else -> Operation.commonOptions(inner)
        } as List<MySealedClassSerializer.Option<Operation<T>, out Operation<T>>>
    }
)