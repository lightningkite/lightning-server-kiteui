package com.lightningkite.kiteui.forms

import com.lightningkite.CaselessStringSerializer
import com.lightningkite.TrimmedCaselessStringSerializer
import com.lightningkite.TrimmedStringSerializer
import com.lightningkite.kiteui.reactive.Constant
import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.lightningdb.SortPart
import com.lightningkite.lightningdb.SortPartSerializer
import com.lightningkite.serialization.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

object SortPathRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Sort Part"
    override val type: String = "com.lightningkite.lightningdb.SortPart"

    val stringTypes = setOf(
        Char.serializer().descriptor.serialName,
        String.serializer().descriptor.serialName,
        CaselessStringSerializer.descriptor.serialName,
        TrimmedStringSerializer.descriptor.serialName,
        TrimmedCaselessStringSerializer.descriptor.serialName,
    )
    val comparableTypes = setOf(
        Boolean.serializer().descriptor.serialName,
        Byte.serializer().descriptor.serialName,
        Short.serializer().descriptor.serialName,
        Int.serializer().descriptor.serialName,
        Long.serializer().descriptor.serialName,
        UByte.serializer().descriptor.serialName,
        UShort.serializer().descriptor.serialName,
        UInt.serializer().descriptor.serialName,
        ULong.serializer().descriptor.serialName,
        UUIDSerializer.descriptor.serialName,
        InstantIso8601Serializer.descriptor.serialName,
        LocalDateIso8601Serializer.descriptor.serialName,
        LocalDateTimeIso8601Serializer.descriptor.serialName,
        LocalTimeIso8601Serializer.descriptor.serialName,
        DurationSerializer.descriptor.serialName,
        DurationMsSerializer.descriptor.serialName,
    ) + stringTypes

    class TypeInfo<T>(val module: FormModule, val serializer: SortPartSerializer<Any?>) {
        val options = ArrayList<SortPart<Any?>>()

        init {
            fun traverse(serializer: KSerializer<Any?>, base: DataClassPath<Any?, Any?>) {
                serializer.serializableProperties?.forEach {
                    val ser = it.serializer.let {
                        if (it is ContextualSerializer<*>) module.module.getContextual(it)
                        else it
                    }
                    if (ser.descriptor.serialName in comparableTypes) {
                        val access = DataClassPathAccess(base, it)
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
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val serializer = selector.serializer as SortPartSerializer<Any?>
        val info = TypeInfo<SortPart<Any?>>(module, serializer)
        return FormRenderer<SortPart<Any?>>(module, this, selector as FormSelector<SortPart<Any?>>) { field, writable ->
            fieldTheme - select {
                bind(writable, Constant(info.options), info::toString)
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val serializer = selector.serializer as SortPartSerializer<Any?>
        val info = TypeInfo<SortPart<Any?>>(module, serializer)
        return ViewRenderer<SortPart<Any?>>(module, this, selector as FormSelector<SortPart<Any?>>) { field, readable ->
            text { ::content { info.toString(readable()) } }
        } as ViewRenderer<T>
    }
}