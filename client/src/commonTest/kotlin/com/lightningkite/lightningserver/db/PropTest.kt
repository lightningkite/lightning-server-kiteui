package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.launch
import com.lightningkite.kiteui.reactive.Property
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.reactive.perElement
import com.lightningkite.kiteui.reactive.reactiveScope
import com.lightningkite.lightningdb.map
import kotlin.test.Test
import kotlin.test.assertEquals

class PropTest {
    @Test fun test() {
        val model = Property(LargeTestModel())
        val view = model.map { it.int }
        testContext {
            assertEquals(model.value.int, view.state.get())
            reactiveScope { println(view()) }
            assertEquals(model.value.int, view.state.get())
            launch { view.set(42) }
            assertEquals(model.value.int, view.state.get())
        }
    }
    @Test fun testMulti() {
        val model = Property(LargeTestModel())
        val views = model.map { it.listEmbedded }.perElement()
        testContext {
            launch { views.add(ClassUsedForEmbedding(value2 = 52)) }
            val view = views.state.get().find { it.value.value2 == 52 }!!
            val prop = view.map { it.value2 }
            reactiveScope { println(view()) }
            reactiveScope { println(prop()) }
            launch { prop.set(2) }
        }
    }
}