package com.lightningkite.kiteui.forms

import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.database.DataClassPathSerializer
import com.lightningkite.services.database.HasId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
@GenerateDataClassPaths
data class NullablePathAction(
    val at: Instant,
    val reason: String = "",
)

@Serializable
@GenerateDataClassPaths
data class NullablePathModel(
    override val _id: Uuid = Uuid.random(),
    val voided: NullablePathAction? = null,
    val createdAt: Instant,
) : HasId<Uuid>

/**
 * `voided.at` reads as null whenever `voided` is null, even though `at` itself is not nullable.
 * The column has to be rendered by a renderer that accepts null, or it crashes on every row that
 * has not been voided.
 */
class ColumnInfoNullablePathTest {
    private val module = FormModule().apply { defaults() }
    private val paths = DataClassPathSerializer(NullablePathModel.serializer())

    @Test
    fun pathThroughNullableIsNullable() {
        assertTrue(paths.fromString("voided.at").readSerializer.descriptor.isNullable)
    }

    @Test
    fun directPathKeepsItsOwnNullability() {
        assertTrue(!paths.fromString("createdAt").readSerializer.descriptor.isNullable)
        assertTrue(paths.fromString("voided").readSerializer.descriptor.isNullable)
    }

    @Test
    fun columnThroughNullableUsesNullTolerantRenderer() {
        val column = ColumnInfo(paths.fromString("voided.at"), module)
        assertEquals<Any?>(NullableInstantRenderer, column.renderer(module))
    }

    @Test
    fun directColumnStillUsesPlainRenderer() {
        val column = ColumnInfo(paths.fromString("createdAt"), module)
        assertEquals<Any?>(InstantRenderer, column.renderer(module))
    }
}
