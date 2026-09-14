package com.lightningkite.lightningserver.admin

import com.lightningkite.services.database.HasId
import kotlin.test.*

/**
 * Test suite for readability.kt
 *
 * Tests the type aliases UnknownModel and UnknownId which provide type erasure
 * for working with models of unknown types in the admin panel.
 *
 * These type aliases are used with unsafe casts (as?) to enable runtime type erasure
 * when the admin panel needs to work with collections of unknown model types.
 */
class ReadabilityTest {

    /**
     * Sample model with String ID for testing type compatibility
     */
    data class TestModelString(
        override val _id: String = "test-id",
        val name: String = "Test"
    ) : HasId<String>

    /**
     * Sample model with Int ID for testing type compatibility
     */
    data class TestModelInt(
        override val _id: Int = 123,
        val value: Int = 456
    ) : HasId<Int>

    /**
     * Tests that models with String IDs can be unsafely cast to UnknownModel
     * This is the actual usage pattern in the admin panel for type erasure
     */
    @Test
    fun testStringIdModelUnsafeCast() {
        val stringModel: HasId<String> = TestModelString(_id = "abc-123", name = "Test String")

        // Unsafe cast as used in CollectionAdminScreen.kt line 73-74
        @Suppress("UNCHECKED_CAST")
        val unknownModel = stringModel as? UnknownModel

        assertNotNull(unknownModel)
        assertEquals<Any?>("abc-123", unknownModel._id)
    }

    /**
     * Tests that models with Int IDs can be unsafely cast to UnknownModel
     * This verifies numeric ID types work with the type erasure pattern
     */
    @Test
    fun testIntIdModelUnsafeCast() {
        val intModel: HasId<Int> = TestModelInt(_id = 999, value = 777)

        // Unsafe cast pattern
        @Suppress("UNCHECKED_CAST")
        val unknownModel = intModel as? UnknownModel

        assertNotNull(unknownModel)
        assertEquals<Any?>(999, unknownModel._id)
    }

    /**
     * Tests that the UnknownModel type alias can represent any HasId implementation
     * This is the core purpose of the type alias - to allow generic handling
     */
    @Test
    fun testUnknownModelTypeErasure() {
        // Create instances of different model types
        val stringModel: HasId<String> = TestModelString(_id = "str-id")
        val intModel: HasId<Int> = TestModelInt(_id = 42)

        // Both can be cast to UnknownModel
        @Suppress("UNCHECKED_CAST")
        val unknown1 = stringModel as? UnknownModel
        @Suppress("UNCHECKED_CAST")
        val unknown2 = intModel as? UnknownModel

        assertNotNull(unknown1)
        assertNotNull(unknown2)

        // Both are still HasId instances
        assertTrue(unknown1 is HasId<*>)
        assertTrue(unknown2 is HasId<*>)
    }

    /**
     * Tests that ID values are accessible after type erasure
     * This is critical for ModelCache operations
     */
    @Test
    fun testIdAccessAfterErasure() {
        val model = TestModelString(_id = "test-123", name = "Test")

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertNotNull(unknown._id)
        assertEquals<Any?>("test-123", unknown._id)
    }

    /**
     * Tests that different ID types can coexist in the same type-erased context
     * This simulates how the admin panel handles multiple collections
     */
    @Test
    fun testMixedIdTypesInCollection() {
        val models: List<HasId<*>> = listOf(
            TestModelString(_id = "string-1"),
            TestModelInt(_id = 100),
            TestModelString(_id = "string-2"),
            TestModelInt(_id = 200)
        )

        // Each can be cast to UnknownModel
        val unknownModels = models.mapNotNull { model ->
            @Suppress("UNCHECKED_CAST")
            model as? UnknownModel
        }

        assertEquals(4, unknownModels.size)
        assertEquals<Any?>("string-1", unknownModels[0]._id)
        assertEquals<Any?>(100, unknownModels[1]._id)
        assertEquals<Any?>("string-2", unknownModels[2]._id)
        assertEquals<Any?>(200, unknownModels[3]._id)
    }

    /**
     * Tests that UnknownModel preserves the underlying model's properties
     * This ensures type erasure doesn't lose data and allows downcasting
     */
    @Test
    fun testDowncastingAfterErasure() {
        val original = TestModelString(_id = "id-123", name = "Original Name")

        @Suppress("UNCHECKED_CAST")
        val unknown = original as? UnknownModel

        assertNotNull(unknown)

        // Should be able to cast back to original type
        val recovered = unknown as? TestModelString

        assertNotNull(recovered)
        assertEquals(original._id, recovered._id)
        assertEquals(original.name, recovered.name)
    }

    /**
     * Tests that UnknownModel works with nullable properties in the underlying model
     */
    @Test
    fun testUnknownModelWithNullableProperties() {
        data class NullableModel(
            override val _id: String,
            val nullableField: String?
        ) : HasId<String>

        val model: HasId<String> = NullableModel(_id = "null-test", nullableField = null)

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertEquals<Any?>("null-test", unknown._id)

        // Cast back to verify nullable field preserved
        val recovered = unknown as? NullableModel
        assertNotNull(recovered)
        assertNull(recovered.nullableField)
    }

    /**
     * Tests edge case with empty string ID
     */
    @Test
    fun testEmptyStringId() {
        val model: HasId<String> = TestModelString(_id = "", name = "Empty ID")

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertEquals<Any?>("", unknown._id)
    }

    /**
     * Tests edge case with zero integer ID
     */
    @Test
    fun testZeroIntegerId() {
        val model: HasId<Int> = TestModelInt(_id = 0, value = 100)

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertEquals<Any?>(0, unknown._id)
    }

    /**
     * Tests that negative integer IDs work correctly
     */
    @Test
    fun testNegativeIntegerId() {
        val model: HasId<Int> = TestModelInt(_id = -1, value = 200)

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertEquals<Any?>(-1, unknown._id)
    }

    /**
     * Tests very large integer IDs
     */
    @Test
    fun testLargeIntegerId() {
        val model: HasId<Int> = TestModelInt(_id = Int.MAX_VALUE, value = 0)

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertEquals<Any?>(Int.MAX_VALUE, unknown._id)
    }

    /**
     * Tests that Unicode characters work in string IDs
     */
    @Test
    fun testUnicodeStringId() {
        val model: HasId<String> = TestModelString(_id = "测试-🔥-id", name = "Unicode")

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertEquals<Any?>("测试-🔥-id", unknown._id)
    }

    /**
     * Tests that safe cast returns null for non-HasId types
     * This verifies the safe cast behavior
     */
    @Test
    fun testSafeCastFailure() {
        val notAModel = "just a string"

        @Suppress("UNCHECKED_CAST")
        val unknown = notAModel as? UnknownModel

        // Should be null since it's not a HasId
        assertNull(unknown)
    }

    /**
     * Tests that models with complex nested ID types work
     * Some models might use Uuid or other Comparable types
     */
    @Test
    fun testComplexIdTypes() {
        // Simulate a UUID-like comparable type
        data class UuidLike(val value: String) : Comparable<UuidLike> {
            override fun compareTo(other: UuidLike): Int = value.compareTo(other.value)
        }

        data class UuidModel(
            override val _id: UuidLike,
            val data: String
        ) : HasId<UuidLike>

        val model: HasId<UuidLike> = UuidModel(_id = UuidLike("uuid-123"), data = "test")

        @Suppress("UNCHECKED_CAST")
        val unknown = model as? UnknownModel

        assertNotNull(unknown)
        assertTrue(unknown._id is UuidLike)
        assertEquals(UuidLike("uuid-123"), unknown._id as UuidLike)
    }
}
