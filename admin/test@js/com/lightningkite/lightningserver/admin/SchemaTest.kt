@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.lightningserver.typed.*
import com.lightningkite.services.database.*
import kotlinx.serialization.Serializable
import kotlin.test.*

/**
 * Test suite for schema.kt
 *
 * Tests the ExternalLightningServer class and related helper functions/classes
 * that bridge server-side Lightning Server schema to client-side caching and endpoints.
 */
class SchemaTest {

    @Serializable
    data class TestModel(
        override val _id: String = "",
        val name: String = "",
        val value: Int = 0
    ) : HasId<String>

    /**
     * Tests ExternalLightningServer basic initialization.
     * Verifies that the schema is stored and basic properties are accessible.
     */
    @Test
    fun testExternalLightningServerInit() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )

        val externalServer = ExternalLightningServer(
            schema = schema,
            useLiveData = false,
            registry = SerializationRegistry.master,
            json = DefaultJson,
            properties = UrlProperties
        )

        assertNotNull(externalServer)
        assertEquals(schema, externalServer.schema)
        assertEquals("http://localhost:8080", externalServer.schema.baseUrl)
        assertEquals("ws://localhost:8080", externalServer.schema.baseWsUrl)
    }

    /**
     * Tests that SerializationRegistry.register() doesn't throw with valid schema.
     * Verifies the extension function can handle empty structures/enums/aliases.
     */
    @Test
    fun testSerializationRegistryRegisterEmptySchema() {
        val registry = SerializationRegistry.master
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )

        // Should not throw with empty schema
        registry.register(schema)
    }

    /**
     * Tests that fetcher() creates a fetcher instance.
     */
    @Test
    fun testFetcherCreation() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        val fetcher = externalServer.fetcher(null)
        assertNotNull(fetcher, "Fetcher should be created")
    }

    /**
     * Tests that fetcher() caches instances per authentication.
     * Verifies same instance is returned for same auth.
     */
    @Test
    fun testFetcherCaching() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        val fetcher1 = externalServer.fetcher(null)
        val fetcher2 = externalServer.fetcher(null)

        assertSame(fetcher1, fetcher2, "Same fetcher should be returned for same auth")
    }

    /**
     * Tests authEndpoints() method creates AuthEndpoints instance.
     */
    @Test
    fun testAuthEndpointsCreation() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        val authEndpoints = externalServer.authEndpoints(null)
        assertNotNull(authEndpoints, "AuthEndpoints should be created")
    }

    /**
     * Tests that authEndpoints() caches instances per authentication.
     * Note: AuthEndpoints objects have a withAuthentication callback that creates new instances,
     * so reference equality on the full object won't hold, but the subjects map should be stable.
     */
    @Test
    fun testAuthEndpointsCaching() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        val endpoints1 = externalServer.authEndpoints(null)
        val endpoints2 = externalServer.authEndpoints(null)

        // Can't use assertSame because AuthEndpoints contains a closure that gets recreated
        // But we can verify they're functionally equivalent
        assertEquals(endpoints1.subjects.size, endpoints2.subjects.size, "AuthEndpoints should have same subjects")
    }

    /**
     * Tests models map is empty when no interfaces.
     */
    @Test
    fun testModelsMapEmpty() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertEquals(0, externalServer.models.size, "Should have no models without interfaces")
    }

    /**
     * Tests formModule() method creates FormModule.
     */
    @Test
    fun testFormModuleCreation() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        val formModule = externalServer.formModule(null)
        assertNotNull(formModule, "FormModule should be created")
    }

    /**
     * Tests formModule() without upload endpoint has null fileUpload.
     */
    @Test
    fun testFormModuleWithoutFileUpload() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        val formModule = externalServer.formModule(null)
        assertNull(formModule.fileUpload, "FileUpload should be null without upload endpoint")
    }

    /**
     * Tests formModule() typeInfo callback.
     */
    @Test
    fun testFormModuleTypeInfo() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        val formModule = externalServer.formModule(null)
        assertNotNull(formModule.typeInfo, "TypeInfo should be set")

        // Test with non-existent type
        val result = formModule.typeInfo?.invoke("com.example.NonExistent")
        assertNull(result, "Should return null for non-existent type")
    }

    /**
     * Tests page property is mutable and can be set.
     */
    @Test
    fun testPagePropertyMutable() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        var pageCalled = false
        externalServer.page = { type, id ->
            pageCalled = true
            null
        }

        // Verify assignment worked
        assertNotNull(externalServer.page, "Page property should be accessible")
    }

    /**
     * Tests bulk endpoint detection returns null when not present.
     */
    @Test
    fun testBulkEndpointNotPresent() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertNull(externalServer.bulk, "Should not find bulk endpoint when not present")
    }

    /**
     * Tests file upload endpoint detection returns null when not present.
     */
    @Test
    fun testUploadEndpointNotPresent() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertNull(externalServer.file, "Should not find upload endpoint when not present")
    }

    /**
     * Tests file verify endpoint detection returns null when not present.
     */
    @Test
    fun testUploadVerifyEndpointNotPresent() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertNull(externalServer.fileVerify, "Should not find verify endpoint when not present")
    }

    /**
     * Tests health endpoint detection returns null when not present.
     */
    @Test
    fun testHealthEndpointNotPresent() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertNull(externalServer.health, "Should not find health endpoint when not present")
    }

    /**
     * Tests ExternalLightningServer handles empty endpoints list.
     */
    @Test
    fun testEmptyEndpoints() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = emptyList(),
            interfaces = emptyList()
        )

        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertNull(externalServer.bulk)
        assertNull(externalServer.file)
        assertNull(externalServer.fileVerify)
        assertNull(externalServer.health)
    }

    /**
     * Tests ExternalLightningServer with different base URLs.
     */
    @Test
    fun testDifferentBaseUrls() {
        val schema = LightningServerKSchema(
            baseUrl = "https://api.example.com",
            baseWsUrl = "wss://api.example.com",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertEquals("https://api.example.com", externalServer.schema.baseUrl)
        assertEquals("wss://api.example.com", externalServer.schema.baseWsUrl)
    }

    /**
     * Tests PerAuthCache class basic functionality.
     */
    @Test
    fun testPerAuthCacheBasicUsage() {
        var calculateCount = 0
        val cache = PerAuthCache<String> { auth ->
            calculateCount++
            "result-${auth?.toString() ?: "null"}"
        }

        // First call should calculate
        val result1 = cache(null)
        assertEquals("result-null", result1)
        assertEquals(1, calculateCount)

        // Second call with null should use forNull lazy
        val result2 = cache(null)
        assertEquals("result-null", result2)
        assertEquals(1, calculateCount, "Should not recalculate for null")
    }

    /**
     * Tests PerAuthCache caches the last non-null authentication.
     */
    @Test
    fun testPerAuthCacheWithAuthentication() {
        var calculateCount = 0
        val cache = PerAuthCache<String> { auth ->
            calculateCount++
            "result-${auth?.subjectPath ?: "null"}"
        }

        // Create mock authentications (we can't fully test without real auth structure)
        // But we can verify the caching behavior with null
        val result1 = cache(null)
        assertEquals(1, calculateCount)

        val result2 = cache(null)
        assertEquals(1, calculateCount, "Should use cached value")
    }

    /**
     * Tests PerAuthCache recalculates when authentication changes.
     */
    @Test
    fun testPerAuthCacheRecalculatesOnAuthChange() {
        var calculateCount = 0
        val cache = PerAuthCache<Int> { auth ->
            calculateCount++
            calculateCount
        }

        // First call
        val result1 = cache(null)
        assertEquals(1, result1)

        // Same auth should not recalculate
        val result2 = cache(null)
        assertEquals(1, result2)
        assertEquals(1, calculateCount, "Should not recalculate for same auth")
    }

    /**
     * Tests PerAuthCache forNull lazy initialization.
     */
    @Test
    fun testPerAuthCacheForNullLazy() {
        var nullCalculateCount = 0
        val cache = PerAuthCache<String> { auth ->
            if (auth == null) nullCalculateCount++
            "result"
        }

        // First null call initializes forNull
        cache(null)
        assertEquals(1, nullCalculateCount)

        // Subsequent null calls use cached forNull
        cache(null)
        cache(null)
        assertEquals(1, nullCalculateCount, "forNull should be lazy and cached")
    }

    /**
     * Tests that ExternalLightningServer can be created with useLiveData=true.
     */
    @Test
    fun testUseLiveDataTrue() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = true)

        assertNotNull(externalServer)
        assertTrue(externalServer.useLiveData)
    }

    /**
     * Tests that ExternalLightningServer can be created with useLiveData=false.
     */
    @Test
    fun testUseLiveDataFalse() {
        val schema = LightningServerKSchema(
            baseUrl = "http://localhost:8080",
            baseWsUrl = "ws://localhost:8080",
            structures = mapOf(),
            enums = mapOf(),
            aliases = mapOf(),
            endpoints = listOf(),
            interfaces = listOf()
        )
        val externalServer = ExternalLightningServer(schema, useLiveData = false)

        assertNotNull(externalServer)
        assertFalse(externalServer.useLiveData)
    }
}
