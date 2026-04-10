@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.CaselessStringSerializer
import com.lightningkite.TrimmedCaselessStringSerializer
import com.lightningkite.TrimmedStringSerializer
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Renderer for SortPart<T> types used in sorting configuration.
 *
 * Displays a dropdown of all sortable fields from the inner type,
 * with options for ascending/descending and case sensitivity for strings.
 *
 * by Claude
 */
object SortPathRenderer : Renderer<Any> {
    override val name: String = "Sort Part"

    private val stringTypes = setOf(
        Char.serializer().descriptor.serialName,
        String.serializer().descriptor.serialName,
        CaselessStringSerializer.descriptor.serialName,
        TrimmedStringSerializer.descriptor.serialName,
        TrimmedCaselessStringSerializer.descriptor.serialName,
    )

    private val comparableTypes = setOf(
        Boolean.serializer().descriptor.serialName,
        Byte.serializer().descriptor.serialName,
        Short.serializer().descriptor.serialName,
        Int.serializer().descriptor.serialName,
        Long.serializer().descriptor.serialName,
        UByte.serializer().descriptor.serialName,
        UShort.serializer().descriptor.serialName,
        UInt.serializer().descriptor.serialName,
        ULong.serializer().descriptor.serialName,
        Uuid.serializer().descriptor.serialName,
        Instant.serializer().descriptor.serialName,
        LocalDate.serializer().descriptor.serialName,
        LocalDateTime.serializer().descriptor.serialName,
        LocalTime.serializer().descriptor.serialName,
        Duration.serializer().descriptor.serialName,
        DurationMsSerializer.descriptor.serialName,
    ) + stringTypes

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        // Only match SortPartSerializer
        return if (context.serializer is SortPartSerializer<*>) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double = 20.0

    /**
     * Helper class to compute sortable options from a SortPartSerializer.
     */
    private class SortOptions(module: FormModule, serializer: SortPartSerializer<Any?>) {
        val options = ArrayList<SortPart<Any?>>()

        init {
            fun traverse(serializer: KSerializer<Any?>, base: DataClassPath<Any?, Any?>) {
                serializer.serializableProperties?.forEach { prop ->
                    val ser = prop.serializer.let {
                        @Suppress("UNCHECKED_CAST")
                        if (it is ContextualSerializer<*>) module.serializersModule.getContextual(it as ContextualSerializer<Any>)
                        else it
                    }
                    if (ser.descriptor.serialName in comparableTypes) {
                        val access = DataClassPathAccess(base, prop)
                        if (ser.descriptor.serialName !in stringTypes) {
                            options += listOf(
                                SortPart(access, ascending = true, ignoreCase = false),
                                SortPart(access, ascending = false, ignoreCase = false),
                            )
                        } else {
                            options += listOf(
                                SortPart(access, ascending = true, ignoreCase = false),
                                SortPart(access, ascending = false, ignoreCase = false),
                                SortPart(access, ascending = true, ignoreCase = true),
                                SortPart(access, ascending = false, ignoreCase = true),
                            )
                        }
                    }
                }
            }
            traverse(serializer.inner, DataClassPathSelf(serializer.inner))
            options.sortByDescending {
                it.field.properties.lastOrNull()?.indexed ?: false
            }
        }

        fun toString(it: SortPart<Any?>): String {
            val path = it.field.properties.joinToString(" / ") { it.displayName }
            return if (it.field.serializerAny.descriptor.serialName in stringTypes) {
                when {
                    it.ignoreCase && it.ascending -> "$path A-Z"
                    it.ignoreCase -> "$path Z-A"
                    it.ascending -> "$path A-z (case sensitive)"
                    else -> "$path z-A (case sensitive)"
                }
            } else {
                if (it.ascending) "$path Low - High"
                else "$path High - Low"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val serializer = context.serializer as SortPartSerializer<Any?>
        val sortOptions = SortOptions(module, serializer)
        val typedValue = value as MutableReactive<SortPart<Any?>>

        return {
            fieldTheme.select {
                bind(typedValue, Constant(sortOptions.options), sortOptions::toString)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val serializer = context.serializer as SortPartSerializer<Any?>
        val sortOptions = SortOptions(module, serializer)
        val typedValue = value as Reactive<SortPart<Any?>>

        return {
            text { ::content { sortOptions.toString(typedValue()) } }
        }
    }

    override fun cellForm(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit =
        form(context, value, module)

    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit =
        view(context, value, module)
}

/**
 * Register the sort path renderer with the module.
 *
 * by Claude
 */
fun FormModule.registerSortPath() {
    register(Selector(type = "com.lightningkite.services.database.SortPart"), SortPathRenderer)
}
