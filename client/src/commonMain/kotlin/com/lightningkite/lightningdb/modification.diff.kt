package com.lightningkite.lightningdb

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

inline fun <reified T> modification(old: T, new: T): Modification<T>? = modification(serializerOrContextual(), old, new)
fun <T> modification(serializer: KSerializer<T>, old: T, new: T): Modification<T>? {
    if(old == new) return null
    if(old == null || new == null) return Modification.Assign(new)
    return (serializer.nullElement() ?: serializer).serializableProperties?.let {
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
