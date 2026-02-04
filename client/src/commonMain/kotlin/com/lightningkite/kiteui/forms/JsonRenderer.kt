@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.components.CodeBlockSemantic
import com.lightningkite.kiteui.models.Dimension
import com.lightningkite.kiteui.models.PrintSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.MySealedClassSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.Json

/**
 * Renderer that displays any value as JSON text.
 *
 * Useful for debugging, advanced editing, or when no specialized renderer exists.
 * Lower priority than most renderers so it acts as a fallback/alternative.
 *
 * by Claude
 */
object JsonRenderer : Renderer<Any?> {
    override val name: String = "JSON"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override fun priority(context: RenderContext<Any?>, module: FormModule): Float {
        // Low priority - should only be selected via switcher or as fallback
        return 0.1f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any?>, value: MutableReactive<Any?>, module: FormModule): ViewWriter.() -> Unit {
        val serializer = context.serializer as KSerializer<Any?>

        return {
            val errorMessage = Signal<String?>(null)

            col {
                themed(CodeBlockSemantic)/*.sizeConstraints(height = height(context, module)).scrolling*/.textArea {
                    // Convert value to JSON for display
                    val jsonLens = value.lens(
                        get = {
                            try {
                                json.encodeToString(serializer, it)
                            } catch (e: Exception) {
                                "Error encoding: ${e.message}"
                            }
                        },
                        modify = { original, newJson ->
                            try {
                                errorMessage.value = null
                                json.decodeFromString(serializer, newJson)
                            } catch (e: Exception) {
                                errorMessage.value = "Parse error: ${e.message}"
                                original  // Keep original on error
                            }
                        }
                    )
                    content bind jsonLens
                }
                // Show parse errors
                text {
                    ::shown { errorMessage() != null }
                    ::content { errorMessage() ?: "" }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any?>, value: Reactive<Any?>, module: FormModule): ViewWriter.() -> Unit {
        val serializer = context.serializer as KSerializer<Any?>

        return {
            themed(CodeBlockSemantic)/*.sizeConstraints(height = height(context, module)).scrolling*/.text {
                wraps = true
                ::content {
                    try {
                        json.encodeToString(serializer, value())
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                }
            }
        }
    }

    override fun cellView(context: RenderContext<Any?>, value: Reactive<Any?>, module: FormModule): ViewWriter.() -> Unit = {
        // Compact JSON (no pretty print) for cells
        val compactJson = Json { encodeDefaults = false }
        @Suppress("UNCHECKED_CAST")
        val serializer = context.serializer as KSerializer<Any?>

        text {
            ellipsis = true
            ::content {
                try {
                    compactJson.encodeToString(serializer, value())
                } catch (e: Exception) {
                    "Error"
                }
            }
        }
    }

    override fun columnWidth(context: RenderContext<Any?>, module: FormModule) = 20.0
    fun height(context: RenderContext<Any?>, module: FormModule): Dimension {
        val seen = HashSet<String>()
        fun traverse(descriptor: SerialDescriptor): Int {
            if(!seen.add(descriptor.serialName)) return 1
            when(descriptor.serialName) {
                "com.lightningkite.services.database.Condition",
                "com.lightningkite.services.database.Modification" -> return 5
            }
            if(descriptor.isInline) return descriptor.elementDescriptors.sumOf { traverse(it) }
            return when (descriptor.kind) {
                StructureKind.LIST -> descriptor.elementDescriptors.sumOf { traverse(it) } * 3 + 2
                StructureKind.MAP -> descriptor.elementDescriptors.sumOf { traverse(it) } * 3 + 2
                SerialKind.ENUM -> 1
                is PrimitiveKind -> 1
                StructureKind.OBJECT -> 1
                StructureKind.CLASS -> descriptor.elementDescriptors.sumOf { traverse(it) } + 2
                PolymorphicKind.OPEN -> 5
                PolymorphicKind.SEALED -> 5
                SerialKind.CONTEXTUAL -> 1
            }
        }
        return traverse(context.serializer.descriptor).plus(1).times(1.5).rem
    }

    override fun labeledForm(
        context: RenderContext<Any?>,
        value: MutableReactive<Any?>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        fieldWithoutBorder(label, description) {
            form(context, value, module)()
        }
    }

    override fun labeledView(
        context: RenderContext<Any?>,
        value: Reactive<Any?>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        fieldWithoutBorder(label, description) {
            view(context, value, module)()
        }
    }
}

fun FormModule.registerJson() {
    // Register for all types via catch-all selector
    register(Selector(), JsonRenderer)
}
