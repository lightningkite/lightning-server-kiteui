package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.db.LimitReadable
import com.lightningkite.serialization.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

object TableRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Table"
    override val type: String = ListSerializer(Unit.serializer()).descriptor.serialName
    override fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize.Block
    fun add(collection: List<Any?>, item: Any?): List<Any?> = collection + item
    fun remove(collection: List<Any?>, item: Any?, index: Int): List<Any?> = collection.toMutableList().apply { this.removeAt(index) }
    fun inner(serializer: KSerializer<*>): KSerializer<Any?> = serializer.listElement()!! as KSerializer<Any?>
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return super<FormRenderer.Generator>.matches(module, selector) && inner(selector.serializer).serializableProperties != null
    }

    val flp = FormLayoutPreferences(25.0, 10.0)

    override fun priority(module: FormModule, selector: FormSelector<*>): Float {
        val innerSer = inner(selector.serializer)
        val inner = module.form(selector.copy(innerSer, desiredSize = flp)) as FormRenderer<Any?>
        return super<FormRenderer.Generator>.priority(module, selector) * (if (inner.size == FormSize.Block) 1.1f else 0.6f)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSer = selector.serializer.listElement()!!
        val inner = module.form(selector.copy(innerSer, desiredSize = flp)) as FormRenderer<Any?>
        return FormRenderer(module, this, selector as FormSelector<List<Any?>>) { _, writable ->
            text("TODO")
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val innerSer = inner(selector.serializer as KSerializer<List<Any?>>)
        return ViewRenderer(module, this, selector as FormSelector<List<Any?>>) { _, readable ->
            sizeConstraints(height = 30.rem) - view(this, module, innerSer, Constant(readable))
        } as ViewRenderer<T>
    }

    fun <T> view(
        writer: ViewWriter,
        formModule: FormModule,
        innerSer: KSerializer<T>,
        readable: Readable<Readable<List<T>>>,
        columns: ImmediateWritable<List<DataClassPath<T, *>>> = Property(run {
            val all = ArrayList<DataClassPath<T, *>>()
            fun <K> checkDataClass(around: DataClassPath<T, K>, properties: Array<SerializableProperty<K, Any?>>) {
                for(prop in properties) {
                    prop.serializer.serializableProperties?.let { subs ->
                        @Suppress("UNCHECKED_CAST")
                        checkDataClass<Any?>(DataClassPathAccess(around, prop), subs as Array<SerializableProperty<Any?, Any?>>)
                    } ?: all.add(DataClassPathAccess(around, prop))
                }
            }
            @Suppress("UNCHECKED_CAST")
            checkDataClass(DataClassPathSelf(innerSer), innerSer.serializableProperties!! as Array<SerializableProperty<T, Any?>>)
            all
        }),
        link: ((T) -> () -> Screen)? = null,
        action: (suspend (T) -> Unit)? = null,
    ) = with(writer) {
        val properties = innerSer.serializableProperties!! as Array<SerializableProperty<T, Any?>>
        val rendererCache = HashMap<DataClassPath<T, Any?>, ViewRenderer<Any?>>()
        val anyCols = columns as ImmediateWritable<List<DataClassPath<T, Any?>>>
        fun renderer(path: DataClassPath<T, Any?>) = rendererCache.getOrPut(path) {
            formModule.view(FormSelector(
                serializer = path.serializer,
                annotations = path.properties.lastOrNull()?.serializableAnnotations ?: listOf(),
                desiredSize = flp,
                handlesField = true
            ))
        }
        scrollsHorizontally - col {
            expanding - changingSizeConstraints {
                SizeConstraints(width = anyCols().sumOf { renderer(it).size.approximateWidth + 2.0 }.rem)
            } - col {
                padded - row {
                    row {
                        forEach(anyCols) {
                            sizeConstraints(width = renderer(it).size.approximateWidth.rem) - important - row {
                                centered - expanding - text(it.properties.joinToString(" ") { it.displayName })
                                button {
                                    spacing = 0.px
                                    centered - icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove Column")
                                    onClick {
                                        anyCols.value -= it
                                    }
                                }
                            }
                        }
                    }
                    menuButton {
                        spacing = 0.px
                        centered - icon(Icon.add.copy(width = 1.rem, height = 1.rem), "Add")
                        preferredDirection = PopoverPreferredDirection.belowLeft
                        requireClick = true
                        opensMenu {
                            val newField = Property<DataClassPathPartial<T>>(DataClassPathSelf(innerSer))
                            col {
                                form(formModule, DataClassPathSerializer(innerSer), newField)
                                button {
                                    text("OK")
                                    onClick {
                                        @Suppress("UNCHECKED_CAST")
                                        anyCols.value += newField.value as DataClassPath<T, Any?>
                                        closePopovers()
                                    }
                                }
                            }
                        }
                    }
                }
                expanding - recyclerView {
                    reactive {
                        val inner = readable()
                        if (inner is LimitReadable<T>) {
                            if (inner.limit < lastVisibleIndex() + 50) {
                                inner.limit = lastVisibleIndex() + 100
                            }
                        }
                    }
                    children(shared { readable()() }) {
                        fun ViewWriter.content() = row {
                            forEach(anyCols) { col ->
                                val render = renderer(col)
//                            sizeConstraints(width = 20.rem) - text {
//                                ::content { col.getAny(it()).toString() }
//                            }
                                @Suppress("UNCHECKED_CAST")
                                padded - sizeConstraints(width = render.size.approximateWidth.rem) - render.render(
                                    this@row,
                                    null,
                                    it.lensPath(col)
                                )
                            }
                        }
                        if (link != null) {
                            card - link {
                                content()
                                ::to { link(it()) }
                            }
                        } else if (action != null) {
                            card - button {
                                content()
                                onClick { action(it()) }
                            }
                        } else {
                            card - content()
                        }

                    }
                }
            }
        }
    }
}
