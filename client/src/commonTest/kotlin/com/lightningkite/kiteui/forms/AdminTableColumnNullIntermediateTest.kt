package com.lightningkite.kiteui.forms

import com.lightningkite.services.data.AdminTableColumns
import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.database.HasId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
@GenerateDataClassPaths
data class VoidAction(
    val at: Instant,
    val by: String = "",
)

@Serializable
@GenerateDataClassPaths
@AdminTableColumns(["amount", "voided.at"])
data class VoidablePayment(
    override val _id: Uuid = Uuid.random(),
    val amount: Int = 0,
    val voided: VoidAction? = null,
) : HasId<Uuid>

/**
 * The shape that broke the admin tables: a column path whose intermediate is nullable and whose
 * leaf is not. `voided.at` reads as null on every un-voided row, so the column has to be rendered
 * by a renderer that takes a null - the leaf's own `Instant` serializer picks [InstantRenderer],
 * which calls `Instant.toLocalDateTime` on the null and takes the whole table body down with it.
 */
class AdminTableColumnNullIntermediateTest {
    private val module = FormModule().apply { defaults() }
    private val column = VoidablePayment.serializer().defaultColumns().first { it.toString().endsWith("at") }

    @Test
    fun adminTableColumnsResolvesTheNestedPath() {
        assertEquals(
            listOf("amount", "voided?.at"),
            VoidablePayment.serializer().defaultColumns().map { it.toString() }
        )
    }

    @Test
    fun nullIntermediateMakesTheColumnReadNull() {
        assertNull(column.getAny(VoidablePayment(amount = 100, voided = null)))
    }

    @Test
    fun theColumnIsRenderedByANullTolerantRenderer() {
        assertEquals<Any?>(NullableInstantRenderer, ColumnInfo(column, module).renderer(module))
    }

    /**
     * Guards the actual fix: picking the renderer off the leaf property's own serializer - what
     * [ColumnInfo] did before [readSerializer] existed - lands on the renderer that cannot take a
     * null. If this ever stops being true the test above stops proving anything.
     */
    @Test
    fun theLeafSerializerAloneWouldPickTheCrashingRenderer() {
        assertEquals<Any?>(InstantRenderer, module.select(RenderContext(column.serializerAny)))
    }
}
