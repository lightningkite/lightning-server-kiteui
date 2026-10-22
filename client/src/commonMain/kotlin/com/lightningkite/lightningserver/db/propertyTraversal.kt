package com.lightningkite.lightningserver.admin

import com.lightningkite.services.database.optics.*
import com.lightningkite.services.database.*
import kotlinx.serialization.KSerializer

public fun <T> Array<Field<T, *>>.walkDataClassPathsLimitedRecursion(serializer: KSerializer<T>): Sequence<Optic<T, *>> {
    return sequence<Optic<T, *>> {
        val seen = HashSet<Field<*, *>>()
        val s = IdentityLens(serializer)
        suspend fun SequenceScope<Optic<T, *>>.recurse(on: Optic<T, *>) {
            this.yield(on)
            val ser = on.serializer.nullElement() ?: on.serializer
            @Suppress("UNCHECKED_CAST")
            val basis = if(ser.descriptor.isNullable) NotNullOptic(on as Optic<T, Any?>) else on as Optic<T, Any>
            ser.fields?.forEach {
                if(seen.add(it)) {
                    @Suppress("UNCHECKED_CAST")
                    recurse(basis + (it as Field<Any, Any?>))
                }
            }
        }
        for(item in this@walkDataClassPathsLimitedRecursion) { recurse(s + item) }
    }
}
