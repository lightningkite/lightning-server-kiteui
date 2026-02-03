package com.lightningkite.kiteui.forms

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for serialization utilities in default.kt
 */
class DefaultKtTest {

    // ============================================
    // Test data classes and enums
    // ============================================

    @Serializable
    enum class TestEnum { A, B, C }

    @Serializable
    data class SimpleData(val name: String, val count: Int)

    @Serializable
    data class NestedData(val outer: String, val inner: SimpleData)

    // ============================================
    // enumValues() Tests
    // ============================================

    @Test
    fun enumValues_extractsAllEnumValues() {
        val values = TestEnum.serializer().enumValues()
        assertEquals(3, values.size)
        assertEquals(TestEnum.A, values[0])
        assertEquals(TestEnum.B, values[1])
        assertEquals(TestEnum.C, values[2])
    }

    @Test
    fun enumValues_preservesOrder() {
        val values = TestEnum.serializer().enumValues()
        assertEquals(listOf(TestEnum.A, TestEnum.B, TestEnum.C), values)
    }

    // ============================================
    // get() Tests
    // ============================================

    @Test
    fun get_retrievesFieldByIndex() {
        val data = SimpleData("test", 42)
        val serializer = SimpleData.serializer()

        // Get name (index 0)
        val name = serializer.get(data, 0, String.serializer())
        assertEquals("test", name)

        // Get count (index 1)
        val count = serializer.get(data, 1, Int.serializer())
        assertEquals(42, count)
    }

    @Test
    fun get_worksWithNestedData() {
        val inner = SimpleData("inner", 10)
        val data = NestedData("outer", inner)
        val serializer = NestedData.serializer()

        // Get outer string (index 0)
        val outer = serializer.get(data, 0, String.serializer())
        assertEquals("outer", outer)

        // Get inner data (index 1)
        val innerResult = serializer.get(data, 1, SimpleData.serializer())
        assertEquals(inner, innerResult)
    }

    // ============================================
    // set() Tests
    // ============================================

    @Test
    fun set_modifiesFieldByIndex() {
        val data = SimpleData("original", 100)
        val serializer = SimpleData.serializer()

        // Set name (index 0)
        val modifiedName = serializer.set(data, 0, String.serializer(), "modified")
        assertEquals("modified", modifiedName.name)
        assertEquals(100, modifiedName.count) // Unchanged

        // Set count (index 1)
        val modifiedCount = serializer.set(data, 1, Int.serializer(), 999)
        assertEquals("original", modifiedCount.name) // Unchanged
        assertEquals(999, modifiedCount.count)
    }

    @Test
    fun set_preservesOtherFields() {
        val inner = SimpleData("inner", 10)
        val data = NestedData("outer", inner)
        val serializer = NestedData.serializer()

        // Modify outer, inner should be preserved
        val modified = serializer.set(data, 0, String.serializer(), "newOuter")
        assertEquals("newOuter", modified.outer)
        assertEquals(inner, modified.inner) // Unchanged
    }

    @Test
    fun set_worksWithNestedDataReplacement() {
        val inner = SimpleData("inner", 10)
        val data = NestedData("outer", inner)
        val serializer = NestedData.serializer()

        val newInner = SimpleData("newInner", 99)
        val modified = serializer.set(data, 1, SimpleData.serializer(), newInner)
        assertEquals("outer", modified.outer) // Unchanged
        assertEquals(newInner, modified.inner)
    }

    // ============================================
    // serializationCast() Tests
    // ============================================

    @Test
    fun serializationCast_convertsCompatibleTypes() {
        val source = SimpleData("test", 42)
        val sourceSerializer = SimpleData.serializer()
        val targetSerializer = SimpleData.serializer()

        val result = sourceSerializer.serializationCast(source, targetSerializer)
        assertEquals(source, result)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun get_set_roundTrip() {
        val original = SimpleData("roundtrip", 123)
        val serializer = SimpleData.serializer()

        // Get value
        val name = serializer.get(original, 0, String.serializer())

        // Set same value back
        val result = serializer.set(original, 0, String.serializer(), name)

        assertEquals(original, result)
    }

    @Test
    fun enumValues_emptyEnumHandling() {
        // This test documents behavior - an enum with no values would have empty list
        // Note: Can't actually test with 0-value enum as Kotlin requires at least one
        val values = TestEnum.serializer().enumValues()
        assertTrue(values.isNotEmpty())
    }
}
