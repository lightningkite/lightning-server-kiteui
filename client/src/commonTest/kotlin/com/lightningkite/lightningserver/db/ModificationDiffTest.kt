package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.modification
import kotlin.uuid.Uuid
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.lt
import com.lightningkite.services.database.optics.notNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock.System.now

class ModificationDiffTest {
    @Test
    fun testNullToNot() {
        val old = LargeTestModel(embeddedNullable = null)
        val new = old.copy(embeddedNullable = ClassUsedForEmbedding())
        println(modification(old, new))
    }

    @Test
    fun testNotToNull() {
        val old = LargeTestModel(embeddedNullable = ClassUsedForEmbedding())
        val new = old.copy(embeddedNullable = null)
        println(modification(old, new))
    }

    @Test
    fun testNotToNot() {
        val old = LargeTestModel(embeddedNullable = ClassUsedForEmbedding())
        val new = old.copy(embeddedNullable = old.embeddedNullable?.copy(value2 = 42))
        println(modification(old, new))
    }

    @Test
    fun cycling() {
        fun check(mod: Modification<LargeTestModel>) {
            println("Checking $mod")
            run {
                val old = LargeTestModel()
                val new = mod.invoke(old)
                val altNew = modification(old, new)?.invoke(old) ?: old
                assertEquals(new, altNew)
            }
            run {
                val old = LargeTestModel(
                    booleanNullable = false,
                    byteNullable = 0,
                    shortNullable = 0,
                    intNullable = 0,
                    longNullable = 0,
                    floatNullable = 1f,
                    doubleNullable = 1.0,
                    charNullable = 'A',
                    stringNullable = "A",
                    UuidNullable = Uuid.random(),
                    instantNullable = now(),
                    listNullable = listOf(),
                    mapNullable = mapOf(),
                    embeddedNullable = ClassUsedForEmbedding(),
                )
                val new = mod.invoke(old)
                val altNew = modification(old, new)?.invoke(old) ?: old
                assertEquals(new, altNew)
            }
        }

        val modification: List<Modification<LargeTestModel>> = listOf(
            Modification { it.boolean assign true },
            Modification { it.byte assign 1 },
            Modification { it.short assign 1 },
            Modification { it.int assign 1 },
            Modification { it.int coerceAtMost -1 },
            Modification { it.int coerceAtLeast 1 },
            Modification { it.long assign 1 },
            Modification { it.float assign 1f },
            Modification { it.double assign 1.0 },
            Modification { it.char assign 'A' },
            Modification { it.string assign "A" },
            Modification { it.uuid assign Uuid.random() },
            Modification { it.instant assign now() },
            Modification { it.list assign listOf(1, 2, 3) },
            Modification { it.listEmbedded assign listOf(ClassUsedForEmbedding("test", 42)) },
            Modification { it.listEmbedded += listOf(ClassUsedForEmbedding("test", 42)) },
            Modification { it.listEmbedded.removeAll { it.value2 lt 2 } },
            Modification { it.set assign setOf(1, 2, 3) },
            Modification { it.setEmbedded assign setOf(ClassUsedForEmbedding("test", 42)) },
            Modification { it.setEmbedded += setOf(ClassUsedForEmbedding("test", 42)) },
            Modification { it.setEmbedded.removeAll { it.value2 lt 2 } },
            Modification { it.map assign mapOf("a" to 1) },
            Modification { it.embedded assign ClassUsedForEmbedding("test", 42) },
            Modification { it.booleanNullable assign true },
            Modification { it.byteNullable assign 1 },
            Modification { it.shortNullable assign 1 },
            Modification { it.intNullable assign 1 },
            Modification { it.intNullable.notNull coerceAtMost -1 },
            Modification { it.intNullable.notNull coerceAtLeast 1 },
            Modification { it.longNullable assign 1 },
            Modification { it.floatNullable assign 1f },
            Modification { it.doubleNullable assign 1.0 },
            Modification { it.charNullable assign 'A' },
            Modification { it.stringNullable assign "A" },
            Modification { it.UuidNullable assign Uuid.random() },
            Modification { it.instantNullable assign now() },
            Modification { it.listNullable assign listOf(1, 2, 3) },
            Modification { it.mapNullable assign mapOf("a" to 1) },
            Modification { it.embeddedNullable assign ClassUsedForEmbedding("test", 42) },
            Modification { it.embeddedNullable.notNull.value2 assign 42 },
            Modification { it.embeddedNullable.notNull.value2 coerceAtLeast 42 },
        )
        modification.forEach { check(it) }
    }
}