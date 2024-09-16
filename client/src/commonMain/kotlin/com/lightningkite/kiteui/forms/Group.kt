package com.lightningkite.kiteui.forms

import kotlin.jvm.JvmName
import kotlin.math.roundToInt


class Group<T>(
    val vertical: Boolean,
    val size: T.() -> FormSize,
    val direct: T? = null,
    val maxWidth: Double = direct?.size()?.approximateWidth ?: 100000.0,
    val maxHeight: Double = direct?.size()?.approximateHeight ?: 100000.0,
) {
    var locked: Boolean = direct != null

    val maxPerp = if (vertical) maxWidth else maxHeight
    val maxSame = if (vertical) maxHeight else maxWidth

    val currentSame: Double
        get() = (direct?.size()?.let { if (vertical) it.approximateHeight else it.approximateWidth } ?: 0.0) +
                children.sumOf { if (vertical) it.usedHeight else it.usedWidth }
    val currentPerp: Double
        get() = maxOf(direct?.size()?.let { if (vertical) it.approximateWidth else it.approximateHeight } ?: 0.0,
            children.maxOfOrNull { if (vertical) it.usedWidth else it.usedHeight } ?: 0.0)

    val width get() = if (vertical) currentPerp else currentSame
    val height get() = if (vertical) currentSame else currentPerp

    val usedWidth get() = if (vertical) maxWidth else width
    val usedHeight get() = if (!vertical) maxHeight else height

    val children = ArrayList<Group<T>>()
    val T.same get() = if (vertical) size().approximateHeight else size().approximateWidth
    val T.perp get() = if (!vertical) size().approximateHeight else size().approximateWidth

    val Group<T>.perp get() = if (this@Group.vertical) usedWidth else usedHeight
    val Group<T>.same get() = if (this@Group.vertical) usedHeight else usedWidth

    sealed interface Data<T> {
        data class Instance<T>(val value: T) : Data<T>
        data class Multiple<T>(val data: List<Data<T>>) : Data<T>
    }

    fun Data<T>.options() = when (this) {
        is Data.Instance -> listOf(Group(direct = value, vertical = true, size = size))
        is Data.Multiple -> listOf(
            Group(vertical = true, size = size, maxWidth = maxWidth, maxHeight = maxHeight).apply { populate(data) },
            Group(vertical = false, size = size, maxWidth = maxWidth, maxHeight = maxHeight).apply { populate(data) },
        )
    }

    @JvmName("populateDirect")
    fun populate(data: Collection<T>): Unit = populate(data.map { Data.Instance(it) })
    fun populate(data: Collection<Data<T>>): Unit {
        val s = data.mapTo(HashSet()) { it to it.options() }
        while (take(s)) {
        }
        locked = true
        if (s.isNotEmpty())
            println("WARNING: Could not fit ${s.size} items: ${s.joinToString { it.second.map { "${it.width.roundToInt()}x${it.height.roundToInt()}" }.toString() }}")
    }

    fun take(data: MutableSet<Pair<Data<T>, List<Group<T>>>>, depthCap: Int = 5, depth: Int = 0): Boolean {
        if (locked) return false
        val remainingSame = maxSame - currentSame
        fun findOption() = data.asSequence()
            .flatMap { it.second.map { o -> o to it } }
            .filter { (it, _) -> it.perp <= maxPerp && it.same <= remainingSame }
            .let {
                if ((depth) % 2 == 0)
                    it.maxByOrNull { it.first.same }
                else
                    it.minByOrNull { it.first.same }
            }
        if (depth == depthCap) {
            val option = findOption() ?: return false
            data -= option.second
            children += option.first
            return true
        } else {
            if(depth == 0)
            for (child in children) {
                if (child.take(data, depthCap, depth + 1)) return true
            }
            findOption()?.let { option ->
                data -= option.second
                children += if (vertical) {
                    Group(
                        vertical = false,
                        maxWidth = maxWidth,
                        maxHeight = option.first.height * 1.20,
//                maxSame = maxPerp,
//                maxPerp = option.first.same * 1.10,
                        size = size,
                    )
                } else {
                    Group(
                        vertical = true,
                        maxWidth = option.first.width * 1.20,
                        maxHeight = maxHeight,
                        size = size,
                    )
                }.apply {
                    children += option.first
                }
                return true
            }
            if(depth != 0)
            for (child in children) {
                if (child.take(data, depthCap, depth + 1)) return true
            }
            return false
        }
    }

    override fun toString(): String {
//        if (children.size == 1) return children[0].toString()
        return (direct?.toString()
            ?: children.toString()).plus("${if (vertical) "v" else "h"} ${usedWidth.roundToInt()}x${usedHeight.roundToInt()} ${width.roundToInt()}x${height.roundToInt()} ${maxWidth.roundToInt()}x${maxHeight.roundToInt()}")
    }
}