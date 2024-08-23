package com.lightningkite.kiteui.forms

import com.lightningkite.*
import com.lightningkite.lightningdb.*
import kotlinx.datetime.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Duration.Companion.seconds

private class EnumValueGetter(var index: Int = 0): Decoder {
    override val serializersModule: SerializersModule
        get() = DefaultDecoder.serializersModule

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        TODO("Not yet implemented")
    }

    override fun decodeBoolean(): Boolean {
        TODO("Not yet implemented")
    }

    override fun decodeByte(): Byte {
        TODO("Not yet implemented")
    }

    override fun decodeChar(): Char {
        TODO("Not yet implemented")
    }

    override fun decodeDouble(): Double {
        TODO("Not yet implemented")
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = index

    override fun decodeFloat(): Float {
        TODO("Not yet implemented")
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        TODO("Not yet implemented")
    }

    override fun decodeInt(): Int {
        TODO("Not yet implemented")
    }

    override fun decodeLong(): Long {
        TODO("Not yet implemented")
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        TODO("Not yet implemented")
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        TODO("Not yet implemented")
    }

    override fun decodeShort(): Short {
        TODO("Not yet implemented")
    }

    override fun decodeString(): String {
        TODO("Not yet implemented")
    }

}

fun <T> KSerializer<T>.enumValues(): List<T> {
    val e = EnumValueGetter(0)
    return (0..<descriptor.elementsCount).map {
        e.index = it
        deserialize(e)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun <T: Any> SerializersModule.getContextual(contextualSerializer: ContextualSerializer<T>): KSerializer<T> {
    try {
        contextualSerializer.deserialize(object : AbstractDecoder() {
            override val serializersModule: SerializersModule get() = this@getContextual
            override fun decodeElementIndex(descriptor: SerialDescriptor): Int = 0
            override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
                throw DesWrap(deserializer)
            }
        })
    } catch(e: DesWrap) {
        @Suppress("UNCHECKED_CAST")
        return e.deserializer as KSerializer<T>
    }
    throw IllegalStateException()
}
private class DesWrap(val deserializer: DeserializationStrategy<*>): Exception()

@OptIn(ExperimentalSerializationApi::class)
internal class ListEncoder : AbstractEncoder() {
    val list = mutableListOf<Any?>()
    override val serializersModule: SerializersModule = EmptySerializersModule()
    override fun encodeValue(value: Any) {
        list.add(value)
    }
    override fun encodeNull() {
        list.add(null)
    }
}
@OptIn(ExperimentalSerializationApi::class)
internal class ListDecoder(val list: ArrayDeque<Any?>) : AbstractDecoder() {
    private var elementIndex = 0
    override val serializersModule: SerializersModule = EmptySerializersModule()
    override fun decodeNotNullMark(): Boolean = list[elementIndex] != null
    override fun decodeValue(): Any = list.removeFirst()!!
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        ListDecoder(list)
}
internal fun <T> KSerializer<T>.get(instance: T, index: Int): Any? {
    val e = ListEncoder()
    this.serialize(e, instance)
    return e.list[index]
}
internal fun <T> KSerializer<T>.set(instance: T, index: Int, value: Any?): T {
    val e = ListEncoder()
    this.serialize(e, instance)
    e.list[index] = value
    return this.deserialize(ListDecoder(ArrayDeque(e.list)))
}