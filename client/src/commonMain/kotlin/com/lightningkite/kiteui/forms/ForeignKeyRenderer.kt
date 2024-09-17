package com.lightningkite.kiteui.forms

import com.lightningkite.CaselessStringSerializer
import com.lightningkite.TrimmedCaselessStringSerializer
import com.lightningkite.TrimmedStringSerializer
import com.lightningkite.kiteui.ExternalServices
import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.launch
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.serialization.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

object ForeignKeyRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Foreign Key"
    override val annotation: String? get() = "com.lightningkite.lightningdb.References"
    override val basePriority: Float
        get() = 2f

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize(16.0, 1.0)
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.lightningdb.References" ||
                    it.fqn == "com.lightningkite.lightningdb.MultipleReferences"
        }?.values ?: return false
        val typeName = anno.get("references")?.let { it as? SerializableAnnotationValue.ClassValue }?.fqn ?: return false
        val typeInfo = module.typeInfo(typeName) as? FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>> ?: return false
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.lightningdb.References" ||
                    it.fqn == "com.lightningkite.lightningdb.MultipleReferences"
        }!!.values
        val typeName = anno.get("references")!!.let { it as SerializableAnnotationValue.ClassValue }.fqn
        val typeInfo = module.typeInfo(typeName)!! as FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        return FormRenderer(module, this, selector as FormSelector<Comparable<Comparable<*>>?>) { field, writable ->
            row {
                expanding - menuButton {
                    requireClick = true
                    gravity(Align.Start, Align.Center) - text {
                        reactiveSuspending {
                            content = writable()?.let { typeInfo.renderToString(it) } ?: "None"
                        }
                    }
                    opensMenu {
                        if(selector.serializer.descriptor.isNullable) {
                            launch { writable set null }
                        }
                        preferredDirection = PopoverPreferredDirection.belowLeft
                        sizeConstraints(width = 25.rem, height = 25.rem) - col {
                            centered - h2("Select")
                            val condition = Property<Condition<HasId<Comparable<Comparable<*>>>>>(Condition.Always)
                            val sort = Property<List<SortPart<HasId<Comparable<Comparable<*>>>>>>(listOf())
                            form(module, Condition.serializer(typeInfo.cache.serializer), condition)
                            form(module, ListSerializer(SortPartSerializer(typeInfo.cache.serializer)), sort)
                            expanding - TableRenderer.view(
                                formModule = module,
                                writer = this@col,
                                innerSer = typeInfo.cache.serializer,
                                readable = shared { typeInfo.cache.watch(Query(condition.debounce(500)(), sort.debounce(500)())) },
                                action = {
                                    writable.set(it._id)
                                    closePopovers()
                                }
                            )
                        }
                    }
                }
                link {
                    icon(Icon.externalLink.copy(width = 1.rem, height = 1.rem), "Open")
                    ::to label@{
                        val id = writable() ?: return@label null
                        return@label typeInfo.screen(id)
                    }
                    newTab = true
                }
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.lightningdb.References" ||
                    it.fqn == "com.lightningkite.lightningdb.MultipleReferences"
        }!!.values
        val typeName = anno.get("references")!!.let { it as SerializableAnnotationValue.ClassValue }.fqn
        val typeInfo = module.typeInfo(typeName)!! as FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        return ViewRenderer(module, this, selector as FormSelector<Comparable<Comparable<*>>?>) { field, readable ->
            link {
                ::to label@{
                    val id = readable() ?: return@label null
                    return@label typeInfo.screen(id)
                }
                text {
                    reactiveSuspending {
                        content = readable()?.let { typeInfo.renderToString(it) } ?: "None"
                    }
                }
            }
        } as ViewRenderer<T>
    }
}