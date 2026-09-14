// by Claude
@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ElementWriter
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
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.ModificationSerializer
import com.lightningkite.services.database.MySealedClassSerializer
import com.lightningkite.services.database.MySealedClassSerializerInterface
import com.lightningkite.services.database.default
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.NothingSerializer

public object ModificationRenderer : Renderer<Modification<Any?>> {
    override val name: String = "Fill In The Blank"
    public val matchesSerializer: String = "com.lightningkite.services.database.Modification"

    override fun priority(context: RenderContext<Modification<Any?>>, module: FormModule): Float {
        return if (context.serializer.descriptor.serialName == matchesSerializer) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Modification<Any?>>, module: FormModule): Double = 20.0

    public data class ExtendedSubtypeData(
        val serialName: String,
        val niceName: String,
    )

    public val nothing: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.Nothing.serializer().descriptor.serialName,
        niceName = "no change",
    )
    public val chain: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.Chain.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "multiple",
    )
    public val ifNotNull: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.IfNotNull.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "if present",
    )
    public val assign: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.Assign.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "set to",
    )
    public val coerceAtMost: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.CoerceAtMost.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "cap at",
    )
    public val coerceAtLeast: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.CoerceAtLeast.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "floor at",
    )
    public val increment: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.Increment.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "add",
    )
    public val multiply: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.Multiply.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "multiply by",
    )
    public val appendString: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.AppendString.serializer().descriptor.serialName,
        niceName = "append",
    )
    public val appendRawString: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.AppendRawString.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "append",
    )
    public val listAppend: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.ListAppend.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "add items",
    )
    public val listRemove: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.ListRemove.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove where",
    )
    public val listRemoveInstances: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.ListRemoveInstances.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove items",
    )
    public val listDropFirst: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.ListDropFirst.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop first",
    )
    public val listDropLast: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.ListDropLast.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop last",
    )
    public val listPerElement: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = "ListPerElement",
        niceName = "for each",
    )
    public val setAppend: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.SetAppend.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "add items",
    )
    public val setRemove: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.SetRemove.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove where",
    )
    public val setRemoveInstances: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.SetRemoveInstances.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove items",
    )
    public val setDropFirst: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.SetDropFirst.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop first",
    )
    public val setDropLast: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.SetDropLast.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "drop last",
    )
    public val setPerElement: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = "SetPerElement",
        niceName = "for each",
    )
    public val combine: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.Combine.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "merge",
    )
    public val removeKeys: ExtendedSubtypeData = ExtendedSubtypeData(
        serialName = Modification.RemoveKeys.serializer(NothingSerializer()).descriptor.serialName,
        niceName = "remove keys",
    )

    public val all: Map<String, ExtendedSubtypeData> = listOf(
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
        removeKeys,
    ).associateBy { it.serialName }

    public val MySealedClassSerializer.Option<Modification<Any?>, *>.extended: ExtendedSubtypeData?
        get() = all[serializer.descriptor.serialName]

    @Suppress("UNCHECKED_CAST")
    override fun form(
        context: RenderContext<Modification<Any?>>,
        value: MutableReactive<Modification<Any?>>,
        module: FormModule
    ): ElementWriter.CanAddTheme.() -> Unit {
        val options = (context.serializer as ModificationSerializer<Any?>).options

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
    ): ElementWriter.CanAddTheme.() -> Unit {
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
    ): ElementWriter.CanAddTheme.() -> Unit {
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
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { form(context, value, module)() }
    }

    override fun labeledView(
        context: RenderContext<Modification<Any?>>,
        value: Reactive<Modification<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { view(context, value, module)() }
    }
}

public fun FormModule.registerModification() {
    register(Selector(type = "com.lightningkite.services.database.Modification"), ModificationRenderer)
}
