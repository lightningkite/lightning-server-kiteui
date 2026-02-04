// by Claude
@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.forms.ConditionRenderer.extended
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.centeredVertically
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.ConditionSerializer
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.ModificationSerializer
import com.lightningkite.services.database.MySealedClassSerializer
import com.lightningkite.services.database.MySealedClassSerializerInterface
import com.lightningkite.services.database.default
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.NothingSerializer

object ModificationRenderer : Renderer<Modification<Any?>> {
    override val name: String = "Fill In The Blank"
    val matchesSerializer = "com.lightningkite.services.database.Modification"

    override fun priority(context: RenderContext<Modification<Any?>>, module: FormModule): Float {
        return if (context.serializer.descriptor.serialName == matchesSerializer) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Modification<Any?>>, module: FormModule): Double = 20.0

    data class ExtendedSubtypeData(
        val serialName: String,
        val niceName: String,
    )

    val nothing = ExtendedSubtypeData(
        serialName = Modification.Nothing.serializer().descriptor.serialName,
        niceName = "no change",
    )
    val chain = ExtendedSubtypeData(
        serialName = Modification.Chain.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "multiple",
    )
    val ifNotNull = ExtendedSubtypeData(
        serialName = Modification.IfNotNull.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "if present",
    )
    val assign = ExtendedSubtypeData(
        serialName = Modification.Assign.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "set to",
    )
    val coerceAtMost = ExtendedSubtypeData(
        serialName = Modification.CoerceAtMost.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "cap at",
    )
    val coerceAtLeast = ExtendedSubtypeData(
        serialName = Modification.CoerceAtLeast.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "floor at",
    )
    val increment = ExtendedSubtypeData(
        serialName = Modification.Increment.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "add",
    )
    val multiply = ExtendedSubtypeData(
        serialName = Modification.Multiply.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "multiply by",
    )
    val appendString = ExtendedSubtypeData(
        serialName = Modification.AppendString.serializer().descriptor.serialName,
        niceName = "append",
    )
    val appendRawString = ExtendedSubtypeData(
        serialName = Modification.AppendRawString.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "append",
    )
    val listAppend = ExtendedSubtypeData(
        serialName = Modification.ListAppend.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "add items",
    )
    val listRemove = ExtendedSubtypeData(
        serialName = Modification.ListRemove.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove where",
    )
    val listRemoveInstances = ExtendedSubtypeData(
        serialName = Modification.ListRemoveInstances.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove items",
    )
    val listDropFirst = ExtendedSubtypeData(
        serialName = Modification.ListDropFirst.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop first",
    )
    val listDropLast = ExtendedSubtypeData(
        serialName = Modification.ListDropLast.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop last",
    )
    val listPerElement = ExtendedSubtypeData(
        serialName = "ListPerElement",
        niceName = "for each",
    )
    val setAppend = ExtendedSubtypeData(
        serialName = Modification.SetAppend.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "add items",
    )
    val setRemove = ExtendedSubtypeData(
        serialName = Modification.SetRemove.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove where",
    )
    val setRemoveInstances = ExtendedSubtypeData(
        serialName = Modification.SetRemoveInstances.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove items",
    )
    val setDropFirst = ExtendedSubtypeData(
        serialName = Modification.SetDropFirst.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop first",
    )
    val setDropLast = ExtendedSubtypeData(
        serialName = Modification.SetDropLast.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop last",
    )
    val setPerElement = ExtendedSubtypeData(
        serialName = "SetPerElement",
        niceName = "for each",
    )
    val combine = ExtendedSubtypeData(
        serialName = Modification.Combine.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "merge",
    )
    val modifyByKey = ExtendedSubtypeData(
        serialName = Modification.ModifyByKey.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "modify keys",
    )
    val removeKeys = ExtendedSubtypeData(
        serialName = Modification.RemoveKeys.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove keys",
    )

    val all = listOf(
        nothing,
        chain,
        ifNotNull,
        assign,
        coerceAtMost,
        coerceAtLeast,
        increment,
        multiply,
        appendString,
        appendRawString,
        listAppend,
        listRemove,
        listRemoveInstances,
        listDropFirst,
        listDropLast,
        listPerElement,
        setAppend,
        setRemove,
        setRemoveInstances,
        setDropFirst,
        setDropLast,
        setPerElement,
        combine,
        modifyByKey,
        removeKeys,
    ).associateBy { it.serialName }

    val MySealedClassSerializer.Option<Modification<Any?>, *>.extended
        get() = all[serializer.descriptor.serialName]

    @Suppress("UNCHECKED_CAST")
    override fun form(
        context: RenderContext<Modification<Any?>>,
        value: MutableReactive<Modification<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit {
        val options = (context.serializer as ModificationSerializer<Any?>).options

        return {
            val type = value.lens(
                get = { options.find { o -> o.isInstance(it) } ?: options.first() },
                set = { it.serializer.default() }
            )
            row {
                centered.sizeConstraints(width = options.maxOf { (it.extended?.niceName ?: it.serializer.displayName).length.times(0.6).rem }).card.select {
                    bind(type, Constant(options)) { it.extended?.niceName ?: it.serializer.displayName }
                }
                expanding.frame {
                    reactive {
                        val selectedType = type()
                        clearChildren()
                        val subSerializer = selectedType.serializer as KSerializer<Any>
                        val subContext =
                            RenderContext(subSerializer as KSerializer<Modification<Any?>>, context.fieldAnnotations)
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
        context: RenderContext<Modification<Any?>>,
        value: Reactive<Modification<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit {
        val options = (context.serializer as ModificationSerializer<Any?>).options

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
                            RenderContext(subSerializer as KSerializer<Modification<Any?>>, context.fieldAnnotations)
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
        context: RenderContext<Modification<Any?>>,
        value: Reactive<Modification<Any?>>,
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
        context: RenderContext<Modification<Any?>>,
        value: MutableReactive<Modification<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        fieldWithoutBorder(label, description) { form(context, value, module)() }
    }

    override fun labeledView(
        context: RenderContext<Modification<Any?>>,
        value: Reactive<Modification<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        fieldWithoutBorder(label, description) { view(context, value, module)() }
    }
}

fun FormModule.registerModification() {
    register(Selector(type = "com.lightningkite.services.database.Modification"), ModificationRenderer)
}
