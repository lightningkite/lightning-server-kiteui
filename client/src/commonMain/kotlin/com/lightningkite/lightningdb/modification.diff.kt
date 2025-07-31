@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningdb

import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.nullElement
import com.lightningkite.serialization.serializableProperties
import com.lightningkite.serialization.serializerOrContextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

inline fun <reified T> modification(old: T, new: T): Modification<T>? = modification(serializerOrContextual(), old, new)
fun <T> modification(serializer: KSerializer<T>, old: T, new: T): Modification<T>? = run {
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
}
