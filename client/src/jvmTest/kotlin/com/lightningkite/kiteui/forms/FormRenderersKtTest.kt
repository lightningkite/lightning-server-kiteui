package com.lightningkite.kiteui.forms

import com.lightningkite.UUID
import com.lightningkite.uuid
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class FormRenderersKtTest {
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
}