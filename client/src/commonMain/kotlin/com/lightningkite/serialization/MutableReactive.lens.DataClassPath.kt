package com.lightningkite.serialization

import com.lightningkite.lightningdb.path
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens

fun <O, T> MutableReactive<O>.lensPath(path: DataClassPath<O, T>): MutableReactive<T> {
    return lens(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
        modify = { o, it -> path.set(o, it) }
    )
}
inline fun <reified O, T> MutableReactive<O>.lensPath(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): MutableReactive<T> = lensPath(makePath(path()))

fun <O, T> Reactive<O>.lensPath(path: DataClassPath<O, T>): Reactive<T> {
    return lens(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
    )
}
inline fun <reified O, T> Reactive<O>.lensPath(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): Reactive<T> = lensPath(makePath(path()))