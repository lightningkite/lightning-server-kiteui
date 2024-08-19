package com.lightningkite.lightningdb

import com.lightningkite.kiteui.reactive.*

fun <O, T> Writable<O>.lensPath(path: DataClassPath<O, T>): Writable<T> {
    return lens(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
        modify = { o, it -> path.set(o, it) }
    )
}
inline fun <reified O, T> Writable<O>.lensPath(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): Writable<T> = lensPath(makePath(path()))

@Deprecated("Use lensPath")
fun <O, T> Writable<O>.map(path: DataClassPath<O, T>): Writable<T> = lensPath(path)
@Deprecated("Use lensPath")
inline fun <reified O, T> Writable<O>.map(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): Writable<T> = lensPath(makePath(path()))