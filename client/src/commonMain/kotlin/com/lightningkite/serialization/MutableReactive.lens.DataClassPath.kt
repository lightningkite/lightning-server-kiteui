package com.lightningkite.serialization

import com.lightningkite.lightningdb.path
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.reactive.lensing.validation.MutableValidatedValue

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

fun <O, T> MutableReactiveValue<O>.lensPath(path: DataClassPath<O, T>): MutableReactiveValue<T> {
    return lens(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
        modify = { o, it -> path.set(o, it) }
    )
}
inline fun <reified O, T> MutableReactiveValue<O>.lensPath(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): MutableReactiveValue<T> = lensPath(makePath(path()))

fun <O, T> MutableValidated<O>.lensPath(path: DataClassPath<O, T>): MutableValidated<T> {
    return lens(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
        modify = { o, it -> path.set(o, it) }
    )
}
inline fun <reified O, T> MutableValidated<O>.lensPath(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): MutableValidated<T> = lensPath(makePath(path()))

fun <O, T> MutableValidatedValue<O>.lensPath(path: DataClassPath<O, T>): MutableValidatedValue<T> {
    return lens(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
        modify = { o, it -> path.set(o, it) }
    )
}
inline fun <reified O, T> MutableValidatedValue<O>.lensPath(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): MutableValidatedValue<T> = lensPath(makePath(path()))

fun <O, T> Reactive<O>.lensPath(path: DataClassPath<O, T>): Reactive<T> {
    return lens(
        get = {
            @Suppress("UNCHECKED_CAST")
            path.get(it) as T
        },
    )
}
inline fun <reified O, T> Reactive<O>.lensPath(makePath: (DataClassPath<O, O>) -> DataClassPath<O, T>): Reactive<T> = lensPath(makePath(path()))
