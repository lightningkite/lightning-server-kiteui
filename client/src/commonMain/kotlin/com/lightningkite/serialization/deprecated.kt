package com.lightningkite.serialization

import com.lightningkite.optics.lens
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
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

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableReactive<O>.lensPath(path: Optic<O, T>): MutableReactive<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableReactive<O>.lensPath(path: Lens<O, T>): MutableReactive<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableReactive<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableReactive<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableReactiveValue<O>.lensPath(path: Optic<O, T>): MutableReactiveValue<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableReactiveValue<O>.lensPath(path: Lens<O, T>): MutableReactiveValue<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableReactiveValue<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableReactiveValue<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableValidated<O>.lensPath(path: Optic<O, T>): MutableValidated<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableValidated<O>.lensPath(path: Lens<O, T>): MutableValidated<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableValidated<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableValidated<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableValidatedValue<O>.lensPath(path: Optic<O, T>): MutableValidatedValue<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> MutableValidatedValue<O>.lensPath(path: Lens<O, T>): MutableValidatedValue<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> MutableValidatedValue<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): MutableValidatedValue<T> =
    lens(makePath(Lens<O>()))

@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> Reactive<O>.lensPath(path: Optic<O, T>): Reactive<T?> = lens(path)
@Deprecated("Renamed to lens", ReplaceWith("lens(path)", "com.lightningkite.optics.lens"))
public fun <O, T> Reactive<O>.lensPath(path: Lens<O, T>): Reactive<T> = lens(path)
@Deprecated(LAMBDA_DEPRECATION)
public inline fun <reified O, T> Reactive<O>.lensPath(makePath: (Lens<O, O>) -> Lens<O, T>): Reactive<T> =
    lens(makePath(Lens<O>()))
