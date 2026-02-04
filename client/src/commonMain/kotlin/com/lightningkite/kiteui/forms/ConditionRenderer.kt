@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTop
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.centeredVertically
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.ConditionSerializer
import com.lightningkite.services.database.MySealedClassSerializer
import com.lightningkite.services.database.MySealedClassSerializerInterface
import com.lightningkite.services.database.WrappingSerializer
import com.lightningkite.services.database.default
import com.lightningkite.services.database.innerElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.serializer

object ConditionRenderer : Renderer<Condition<Any?>> {
    override val name: String = "Fill In The Blank"
    val matchesSerializer = "com.lightningkite.services.database.Condition"

    override fun priority(context: RenderContext<Condition<Any?>>, module: FormModule): Float {
        // Only match if serializer is MySealedClassSerializerInterface
        return if (context.serializer.descriptor.serialName == matchesSerializer) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Condition<Any?>>, module: FormModule): Double = 20.0

    data class ExtendedSubtypeData(
        val serialName: String,
        val niceName: String,
    )

    val never = ExtendedSubtypeData(
        serialName = Condition.Never.serializer().descriptor.serialName,
        niceName = "nothing",
    )
    val always = ExtendedSubtypeData(
        serialName = Condition.Always.serializer().descriptor.serialName,
        niceName = "everything",
    )
    val and = ExtendedSubtypeData(
        serialName = Condition.And.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "and",
    )
    val or = ExtendedSubtypeData(
        serialName = Condition.Or.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "or",
    )
    val not = ExtendedSubtypeData(
        serialName = Condition.Not.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "not",
    )
    val equal = ExtendedSubtypeData(
        serialName = Condition.Equal.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "is",
    )
    val notEqual = ExtendedSubtypeData(
        serialName = Condition.NotEqual.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "isn't",
    )
    val inside = ExtendedSubtypeData(
        serialName = Condition.Inside.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "is one of",
    )
    val notInside = ExtendedSubtypeData(
        serialName = Condition.NotInside.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "isn't one of",
    )
    val ifNotNull = ExtendedSubtypeData(
        serialName = Condition.IfNotNull.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "is present and",
    )
    val greaterThan = ExtendedSubtypeData(
        serialName = Condition.GreaterThan.serializer(NothingSerializer()).descriptor.serialName,
        niceName = ">",
    )
    val lessThan = ExtendedSubtypeData(
        serialName = Condition.LessThan.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "<",
    )
    val greaterThanOrEqual = ExtendedSubtypeData(
        serialName = Condition.GreaterThanOrEqual.serializer(NothingSerializer()).descriptor.serialName,
        niceName = ">=",
    )
    val lessThanOrEqual = ExtendedSubtypeData(
        serialName = Condition.LessThanOrEqual.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "<=",
    )
    val listAllElements = ExtendedSubtypeData(
        serialName = Condition.ListAllElements.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "entries all",
    )
    val listAnyElements = ExtendedSubtypeData(
        serialName = Condition.ListAnyElements.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "any entry",
    )
    val listSizesEquals = ExtendedSubtypeData(
        serialName = Condition.ListSizesEquals.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "size is",
    )
    val setAllElements = ExtendedSubtypeData(
        serialName = Condition.SetAllElements.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "entries all",
    )
    val setAnyElements = ExtendedSubtypeData(
        serialName = Condition.SetAnyElements.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "any entry",
    )
    val setSizesEquals = ExtendedSubtypeData(
        serialName = Condition.SetSizesEquals.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "size is",
    )
    val exists = ExtendedSubtypeData(
        serialName = Condition.Exists.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "has key",
    )
    val onKey = ExtendedSubtypeData(
        serialName = Condition.OnKey.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "where key",
    )
    val intBitsClear = ExtendedSubtypeData(
        serialName = Condition.IntBitsClear.serializer().descriptor.serialName,
        niceName = "has these bits cleared",
    )
    val intBitsSet = ExtendedSubtypeData(
        serialName = Condition.IntBitsSet.serializer().descriptor.serialName,
        niceName = "has these bits set",
    )
    val intBitsAnyClear = ExtendedSubtypeData(
        serialName = Condition.IntBitsAnyClear.serializer().descriptor.serialName,
        niceName = "has any of these bits cleared",
    )
    val intBitsAnySet = ExtendedSubtypeData(
        serialName = Condition.IntBitsAnySet.serializer().descriptor.serialName,
        niceName = "has any of these bits set",
    )
    val geoDistance = ExtendedSubtypeData(
        serialName = Condition.GeoDistance.serializer().descriptor.serialName,
        niceName = "distance is",
    )
    val stringContains = ExtendedSubtypeData(
        serialName = Condition.StringContains.serializer().descriptor.serialName,
        niceName = "contains",
    )
    val regexMatches = ExtendedSubtypeData(
        serialName = Condition.RegexMatches.serializer().descriptor.serialName,
        niceName = "matches regex",
    )
    val rawStringContains = ExtendedSubtypeData(
        serialName = Condition.RawStringContains.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "contains",
    )
    val fullTextSearch = ExtendedSubtypeData(
        serialName = Condition.FullTextSearch.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "matches search",
    )
    val all = listOf(
        never,
        always,
        and,
        or,
        not,
        equal,
        notEqual,
        inside,
        notInside,
        ifNotNull,
        greaterThan,
        lessThan,
        greaterThanOrEqual,
        lessThanOrEqual,
        listAllElements,
        listAnyElements,
        listSizesEquals,
        setAllElements,
        setAnyElements,
        setSizesEquals,
        exists,
        onKey,
        intBitsClear,
        intBitsSet,
        intBitsAnyClear,
        intBitsAnySet,
        geoDistance,
        stringContains,
        regexMatches,
        rawStringContains,
        fullTextSearch,
    ).associateBy { it.serialName }

    val MySealedClassSerializer.Option<Condition<Any?>, *>.extended
        get() = all[serializer.descriptor.serialName]

    @Suppress("UNCHECKED_CAST")
    override fun form(
        context: RenderContext<Condition<Any?>>,
        value: MutableReactive<Condition<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit {
        val options = (context.serializer as ConditionSerializer<Any?>).options

        return {
            val type = value.lens(
                get = { options.find { o -> o.isInstance(it) } ?: options.first() },
                set = { it.serializer.default() }
            )
            row {
                centered.sizeConstraints(width = options.maxOf { (it.extended?.niceName ?: it.serializer.displayName).length.times(0.65).rem }).card.select {
                    bind(type, Constant(options)) { it.extended?.niceName ?: it.serializer.displayName }
                }
                expanding.frame {
                    reactive {
                        val selectedType = type()
                        clearChildren()
                        val subSerializer = selectedType.serializer as KSerializer<Any>
                        val subContext =
                            RenderContext(subSerializer as KSerializer<Condition<Any?>>, context.fieldAnnotations)
                        val subValue = value.lens(
                            get = { if (selectedType.isInstance(it)) it else subSerializer.default() },
                            set = { it }
                        )
                        module.form(subContext, subValue)(centeredVertically)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(
        context: RenderContext<Condition<Any?>>,
        value: Reactive<Condition<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit {
        val options = (context.serializer as ConditionSerializer<Any?>).options

        return {
            val type = value.lens(
                get = { options.find { o -> o.isInstance(it) } ?: options.first() },
            )
            row {
                gap = 0.25.rem
                centered.text {
                    ::content { type().extended?.niceName ?: type().serializer.displayName }
                }
                expanding.frame {
                    reactive {
                        val selectedType = type()
                        clearChildren()
                        val subSerializer = selectedType.serializer as KSerializer<Any>
                        val subContext =
                            RenderContext(subSerializer as KSerializer<Condition<Any?>>, context.fieldAnnotations)
                        val subValue = value.lens(
                            get = { if (selectedType.isInstance(it)) it else subSerializer.default() },
                        )
                        module.view(subContext, subValue)(centeredVertically)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(
        context: RenderContext<Condition<Any?>>,
        value: Reactive<Condition<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit {
        val serializer = context.serializer as MySealedClassSerializerInterface<Any>

        return {
            text {
                ::content { value().toString() }
            }
        }
    }

    override fun labeledForm(
        context: RenderContext<Condition<Any?>>,
        value: MutableReactive<Condition<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        fieldWithoutBorder(label, description) { form(context, value, module)() }
    }

    override fun labeledView(
        context: RenderContext<Condition<Any?>>,
        value: Reactive<Condition<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        fieldWithoutBorder(label, description) { view(context, value, module)() }
    }
}

fun FormModule.registerCondition() {
    register(Selector(type = "com.lightningkite.services.database.Condition"), ConditionRenderer)
}
