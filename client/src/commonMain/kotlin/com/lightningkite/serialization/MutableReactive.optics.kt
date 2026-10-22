@file:OptIn(ExperimentalTypeInference::class)

package com.lightningkite.serialization

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.reactive.lensing.validation.MutableValidatedValue
import com.lightningkite.services.database.optics.Lens
import com.lightningkite.services.database.optics.Optic
import kotlin.experimental.ExperimentalTypeInference
import kotlin.jvm.JvmName

// Claude: every receiver needs both an Optic and a Lens overload. If a receiver only had one, a call like
// Signal.lensPath(lens) would match a more specific receiver on one overload and a more specific parameter
// on another, and neither would win.

private fun <A, B> Optic<A, B>.trySet(source: A, value: B?): A =
    @Suppress("UNCHECKED_CAST")
    if (value != null) set(source, value)
    else if (serializer.descriptor.isNullable) set(source, value as B)
    else source

public fun <O, T> MutableReactive<O>.lensPath(path: Optic<O, T>): MutableReactive<T?> = lens(path::get, path::trySet)
public fun <O, T> MutableReactive<O>.lensPath(path: Lens<O, T>): MutableReactive<T> = lens(path::get, path::set)

@JvmName("lensPathOptic")
public inline fun <reified O, T> MutableReactive<O>.lensPath(makePath: (Lens<O, O>) -> Optic<O, T>): MutableReactive<T?> =
    lensPath(makePath(Lens<O>()))

@OverloadResolutionByLambdaReturnType
public inline fun <reified O, T> MutableReactive<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableReactive<T> =
    lensPath(makePath(Lens<O>()))

public fun <O, T> MutableReactiveValue<O>.lensPath(path: Optic<O, T>): MutableReactiveValue<T?> = lens(path::get, path::trySet)
public fun <O, T> MutableReactiveValue<O>.lensPath(path: Lens<O, T>): MutableReactiveValue<T> = lens(path::get, path::set)

@JvmName("lensPathOptic")
public inline fun <reified O, T> MutableReactiveValue<O>.lensPath(makePath: (Lens<O, O>) -> Optic<O, T>): MutableReactiveValue<T?> =
    lensPath(makePath(Lens<O>()))

@OverloadResolutionByLambdaReturnType
public inline fun <reified O, T> MutableReactiveValue<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableReactiveValue<T> =
    lensPath(makePath(Lens<O>()))

public fun <O, T> MutableValidated<O>.lensPath(path: Optic<O, T>): MutableValidated<T?> = lens(path::get, path::trySet)
public fun <O, T> MutableValidated<O>.lensPath(path: Lens<O, T>): MutableValidated<T> = lens(path::get, path::set)

@JvmName("lensPathOptic")
public inline fun <reified O, T> MutableValidated<O>.lensPath(makePath: (Lens<O, O>) -> Optic<O, T>): MutableValidated<T?> =
    lensPath(makePath(Lens<O>()))

@OverloadResolutionByLambdaReturnType
public inline fun <reified O, T> MutableValidated<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableValidated<T> =
    lensPath(makePath(Lens<O>()))

public fun <O, T> MutableValidatedValue<O>.lensPath(path: Optic<O, T>): MutableValidatedValue<T?> = lens(path::get, path::trySet)
public fun <O, T> MutableValidatedValue<O>.lensPath(path: Lens<O, T>): MutableValidatedValue<T> = lens(path::get, path::set)

@JvmName("lensPathOptic")
public inline fun <reified O, T> MutableValidatedValue<O>.lensPath(makePath: (Lens<O, O>) -> Optic<O, T>): MutableValidatedValue<T?> =
    lensPath(makePath(Lens<O>()))

@OverloadResolutionByLambdaReturnType
public inline fun <reified O, T> MutableValidatedValue<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableValidatedValue<T> =
    lensPath(makePath(Lens<O>()))

public fun <O, T> Reactive<O>.lensPath(path: Optic<O, T>): Reactive<T?> = lens(path::get)
public fun <O, T> Reactive<O>.lensPath(path: Lens<O, T>): Reactive<T> = lens(path::get)

@JvmName("lensPathOptic")
public inline fun <reified O, T> Reactive<O>.lensPath(makePath: (Lens<O, O>) -> Optic<O, T>): Reactive<T?> =
    lensPath(makePath(Lens<O>()))

@OverloadResolutionByLambdaReturnType
public inline fun <reified O, T> Reactive<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): Reactive<T> =
    lensPath(makePath(Lens<O>()))
