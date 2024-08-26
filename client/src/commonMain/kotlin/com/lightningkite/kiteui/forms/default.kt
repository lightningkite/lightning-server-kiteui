package com.lightningkite.kiteui.forms

import com.lightningkite.*
import com.lightningkite.lightningdb.*
import kotlinx.datetime.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule

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
private class MinEncoder() : AbstractEncoder() {
    var out: Any? = null
    override val serializersModule: SerializersModule = FormRenderer.module
    var index = -1
    override fun encodeValue(value: Any) {
        if(index == -1) out = value
        else (out as MutableMap<Int, Any?>)[index] = value
    }
    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
//        val opt = descriptor.isElementOptional(index)
        this.index = index
        return true
    }
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return if(index == -1) {
            out = mutableMapOf<Int, Any?>()
            this
        } else {
            val l = MinEncoder()
            val m = mutableMapOf<Int, Any?>()
            l.index = 0
            l.out = m
            (out as MutableMap<Int, Any?>)[index] = m
            l
        }
    }

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = true
    override fun encodeNull() {
        if(index == -1) out = null
        else (out as MutableMap<Int, Any?>)[index] = null
    }
}
@OptIn(ExperimentalSerializationApi::class)
private class MinDecoder(var item: Any?) : AbstractDecoder() {
    override val serializersModule: SerializersModule = FormRenderer.module
    var lastIndex: Int = -1
    var lastValue: Any? = item
    val indexIter by lazy { (item as Map<Int, Any?>).iterator() }
    override fun decodeValue(): Any = lastValue!!
    override fun decodeNotNullMark(): Boolean = lastValue != null
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (!indexIter.hasNext()) return CompositeDecoder.DECODE_DONE
        val l = indexIter.next()
        lastIndex = l.key
        lastValue = l.value
        return l.key
    }
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if(lastValue == null) return MinDecoder(mapOf<Int, Any?>())
        @Suppress("UNCHECKED_CAST")
        return MinDecoder(lastValue!! as Map<Int, Any?>)
    }
}
internal fun <T, V> KSerializer<T>.get(instance: T, index: Int, childSerializer: KSerializer<V>): V {
    val e = MinEncoder()
    this.serialize(e, instance)
    val encodedValue = (e.out as Map<Int, Any?>)[index]
    val d = MinDecoder(encodedValue)
    return childSerializer.deserialize(d)
}
internal fun <T, V> KSerializer<T>.set(instance: T, index: Int, childSerializer: KSerializer<V>, value: V): T {
    val e = MinEncoder()
    this.serialize(e, instance)
    val e2 = MinEncoder()
    childSerializer.serialize(e2, value)
    val eo = e.out as MutableMap<Int, Any?>
    eo[index] = e2.out
    @Suppress("UNCHECKED_CAST") val d = MinDecoder(e.out)
    return deserialize(d)
}