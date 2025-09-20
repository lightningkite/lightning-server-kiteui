package com.lightningkite.kiteui.tests

import com.lightningkite.services.database.ModelPermissions
import com.lightningkite.lightningserver.sessions.Session
import com.lightningkite.services.database.HasId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.uuid.Uuid

class SerializationChecks {
    @Test
    fun test() {
        val data =
            """{"create":{"Never":true},"read":{"subjectId":{"Equal":"550fcf49-520f-4110-962a-bbada46ad1dc"}},"readMask":{"pairs":[{"first":{"Never":true},"second":{"secretHash":{"Assign":""}}}]},"update":{"Never":true},"updateRestrictions":{"fields":[]},"delete":{"Never":true},"maxQueryTimeMs":1000}""".trimIndent()
        val ser = ModelPermissions.serializer(Session.serializer(FakeUser.serializer(), Uuid.serializer()))
        println(Json.decodeFromString(ser, data))
    }
}

@Serializable
data class FakeUser(override val _id: Uuid = Uuid.random()) : HasId<Uuid>