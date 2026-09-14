package com.lightningkite.lightningserver.admin

import com.lightningkite.services.database.*
import kotlinx.serialization.KSerializer

public fun <T> Array<SerializableProperty<T, *>>.walkDataClassPathsLimitedRecursion(serializer: KSerializer<T>): Sequence<DataClassPath<T, *>> {
    return sequence<DataClassPath<T, *>> {
        val seen = HashSet<SerializableProperty<*, *>>()
        val s = DataClassPathSelf(serializer)
        suspend fun SequenceScope<DataClassPath<T, *>>.recurse(on: DataClassPath<T, *>) {
            this.yield(on)
            val ser = on.serializer.nullElement() ?: on.serializer
            @Suppress("UNCHECKED_CAST")
            val basis = if(ser.descriptor.isNullable) DataClassPathNotNull(on as DataClassPath<T, Any?>) else on as DataClassPath<T, Any>
            ser.serializableProperties?.forEach {
                if(seen.add(it)) {
                    @Suppress("UNCHECKED_CAST")
                    recurse(DataClassPathAccess(basis, it as SerializableProperty<Any, Any?>))
                }
            }
        }
        for(item in this@walkDataClassPathsLimitedRecursion) { recurse(DataClassPathAccess(s, item)) }
    }
}