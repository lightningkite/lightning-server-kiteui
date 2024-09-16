package com.lightningkite.kiteui.forms

import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test

class GroupTest {

    data class TestSquare(
        val label: Char,
        val size: FormSize,
    ) {
        override fun toString(): String {
            return "$label(${size.chunkyScreenHalves.toInt()}x${size.lines.toInt()})"
        }
    }

    class CharGrid(val width: Int, val height: Int) {
        val chars = CharArray(width * height) { ' ' }
        operator fun get(x: Int, y: Int): Char {
            if (x !in 0..<width) return ' '
            if (y !in 0..<height) return ' '
            return chars[y * width + x]
        }

        operator fun set(x: Int, y: Int, char: Char) {
            if (x !in 0..<width) return
            if (y !in 0..<height) return
            chars[y * width + x] = char
        }

        fun string(x: Int, y: Int, string: String) {
            for(i in string.indices)
                set(x + i, y, string[i])
        }

        override fun toString(): String = buildString {
            for (y in 0..<height) {
                append(chars, y * width, width)
                appendLine()
            }
        }
    }

    fun Group<TestSquare>.renderBasic(chars: CharGrid, x: Int, y: Int) {
//        val c = direct?.label
        val c: Char? = null
        val endOffset = -1
        if(direct != null)
            for (xc in (x..x + width.roundToInt() + endOffset))
                for (yc in (y..y + height.roundToInt() + endOffset))
                    chars[xc, yc] = '.'
        for (xc in (x..x + width.roundToInt() + endOffset)) chars[xc, y] = c ?: '-'
        for (xc in (x..x + width.roundToInt() + endOffset)) chars[xc, y + height.roundToInt() + endOffset] = c ?: '-'
        for (yc in (y..y + height.roundToInt() + endOffset)) chars[x, yc] = c ?: '|'
        for (yc in (y..y + height.roundToInt() + endOffset)) chars[x + width.roundToInt() + endOffset, yc] = c ?: '|'
        chars[x, y] = c ?: '+'
        chars[x, y + height.roundToInt() + endOffset] = c ?: '+'
        chars[x + width.roundToInt() + endOffset, y] = c ?: '+'
        chars[x + width.roundToInt() + endOffset, y + height.roundToInt() + endOffset] = c ?: '+'
        direct?.let {
//            chars[x + 1, y + 1] = it.label
            chars.string(x + 1, y + 1, it.toString())
        } ?: run {
            if(vertical)
                chars[x + width.roundToInt() + endOffset, y + height.roundToInt() / 2] = 'V'
            else
                chars[x + width.roundToInt() / 2, y + height.roundToInt() + endOffset] = '>'
        }
        var offset = 0.0
        for (child in children) {
            if (vertical) {
                child.renderBasic(chars, x, y + offset.roundToInt())
                offset += child.height
            } else {
                child.renderBasic(chars, x + offset.roundToInt(), y)
                offset += child.width
            }
        }
//        chars.string(x + 1, y + 1, "(${currentSame.roundToInt()}x${maxPerp.roundToInt()})")
    }

    @Test
    fun testGrid() {
        println(CharGrid(50, 50).apply {
            for (i in 0..<50) this[i, i] = '\\'
        })
    }

    @Test
    fun testGrid2() {
        val base = true
        val group = Group<TestSquare>(base, { size }, null, maxHeight = 10000.0, maxWidth = 80.0).apply {
            children += Group<TestSquare>(!base, { size }, null, maxWidth = 80.0, maxHeight = 20.0).apply {
                children += Group(base, { size }, direct = TestSquare('A', FormSize(10.0, 10.0)))
                children += Group(base, { size }, direct = TestSquare('B', FormSize(10.0, 15.0)))
                children += Group(base, { size }, direct = TestSquare('C', FormSize(10.0, 20.0)))
            }
            children += Group<TestSquare>(!base, { size }, null, maxWidth = 80.0, maxHeight = 20.0).apply {
                children += Group(base, { size }, direct = TestSquare('A', FormSize(10.0, 10.0)))
                children += Group(base, { size }, direct = TestSquare('B', FormSize(10.0, 15.0)))
                children += Group(base, { size }, direct = TestSquare('C', FormSize(10.0, 20.0)))
            }
            children += Group<TestSquare>(!base, { size }, null, maxWidth = 80.0, maxHeight = 20.0).apply {
                children += Group(base, { size }, direct = TestSquare('A', FormSize(10.0, 10.0)))
                children += Group(base, { size }, direct = TestSquare('B', FormSize(10.0, 15.0)))
                children += Group(base, { size }, direct = TestSquare('C', FormSize(10.0, 20.0)))
            }
        }
        println(group)
        if (group.currentSame > 400.0) throw Exception("TOO BIG! ${group.currentSame}")
        val grid = CharGrid(80, group.currentSame.roundToInt() + 1)
        group.renderBasic(grid, 0, 0)
        println(grid)
    }

    @Test
    fun test() {
        val group = Group<TestSquare>(true, { size }, null, maxHeight = 10000.0, maxWidth = 80.0)
        group.populate(listOf(
            TestSquare('A', FormSize(14.0, 16.0)),
            TestSquare('B', FormSize(14.0, 5.0)),
            TestSquare('C', FormSize(4.0, 20.0)),
            TestSquare('D', FormSize(11.0, 11.0)),
        ))
        println(group)
        if (group.currentSame > 400.0) throw Exception("TOO BIG! ${group.currentSame}")
        val grid = CharGrid(90, group.currentSame.roundToInt() + 10)
        group.renderBasic(grid, 0, 0)
        println(grid)
    }

    fun Random.randomElements(count: Int = 26): List<TestSquare> {
        return ('A'..'Z').take(count).map {
            if(Random.nextBoolean()) {
                // thin
                TestSquare(it, FormSize(nextDouble(20.0, 60.0), 5.0))
            } else {
                // tall
                TestSquare(it, FormSize(nextDouble(20.0, 60.0), nextDouble(20.0, 60.0)))
            }
        }
    }

    @Test
    fun random() {
        val group = Group<TestSquare>(true, { size }, null, maxHeight = 10000.0, maxWidth = 80.0)
        group.populate(Random(0x8923441280).randomElements())
        println(group)
        if (group.currentSame > 600.0) throw Exception("TOO BIG! ${group.currentSame}")
        val grid = CharGrid(90, group.currentSame.roundToInt() + 10)
        group.renderBasic(grid, 0, 0)
        println(grid)
    }

    @Test
    fun randomGroups() {
        val random = Random(0x8923441280)
        val group = Group<TestSquare>(true, { size }, null, maxHeight = 10000.0, maxWidth = 80.0)
        group.populate(
            ('A'..'Z').map {
                TestSquare(it, FormSize(random.nextDouble(5.0, 20.0), random.nextDouble(5.0, 20.0)))
            }.chunked(4).map {
                Group.Data.Multiple(it.map { Group.Data.Instance(it) })
            }
        )
        println(group)
        if (group.currentSame > 400.0) throw Exception("TOO BIG! ${group.currentSame}")
        val grid = CharGrid(90, group.currentSame.roundToInt() + 10)
        group.renderBasic(grid, 0, 0)
        println(grid)
    }
}