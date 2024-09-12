package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.SortPart
import com.lightningkite.serialization.DataClassPath
import com.lightningkite.serialization.DataClassPathNotNull
import com.lightningkite.serialization.notNull

fun <T> List<SortPart<T>>.after(after: T): Condition<T> {
    return Condition.Or<T>((1..this.size).map { count ->
        Condition.And(this.take(count).mapIndexed { index, it ->
            val isLast = index == count - 1
            val f = it.field
            if(f is DataClassPathNotNull<*, *>) {
                f as DataClassPathNotNull<T, Comparable<Comparable<*>>>
                val v = f.get(after)
                f.wraps.mapCondition(
                    if (v == null) {
                        if (it.ascending) {
                            if (isLast) Condition.NotEqual(null)
                            else Condition.Always
                        } else {
                            if (isLast) Condition.Never
                            else Condition.Equal(null)
                        }
                    } else {
                        if (it.ascending) {
                            if (isLast) Condition.IfNotNull(Condition.GreaterThan(v))
                            else Condition.IfNotNull(Condition.GreaterThanOrEqual(v))
                        } else {
                            if (isLast) Condition.Or(listOf(Condition.IfNotNull(Condition.LessThan(v)), Condition.Equal(null)))
                            else Condition.Or(listOf(Condition.IfNotNull(Condition.LessThanOrEqual(v)), Condition.Equal(null)))
                        }
                    }
                )
            } else {
                f as DataClassPath<T, Comparable<Comparable<*>>>
                val v = f.get(after)
                if (v == null) throw Error("Value to compare against is null; this should not be possible")
                f.mapCondition(
                    if (it.ascending) {
                        if (isLast) Condition.GreaterThan(v)
                        else Condition.GreaterThanOrEqual(v)
                    } else {
                        if (isLast) Condition.LessThan(v)
                        else Condition.LessThanOrEqual(v)
                    }
                )
            }
        })
    })
}