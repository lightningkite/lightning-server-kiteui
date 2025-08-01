package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.reactive.lensing.lensByElementAssumingSetNeverManipulates
import com.lightningkite.serialization.lensPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PropTest {
    @Test fun test() {
        val model = Signal(LargeTestModel())
        val view = model.lensPath { it.int }
        runTest2 {
            assertEquals(model.value.int, view.state.get())
            reactiveScope { println(view()) }
            assertEquals(model.value.int, view.state.get())
            launch { view.set(42) }
            assertEquals(model.value.int, view.state.get())
        }
    }
    @Test fun testMulti() {
        val model = Signal(LargeTestModel())
        val views = model.lensPath { it.listEmbedded }.lensByElementAssumingSetNeverManipulates()
        runTest2 {
            launch { model.modify { it.copy(listEmbedded = it.listEmbedded.plus(ClassUsedForEmbedding(value2 = 52))) } }
            delay(1.seconds)
            val view = views.state.get().find { it.value.value2 == 52 }!!
            val prop = view.lensPath { it.value2 }
            reactiveScope { println(view()) }
            reactiveScope { println(prop()) }
            launch { prop.set(2) }
        }
    }
}