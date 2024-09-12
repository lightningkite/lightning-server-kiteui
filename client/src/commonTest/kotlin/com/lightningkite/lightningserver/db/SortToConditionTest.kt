package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.comparator
import com.lightningkite.lightningdb.sort
import com.lightningkite.serialization.notNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class SortToConditionTest {
    @Test
    fun test() {
        val data = (1..100).map {
            LargeTestModel(
                int = Random.nextInt(),
                long = Random.nextLong(),
                float = Random.nextFloat(),
                double = Random.nextDouble(),
                intNullable = Random.nextInt().takeIf { Random.nextBoolean() },
                longNullable = Random.nextLong().takeIf { Random.nextBoolean() },
                floatNullable = Random.nextFloat().takeIf { Random.nextBoolean() },
                doubleNullable = Random.nextDouble().takeIf { Random.nextBoolean() },
            )
        }
        val sorts = listOf(
            sort<LargeTestModel> {
                it.int.ascending()
                it._id.ascending()
            },
            sort<LargeTestModel> {
                it.intNullable.notNull.ascending()
                it._id.ascending()
            },
            sort<LargeTestModel> {
                it.intNullable.notNull.descending()
                it._id.ascending()
            },
        )
        for (sort in sorts) {
            val sorted = data.sortedWith(sort.comparator!!)
            (1..sorted.lastIndex).forEach { index ->
                val firstHalf = sorted.take(index)
                val lastElement = firstHalf.last()
                val condition = sort.after(lastElement)
                val secondHalf = sorted.drop(index)
                val afterLast = sorted.filter { condition(it) }
                fun toString(model: LargeTestModel) = sort.joinToString("/") { it.field.getAny(model).toString() }
                assertEquals(secondHalf.joinToString("\n", transform = ::toString), afterLast.joinToString("\n", transform = ::toString), message = "Failed equality on ${sort} index $index.\n${toString(lastElement)}\n${condition}\nFull List: ${sorted.joinToString(transform = ::toString)}")
//                println("Success on ${sort} index $index.\n${toString(lastElement)}\n${condition}\nFull List: ${sorted.joinToString(transform = ::toString)}")
            }
        }
    }
}