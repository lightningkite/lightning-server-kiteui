package com.lightningkite.serialization

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.MutableWithReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.reactive.lensing.validation.MutableValidatedValue
import com.lightningkite.services.database.optics.Lens
import com.lightningkite.services.database.optics.Optic

// Claude: every receiver needs both an Optic and a Lens overload. If a receiver only had one, a call like
// Signal.lens(lens) would match a more specific receiver on one overload and a more specific parameter
// on another, and neither would win. For the same reason a type that is both MutableReactive and
// ReactiveValue needs its own pair (MutableWithReactiveValue), or those two pairs tie.

private const val LAMBDA_DEPRECATION =
    "Pass the path directly, like lens(User.address.city). Fields are paths now, and a Lens path keeps the result non-null."

private fun <A, B> Optic<A, B>.trySet(source: A, value: B?): A =
    @Suppress("UNCHECKED_CAST")
    if (value != null) set(source, value)
    else if (serializer.descriptor.isNullable) set(source, value as B)
    else source

public fun <O, T> MutableReactive<O>.lens(optic: Optic<O, T>): MutableReactive<T?> = lens(optic::get, optic::trySet)
public fun <O, T> MutableReactive<O>.lens(path: Lens<O, T>): MutableReactive<T> = lens(path::get, path::set)

public fun <O, T> MutableWithReactiveValue<O>.lens(optic: Optic<O, T>): MutableReactive<T?> = lens(optic::get, optic::trySet)
public fun <O, T> MutableWithReactiveValue<O>.lens(lens: Lens<O, T>): MutableReactive<T> = lens(lens::get, lens::set)

public fun <O, T> MutableReactiveValue<O>.lens(optic: Optic<O, T>): MutableReactiveValue<T?> = lens(optic::get, optic::trySet)
public fun <O, T> MutableReactiveValue<O>.lens(lens: Lens<O, T>): MutableReactiveValue<T> = lens(lens::get, lens::set)

public fun <O, T> MutableValidated<O>.lens(optic: Optic<O, T>): MutableValidated<T?> = lens(optic::get, optic::trySet)
public fun <O, T> MutableValidated<O>.lens(lens: Lens<O, T>): MutableValidated<T> = lens(lens::get, lens::set)

public fun <O, T> MutableValidatedValue<O>.lens(optic: Optic<O, T>): MutableValidatedValue<T?> = lens(optic::get, optic::trySet)
public fun <O, T> MutableValidatedValue<O>.lens(lens: Lens<O, T>): MutableValidatedValue<T> = lens(lens::get, lens::set)

public fun <O, T> Reactive<O>.lens(optic: Optic<O, T>): Reactive<T?> = lens(optic::get)
public fun <O, T> Reactive<O>.lens(lens: Lens<O, T>): Reactive<T> = lens(lens::get)

public fun <O, T> ReactiveValue<O>.lens(optic: Optic<O, T>): ReactiveValue<T?> = lens(optic::get)
public fun <O, T> ReactiveValue<O>.lens(lens: Lens<O, T>): ReactiveValue<T> = lens(lens::get)

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableReactive<O>.lensPath(path: Optic<O, T>): MutableReactive<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableReactive<O>.lensPath(path: Lens<O, T>): MutableReactive<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableReactive<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableReactive<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableReactiveValue<O>.lensPath(path: Optic<O, T>): MutableReactiveValue<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableReactiveValue<O>.lensPath(path: Lens<O, T>): MutableReactiveValue<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableReactiveValue<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableReactiveValue<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableValidated<O>.lensPath(path: Optic<O, T>): MutableValidated<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableValidated<O>.lensPath(path: Lens<O, T>): MutableValidated<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableValidated<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableValidated<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableValidatedValue<O>.lensPath(path: Optic<O, T>): MutableValidatedValue<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> MutableValidatedValue<O>.lensPath(path: Lens<O, T>): MutableValidatedValue<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableValidatedValue<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableValidatedValue<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> Reactive<O>.lensPath(path: Optic<O, T>): Reactive<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.serialization.lens"))
public fun <O, T> Reactive<O>.lensPath(path: Lens<O, T>): Reactive<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> Reactive<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): Reactive<T> =
    lens(makePath(Lens<O>()))
