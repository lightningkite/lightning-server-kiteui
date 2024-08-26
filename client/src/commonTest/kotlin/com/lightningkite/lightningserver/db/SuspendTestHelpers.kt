package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.reactive.CalculationContext
import com.lightningkite.kiteui.reactive.CalculationContextStack
import com.lightningkite.kiteui.suspendCoroutineCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.test.assertEquals


class VirtualDelay<T>(val action: () -> T) {
    val continuations = ArrayList<Continuation<T>>()
    var value: T? = null
    var ready: Boolean = false
    suspend fun await(): T {
        if(ready) return value as T
        return suspendCoroutineCancellable {
            continuations.add(it)
            return@suspendCoroutineCancellable {}
        }
    }
    fun clear() {
        ready = false
    }
    fun go() {
        val value = action()
        this.value = value
        ready = true
        for(continuation in continuations) {
            continuation.resume(value)
        }
        continuations.clear()
    }
}

class VirtualDelayer() {
    val continuations = ArrayList<Continuation<Unit>>()
    suspend fun await(): Unit {
        return suspendCoroutineCancellable {
            continuations.add(it)
            return@suspendCoroutineCancellable {}
        }
    }
    fun go() {
        for(continuation in continuations) {
            continuation.resume(Unit)
        }
        continuations.clear()
    }
}

fun testContext(action: CalculationContext.()->Unit): Job {
    var error: Throwable? = null
    val job = Job()
    var numOutstandingContracts = 0
    with(object: CalculationContext {
        override val coroutineContext: CoroutineContext = job + Dispatchers.Unconfined

        override fun notifyLongComplete(result: Result<Unit>) {
            numOutstandingContracts--
            println("Long load complete")
        }

        override fun notifyStart() {
            numOutstandingContracts++
            println("Long load start")
        }

        override fun notifyComplete(result: Result<Unit>) {
            result.onFailure { t ->
                t.printStackTrace()
                error = t
            }
        }
    }) {
        CalculationContextStack.useIn(this) {
            action()
        }
        job.cancel()
        if(error != null) throw error!!
        assertEquals(0, numOutstandingContracts, "Some work was not completed.")
    }
    return job
}