package com.lightningkite.lightningserver.admin

import com.lightningkite.lightningserver.typed.LightningServerKSchema
import com.lightningkite.lightningserver.typed.LightningServerKSchemaEndpoint
import com.lightningkite.lightningserver.typed.LightningServerKSchemaInterface
import com.lightningkite.services.database.VirtualTypeReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemActionsTest {
    private fun type(name: String, vararg args: VirtualTypeReference, nullable: Boolean = false) =
        VirtualTypeReference(name, args.toList(), nullable)

    private val uuid = type("kotlin.uuid.Uuid")
    private val string = type("kotlin.String")
    private val unit = type("kotlin.Unit")

    private fun model(path: String, typeName: String, id: VirtualTypeReference) = LightningServerKSchemaInterface(
        matches = type("com.lightningkite.lightningserver.typed.ClientModelRestEndpoints", type(typeName), id),
        path = path,
    )

    private fun endpoint(
        path: String,
        summary: String,
        routes: Map<String, VirtualTypeReference> = mapOf(),
        input: VirtualTypeReference = unit,
        method: String = "POST",
        belongsTo: VirtualTypeReference? = null,
    ) = LightningServerKSchemaEndpoint(
        description = "",
        summary = summary,
        method = method,
        path = path,
        routes = routes,
        input = input,
        output = unit,
        belongsToInterface = belongsTo,
    )

    private fun detect(vararg endpoints: LightningServerKSchemaEndpoint) = LightningServerKSchema(
        baseUrl = "http://localhost:8080",
        baseWsUrl = "ws://localhost:8080",
        structures = mapOf(),
        enums = mapOf(),
        aliases = mapOf(),
        endpoints = endpoints.toList(),
        interfaces = listOf(
            model("/race/racer", "demo.Racer", uuid),
            model("/race/club", "demo.Club", uuid),
            model("/fcmTokens", "demo.FcmToken", string),
            model("/auth/session/sessions", "demo.Session", uuid),
        ),
    ).detectItemActions().mapValues { (_, actions) -> actions.map { it.endpoint.summary } }

    @Test
    fun routeUnderTheCollectionPath() {
        val found = detect(endpoint("/fcmTokens/{id}/clear", "Clear Token", routes = mapOf("id" to string)))
        assertEquals(listOf("Clear Token"), found["/fcmTokens"])
    }

    @Test
    fun routeNamedAfterTheModel() {
        val found = detect(endpoint("/auth/session/{sessionId}/terminate", "Terminate Session", routes = mapOf("sessionId" to uuid)))
        assertEquals(listOf("Terminate Session"), found["/auth/session/sessions"])
    }

    @Test
    fun idAsBodyNamedInSummary() {
        val found = detect(endpoint("/race/scratch", "Scratch Racer", input = type("kotlin.uuid.Uuid", nullable = true)))
        assertEquals(listOf("Scratch Racer"), found["/race/racer"])
        assertNull(found["/race/club"])
    }

    @Test
    fun sharedIdTypeAloneIsNotEnough() {
        // Takes a Uuid, but nothing says whose.
        assertEquals(mapOf(), detect(endpoint("/race/reset/{id}", "Reset", routes = mapOf("id" to uuid))))
    }

    @Test
    fun ambiguousMatchesAreLeftOut() {
        assertEquals(mapOf(), detect(endpoint("/race/merge", "Merge Racer Into Club", input = uuid)))
    }

    @Test
    fun genericBodyTypeNeedsEvidenceToo() {
        // A String body that isn't about FcmTokens at all.
        assertEquals(mapOf(), detect(endpoint("/auth/session/token/simple", "Get Token Simple", input = string)))
    }

    @Test
    fun standardRestOperationsAreNotActions() {
        val rest = type("com.lightningkite.lightningserver.typed.ClientModelRestEndpoints", type("demo.Racer"), uuid)
        assertEquals(mapOf(), detect(endpoint("/race/racer/{id}", "Detail", routes = mapOf("id" to uuid), method = "GET", belongsTo = rest)))
    }

    @Test
    fun extraInputIsCollected() {
        val schema = LightningServerKSchema(
            baseUrl = "", baseWsUrl = "", structures = mapOf(), enums = mapOf(), aliases = mapOf(),
            endpoints = listOf(endpoint("/race/racer/{id}/advance", "Advance", routes = mapOf("id" to uuid), input = type("demo.AdvanceRequest"))),
            interfaces = listOf(model("/race/racer", "demo.Racer", uuid)),
        )
        val action = schema.detectItemActions().getValue("/race/racer").single()
        assertEquals("id", action.idRoute)
        assertEquals(true, action.hasInput)
    }
}
