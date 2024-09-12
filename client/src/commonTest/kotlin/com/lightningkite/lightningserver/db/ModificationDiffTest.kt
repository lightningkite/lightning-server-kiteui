package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.*
import com.lightningkite.serialization.*
import com.lightningkite.now
import com.lightningkite.prepareModelsShared
import com.lightningkite.uuid
import kotlin.test.Test
import kotlin.test.assertEquals

class ModificationDiffTest {
    init  {
        prepareModelsShared()
        prepareModelsClientTest()
    }
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
                    uuidNullable = uuid(),
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
            modification { it.boolean assign true },
            modification { it.byte assign 1 },
            modification { it.short assign 1 },
            modification { it.int assign 1 },
            modification { it.int coerceAtMost -1 },
            modification { it.int coerceAtLeast 1 },
            modification { it.long assign 1 },
            modification { it.float assign 1f },
            modification { it.double assign 1.0 },
            modification { it.char assign 'A' },
            modification { it.string assign "A" },
            modification { it.uuid assign uuid() },
            modification { it.instant assign now() },
            modification { it.list assign listOf(1, 2, 3) },
            modification { it.listEmbedded assign listOf(ClassUsedForEmbedding("test", 42)) },
            modification { it.listEmbedded += listOf(ClassUsedForEmbedding("test", 42)) },
            modification { it.listEmbedded.removeAll { it.value2 lt 2 } },
            modification { it.set assign setOf(1, 2, 3) },
            modification { it.setEmbedded assign setOf(ClassUsedForEmbedding("test", 42)) },
            modification { it.setEmbedded += setOf(ClassUsedForEmbedding("test", 42)) },
            modification { it.setEmbedded.removeAll { it.value2 lt 2 } },
            modification { it.map assign mapOf("a" to 1) },
            modification { it.embedded assign ClassUsedForEmbedding("test", 42) },
            modification { it.booleanNullable assign true },
            modification { it.byteNullable assign 1 },
            modification { it.shortNullable assign 1 },
            modification { it.intNullable assign 1 },
            modification { it.intNullable.notNull coerceAtMost -1 },
            modification { it.intNullable.notNull coerceAtLeast 1 },
            modification { it.longNullable assign 1 },
            modification { it.floatNullable assign 1f },
            modification { it.doubleNullable assign 1.0 },
            modification { it.charNullable assign 'A' },
            modification { it.stringNullable assign "A" },
            modification { it.uuidNullable assign uuid() },
            modification { it.instantNullable assign now() },
            modification { it.listNullable assign listOf(1, 2, 3) },
            modification { it.mapNullable assign mapOf("a" to 1) },
            modification { it.embeddedNullable assign ClassUsedForEmbedding("test", 42) },
            modification { it.embeddedNullable.notNull.value2 assign 42 },
            modification { it.embeddedNullable.notNull.value2 coerceAtLeast 42 },
        )
        modification.forEach { check(it) }
    }
}