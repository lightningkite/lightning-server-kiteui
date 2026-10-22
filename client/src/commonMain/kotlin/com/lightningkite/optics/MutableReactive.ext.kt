package com.lightningkite.optics

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
import kotlin.jvm.JvmName

private fun <A, B> Optic<A, B>.trySet(source: A, value: B?): A =
    @Suppress("UNCHECKED_CAST")
    if (value != null) set(source, value)
    else if (serializer.descriptor.isNullable) set(source, value as B)
    else source

private fun <A, B> Optic<A, B>.getNullable(source: A?): B? = source?.let(::get)
private fun <A, B> Optic<A, B>.setNullable(source: A?, value: B?): A? = source?.let { trySet(it, value) }

public fun <O, T> MutableReactive<O>.lens(optic: Optic<O, T>): MutableReactive<T?> = lens(optic::get, optic::trySet)
@JvmName("lensNullable") public fun <O : Any, T> MutableReactive<O?>.lens(optic: Optic<O, T>): MutableReactive<T?> = lens(optic::getNullable, optic::setNullable)
public fun <O, T> MutableReactive<O>.lens(lens: Lens<O, T>): MutableReactive<T> = lens(lens::get, lens::set)

public fun <O, T> MutableWithReactiveValue<O>.lens(optic: Optic<O, T>): MutableReactive<T?> = lens(optic::get, optic::trySet)
@JvmName("lensNullable") public fun <O : Any, T> MutableWithReactiveValue<O?>.lens(optic: Optic<O, T>): MutableReactive<T?> = lens(optic::getNullable, optic::setNullable)
public fun <O, T> MutableWithReactiveValue<O>.lens(lens: Lens<O, T>): MutableReactive<T> = lens(lens::get, lens::set)

public fun <O, T> MutableReactiveValue<O>.lens(optic: Optic<O, T>): MutableReactiveValue<T?> = lens(optic::get, optic::trySet)
@JvmName("lensNullable") public fun <O : Any, T> MutableReactiveValue<O?>.lens(optic: Optic<O, T>): MutableReactiveValue<T?> = lens(optic::getNullable, optic::setNullable)
public fun <O, T> MutableReactiveValue<O>.lens(lens: Lens<O, T>): MutableReactiveValue<T> = lens(lens::get, lens::set)

public fun <O, T> MutableValidated<O>.lens(optic: Optic<O, T>): MutableValidated<T?> = lens(optic::get, optic::trySet)
@JvmName("lensNullable") public fun <O : Any, T> MutableValidated<O?>.lens(optic: Optic<O, T>): MutableValidated<T?> = lens(optic::getNullable, optic::setNullable)
public fun <O, T> MutableValidated<O>.lens(lens: Lens<O, T>): MutableValidated<T> = lens(lens::get, lens::set)

public fun <O, T> MutableValidatedValue<O>.lens(optic: Optic<O, T>): MutableValidatedValue<T?> = lens(optic::get, optic::trySet)
@JvmName("lensNullable") public fun <O : Any, T> MutableValidatedValue<O?>.lens(optic: Optic<O, T>): MutableValidatedValue<T?> = lens(optic::getNullable, optic::setNullable)
public fun <O, T> MutableValidatedValue<O>.lens(lens: Lens<O, T>): MutableValidatedValue<T> = lens(lens::get, lens::set)

public fun <O, T> Reactive<O>.lens(optic: Optic<O, T>): Reactive<T?> = lens(optic::get)
public fun <O, T> Reactive<O>.lens(lens: Lens<O, T>): Reactive<T> = lens(lens::get)

public fun <O, T> ReactiveValue<O>.lens(optic: Optic<O, T>): ReactiveValue<T?> = lens(optic::get)
public fun <O, T> ReactiveValue<O>.lens(lens: Lens<O, T>): ReactiveValue<T> = lens(lens::get)
