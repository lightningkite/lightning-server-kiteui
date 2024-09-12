package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.PopoverPreferredDirection
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
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
    override fun size(selector: FormSelector<*>): FormSize = FormSize.Block
    fun add(collection: List<Any?>, item: Any?): List<Any?> = collection + item
    fun remove(collection: List<Any?>, item: Any?, index: Int): List<Any?> = collection.toMutableList().apply { this.removeAt(index) }
    fun inner(serializer: KSerializer<*>): KSerializer<Any?> = serializer.listElement()!! as KSerializer<Any?>
    override fun matches(selector: FormSelector<*>): Boolean {
        return super<FormRenderer.Generator>.matches(selector) && inner(selector.serializer).serializableProperties != null
    }

    val flp = FormLayoutPreferences(25.0, 10.0)

    override fun priority(selector: FormSelector<*>): Float {
        val innerSer = inner(selector.serializer)
        val inner = FormRenderer[selector.copy(innerSer, desiredSize = flp)] as FormRenderer<Any?>
        return super<FormRenderer.Generator>.priority(selector) * (if (inner.size == FormSize.Block) 1.1f else 0.6f)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(selector: FormSelector<T>): FormRenderer<T> {
        val innerSer = selector.serializer.listElement()!!
        val inner = FormRenderer[selector.copy(innerSer, desiredSize = flp)] as FormRenderer<Any?>
        return FormRenderer(this, selector as FormSelector<List<Any?>>) { _, writable ->
            text("TODO")
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> {
        val innerSer = inner(selector.serializer as KSerializer<List<Any?>>)
        return ViewRenderer(this, selector as FormSelector<List<Any?>>) { _, readable ->
            sizeConstraints(height = 30.rem) - view(this, innerSer, Constant(readable))
        } as ViewRenderer<T>
    }

    fun <T> view(
        writer: ViewWriter,
        innerSer: KSerializer<T>,
        readable: Readable<Readable<List<T>>>,
        link: ((T)->()-> Screen)? = null
    ) = with(writer) {
        val properties = innerSer.serializableProperties!! as Array<SerializableProperty<T, Any?>>
        val columns = Property<List<DataClassPath<T, Any?>>>(
            properties.take(5).map { DataClassPathAccess(DataClassPathSelf(innerSer), it) }
        )
        val rendererCache = HashMap<DataClassPath<T, Any?>, ViewRenderer<Any?>>()
        fun renderer(path: DataClassPath<T, Any?>) = rendererCache.getOrPut(path) {
            ViewRenderer[FormSelector(
                serializer = path.serializer,
                annotations = path.properties.lastOrNull()?.serializableAnnotations ?: listOf(),
                desiredSize = flp,
                handlesField = true
            )]
        }
        scrollsHorizontally - col {
            padded - row {
                expanding - row {
                    forEach(columns) {
                        sizeConstraints(width = renderer(it).size.approximateWidth.rem) - important - row {
                            centered - expanding - text(it.properties.joinToString(" ") { it.displayName })
                            button {
                                spacing = 0.px
                                centered - icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove Column")
                                onClick {
                                    columns.value -= it
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
                            form(DataClassPathSerializer(innerSer), newField)
                            button {
                                text("OK")
                                onClick {
                                    @Suppress("UNCHECKED_CAST")
                                    columns.value += newField.value as DataClassPath<T, Any?>
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
                        forEach(columns) { col ->
                            val render = renderer(col)
//                            sizeConstraints(width = 20.rem) - text {
//                                ::content { col.getAny(it()).toString() }
//                            }
                            @Suppress("UNCHECKED_CAST")
                            padded - sizeConstraints(width = render.size.approximateWidth.rem) - render.render(
                                this@row,
                                col.properties.lastOrNull(),
                                it.lensPath(col)
                            )
                        }
                    }
                    if(link != null) {
                        card - link {
                            content()
                            ::to { link(it()) }
                        }
                    } else {
                        card - content()
                    }

                }
            }
        }
    }
}
