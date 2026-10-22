package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.serialization.lens
import com.lightningkite.services.database.optics.notNull
import kotlin.test.Test
import kotlin.test.assertEquals

class LensOverloadTest {
    @Test fun lensPicksOverloadByOpticKind() {
        val model = Signal(LargeTestModel())

        val field: MutableReactiveValue<Int> = model.lens(LargeTestModel.int)
        val composed: MutableReactiveValue<Int> = model.lens(LargeTestModel.embedded.value2)
        val optic: MutableReactiveValue<Int?> = model.lens(LargeTestModel.intNullable.notNull)
        val readOnly: Reactive<Int> = (model as Reactive<LargeTestModel>).lens(LargeTestModel.int)
        val readOnlyValue: ReactiveValue<Int?> = (model as ReactiveValue<LargeTestModel>).lens(LargeTestModel.intNullable.notNull)
        // Claude: the reactive library's lambda lens must still win for lambdas.
        val getter: ReactiveValue<Int> = model.lens { it.int }

        field.value = 5
        assertEquals(5, model.value.int)
        assertEquals(5, getter.value)
        assertEquals(null, optic.value)
        optic.value = null
        assertEquals(null, model.value.intNullable)
    }
}
