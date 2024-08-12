package com.lightningkite.lightningdb

import com.lightningkite.kiteui.reactive.*

fun <O, T> Writable<O>.map(path: DataClassPath<O, T>): Writable<T> {
    return map(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
        set = { o, it -> path.set(o, it) }
    )
}
inline fun <reified O, T> Writable<O>.map(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): Writable<T> {
    val path = makePath(path())
    return map(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
        set = { o, it -> path.set(o, it) }
    )
}