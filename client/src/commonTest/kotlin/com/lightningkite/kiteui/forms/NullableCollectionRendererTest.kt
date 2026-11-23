package com.lightningkite.kiteui.forms

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class NullableCollectionRendererTest {
    private val module = FormModule().apply { defaults() }

    @Test
    fun nullableMapIsRenderedByNullableRenderer() {
        val context = RenderContext(MapSerializer(String.serializer(), String.serializer()).nullable)
        assertEquals<Any?>(NullableRenderer, module.select(context))
    }

    @Test
    fun nullableListIsRenderedByNullableRenderer() {
        val context = RenderContext(ListSerializer(String.serializer()).nullable)
        assertEquals<Any?>(NullableRenderer, module.select(context))
    }

    @Test
    fun nullableSetIsRenderedByNullableRenderer() {
        val context = RenderContext(SetSerializer(String.serializer()).nullable)
        assertEquals<Any?>(NullableRenderer, module.select(context))
    }
}
