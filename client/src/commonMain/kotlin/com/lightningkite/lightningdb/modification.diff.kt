@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningdb

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import com.lightningkite.serialization.*

inline fun <reified T> modification(old: T, new: T): Modification<T>? = modification(serializerOrContextual(), old, new)
fun <T> modification(serializer: KSerializer<T>, old: T, new: T): Modification<T>? = run {
    println("modification ${serializer.descriptor.serialName} $old $new")
    if(old == new) return@run null
    if(old == null || new == null) return@run Modification.Assign(new)
    return@run (serializer.nullElement() ?: serializer).serializableProperties?.let {
        Modification.Chain<T>(it.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            it as SerializableProperty<T, Any?>
            val oldValue = it.get(old as T)
            val newValue = it.get(new as T)
            val inner = modification(it.serializer, oldValue, newValue)?.let { mod ->
                if(it.serializer.descriptor.isNullable && mod !is Modification.Assign<*>)
                    Modification.IfNotNull(mod)
                else mod
            } ?: return@mapNotNull null
            Modification.OnField(it, inner)
        })
    } ?: Modification.Assign<T>(new)
}.also {
    println("modification ${serializer.descriptor.serialName} $old $new -> $it")
}
