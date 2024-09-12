package com.lightningkite.lightningserver.db

import com.lightningkite.UUID
import com.lightningkite.kiteui.forms.get
import com.lightningkite.kiteui.forms.set
import com.lightningkite.lightningdb.Query
import com.lightningkite.serialization.default
import com.lightningkite.uuid
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.time.Duration

class HelpersKtTest {
    @Serializable
    data class TestModel(
        val _id: UUID = uuid(),
        val x: Int = 0,
        val y: String,
        val z: Duration?,
        val uhoh: UUID,
        val nah: Instant
    )

    @Test
    fun testDefaults() {
        println(TestModel.serializer().default())
    }

    @Test fun testAnonGet() {
        val q = Query<LargeTestModel>()
        kotlin.test.assertEquals(q.limit, serializer<Query<LargeTestModel>>().get(q, 3, Int.serializer()))
    }

    @Test fun testAnonSet() {
        val q = Query<LargeTestModel>()
        kotlin.test.assertEquals(q.copy(limit = 20), serializer<Query<LargeTestModel>>().set(q, 3, Int.serializer(), 20))
    }
}