package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.reactive.lensing.lensByElementAssumingSetNeverManipulates
import com.lightningkite.optics.lens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PropTest {
    @Test fun test() {
        val model = Signal(LargeTestModel())
        val view = model.lens(LargeTestModel.int)
        runTest2 {
            assertEquals(model.value.int, view.state.getOrNull())
            reactive { println(view()) }
            assertEquals(model.value.int, view.state.getOrNull())
            launch { view.set(42) }
            assertEquals(model.value.int, view.state.getOrNull())
        }
    }
    @Test fun testMulti() {
        val model = Signal(LargeTestModel())
        val views = model.lens(LargeTestModel.listEmbedded).lensByElementAssumingSetNeverManipulates()
        runTest2 {
            launch { model.modify { it.copy(listEmbedded = it.listEmbedded.plus(ClassUsedForEmbedding(value2 = 52))) } }
            delay(1.seconds)
            val view = views.state.getOrNull()!!.find { it.value.value2 == 52 }!!
            val prop = view.lens(ClassUsedForEmbedding.value2)
            reactive { println(view()) }
            reactive { println(prop()) }
            launch { prop.set(2) }
        }
    }
}
