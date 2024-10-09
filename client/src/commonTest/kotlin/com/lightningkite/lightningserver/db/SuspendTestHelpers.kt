package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.suspendCoroutineCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.test.assertEquals
import kotlin.test.fail


class VirtualDelay<T>(val action: () -> T) {
    val continuations = ArrayList<Continuation<T>>()
    var value: T? = null
    var ready: Boolean = false
    suspend fun await(): T {
        if (ready) return value as T
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
        for (continuation in continuations) {
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
        for (continuation in continuations) {
            continuation.resume(Unit)
        }
        continuations.clear()
    }
}

class TestContext : CoroutineScope {
    var error: Throwable? = null
    val job = Job()
    var loadCount = 0
    fun expectException(): Throwable {
        val e = error ?: fail("Expected exception but there was none")
        error = null
        return e
    }

    val incompleteKeys = HashSet<Any>()
    override val coroutineContext: CoroutineContext = job + Dispatchers.Unconfined + object : StatusListener {
        override fun loading(readable: Readable<*>) {
            var loading = false
            var excEnder: (() -> Unit)? = null
            readable.addAndRunListener {
                val s = readable.state
                println("${readable} reports ${s}")
                if (loading != !s.ready) {
                    if (s.ready) {
                        loadCount--
                    } else {
                        loadCount++
                    }
                    loading = !s.ready
                }
                excEnder?.invoke()
                s.exception?.let { t ->
                    t.printStackTrace()
                    error = t
                }
            }.let { onRemove(it) }
        }
    }
}

fun testContext(action: TestContext.() -> Unit) {
    with(TestContext()) {
        CoroutineScopeStack.useIn(this) {
            action()
        }
        job.cancel()
        if (error != null) throw Exception("Unexpected error", error!!)
        assertEquals(0, loadCount, "Some work was not completed: ${incompleteKeys}")
    }
}