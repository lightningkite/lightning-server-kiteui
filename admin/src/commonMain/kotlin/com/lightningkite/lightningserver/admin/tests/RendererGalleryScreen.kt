@file:OptIn(ExperimentalUnsignedTypes::class)

package com.lightningkite.lightningserver.admin.tests

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.fileName
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.RenderContext
import com.lightningkite.kiteui.forms.cellForm
import com.lightningkite.kiteui.forms.cellView
import com.lightningkite.kiteui.forms.defaults
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.models.SelectedSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.compact
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.dynamicThemed
import com.lightningkite.kiteui.views.direct.align
import com.lightningkite.kiteui.views.direct.scrolling
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.l2.lazyColumn
import com.lightningkite.lightningserver.admin.HomePage
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.services.data.Description
import com.lightningkite.services.data.Multiline
import com.lightningkite.services.database.SerializableAnnotation
import com.lightningkite.services.database.SerializationRegistry
import com.lightningkite.services.database.default
import com.lightningkite.services.files.ServerFile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
private data class GalleryNested(
    @Description("Display name") val name: String = "Sample",
    val count: Int = 7,
)

@Serializable
private data class GalleryDemo(
    val active: Boolean = true,
    val title: String = "Hello",
    @Multiline val notes: String = "Line one\nLine two",
    val quantity: Int = 42,
    val price: Double = 3.14,
    val tags: List<String> = listOf("alpha", "beta"),
    val nested: GalleryNested = GalleryNested(),
)

@Serializable
private enum class GalleryEnum { OPTION_A, OPTION_B, OPTION_C }

private data class TestCase(
    val label: String,
    val typeDescription: String,
    val context: RenderContext<Any?>,
    val value: MutableReactive<Any?>,
)

@Suppress("UNCHECKED_CAST")
private fun resolveSerializer(typeName: String): KSerializer<Any?>? = try {
    if (typeName.endsWith("?")) {
        val inner = SerializationRegistry.master.get(typeName.removeSuffix("?"), arrayOf())
            ?: return null
        (inner as KSerializer<Any>).nullable
    } else {
        SerializationRegistry.master.get(typeName, arrayOf())
    }
} catch (e: Throwable) {
    null
}

@Suppress("UNCHECKED_CAST")
private fun makeCase(
    serializer: KSerializer<*>,
    label: String,
    typeDescription: String,
    annotationFqns: List<String> = emptyList(),
    initial: Any? = null,
): TestCase? = try {
    val s = serializer as KSerializer<Any?>
    val annotations = annotationFqns.map { SerializableAnnotation(fqn = it, values = emptyMap()) }
    val v: Any? = initial ?: s.default()
    TestCase(
        label = label,
        typeDescription = typeDescription,
        context = RenderContext(s, annotations),
        value = Signal(v),
    )
} catch (e: Throwable) {
    null
}

private fun autoDiscoveredCases(module: FormModule): List<TestCase> =
    module.allRenderers.mapNotNull { (selector, renderer) ->
        val typeName = selector.type ?: return@mapNotNull null
        val s = resolveSerializer(typeName) ?: return@mapNotNull null
        val annotationSuffix = selector.annotation?.let { " @${it.substringAfterLast('.')}" } ?: ""
        makeCase(
            serializer = s,
            label = "${renderer.name} — ${s.displayName}",
            typeDescription = "$typeName$annotationSuffix",
            annotationFqns = listOfNotNull(selector.annotation),
        )
    }

// Cases the auto-discovery can't synthesize (parametric types, kind-only renderers,
// nested data classes, etc.). These exercise the structural renderers (List, Set,
// Map, Table, DataClass, Enum) and annotation-driven variants like @Multiline.
private fun manualCases(): List<TestCase> = listOfNotNull(
    makeCase(
        serializer = String.serializer(),
        label = "Multiline String — String",
        typeDescription = "kotlin.String @Multiline",
        annotationFqns = listOf("com.lightningkite.services.data.Multiline"),
        initial = "First line\nSecond line\nThird line",
    ),
    makeCase(
        serializer = ListSerializer(String.serializer()),
        label = "List — List<String>",
        typeDescription = "kotlin.collections.List<kotlin.String>",
        initial = listOf("apple", "banana", "cherry"),
    ),
    makeCase(
        serializer = ListSerializer(Int.serializer()),
        label = "List — List<Int>",
        typeDescription = "kotlin.collections.List<kotlin.Int>",
        initial = listOf(1, 2, 3, 5, 8),
    ),
    makeCase(
        serializer = SetSerializer(String.serializer()),
        label = "Set — Set<String>",
        typeDescription = "kotlin.collections.Set<kotlin.String>",
        initial = setOf("red", "green", "blue"),
    ),
    makeCase(
        serializer = SetSerializer(GalleryEnum.serializer()),
        label = "Enum Set — Set<GalleryEnum>",
        typeDescription = "kotlin.collections.Set<GalleryEnum>",
        initial = setOf(GalleryEnum.OPTION_A, GalleryEnum.OPTION_C),
    ),
    makeCase(
        serializer = MapSerializer(String.serializer(), Int.serializer()),
        label = "Map — Map<String, Int>",
        typeDescription = "kotlin.collections.Map<String, Int>",
        initial = mapOf("one" to 1, "two" to 2, "three" to 3),
    ),
    makeCase(
        serializer = GalleryEnum.serializer(),
        label = "Enum — GalleryEnum",
        typeDescription = "GalleryEnum",
        initial = GalleryEnum.OPTION_B,
    ),
    makeCase(
        serializer = GalleryNested.serializer(),
        label = "Object — GalleryNested",
        typeDescription = "GalleryNested",
    ),
    makeCase(
        serializer = GalleryDemo.serializer(),
        label = "Object — GalleryDemo",
        typeDescription = "GalleryDemo (data class with various field types)",
    ),
    makeCase(
        serializer = ListSerializer(GalleryNested.serializer()),
        label = "Table — List<GalleryNested>",
        typeDescription = "kotlin.collections.List<GalleryNested>",
        initial = listOf(
            GalleryNested("Alpha", 1),
            GalleryNested("Beta", 2),
            GalleryNested("Gamma", 3),
            GalleryNested("Delta", 4),
        ),
    ),
)

@Routable("renderer-gallery")
class RendererGalleryScreen : Page {
    @QueryParameter
    val filter = Signal<String>("")

    override fun ElementWriter.CanAddTheme.render() {
        val module = FormModule().apply {
            defaults()
            enableRendererSwitching = true
            fileUpload = { ServerFile("https://example.com/uploads/${it.fileName()}") }
        }

        val cases = (autoDiscoveredCases(module) + manualCases())
            .sortedBy { it.label.lowercase() }
        val selected = Signal<TestCase?>(cases.firstOrNull())
        val visibleCases = remember {
            val q = filter().trim().lowercase()
            if (q.isEmpty()) cases
            else cases.filter { it.label.lowercase().contains(q) || it.typeDescription.lowercase().contains(q) }
        }

        row {
            sizeConstraints(width = 22.rem).card.col {
                h2("Renderers")
                fieldTheme.textInput {
                    hint = "Filter by name or type…"
                    content bind filter
                }
                subtext { ::content { "${visibleCases().size} of ${cases.size} test cases" } }
                expanding.scrolling.column {
                    forEach(visibleCases) { case ->
                        dynamicThemed { if (selected() === case) SelectedSemantic else null }.compact.card.button {
                            col {
                                text {
                                    content = case.label
                                    wraps = true
                                }
                                subtext {
                                    content = case.typeDescription
                                    wraps = true
                                }
                            }
                            onClick { selected.value = case }
                        }
                    }
                }
            }

            expanding.scrolling.col {
                reactive {
                    clearChildren()
                    val case = selected() ?: return@reactive
                    h1(case.label)
                    subtext(case.typeDescription)

                    card.col {
                        h2("Form")
                        subtext("Editable form")
                        try {
                            module.form(case.context, case.value)(this)
                        } catch (e: Throwable) {
                            text("Form rendering failed: ${e::class.simpleName}: ${e.message}")
                        }
                    }
                    card.col {
                        h2("View")
                        subtext("Read-only view")
                        try {
                            module.view(case.context, case.value)(this)
                        } catch (e: Throwable) {
                            text("View rendering failed: ${e::class.simpleName}: ${e.message}")
                        }
                    }
                    card.col {
                        h2("Cell")
                        subtext("Compact form/view used in tables")
                        align(Align.Start, Align.Center).sizeConstraints(width = 14.rem).card.col {
                            subtext("Cell Form")
                            try {
                                module.cellForm(case.context, case.value)(this)
                            } catch (e: Throwable) {
                                text("Cell form failed: ${e::class.simpleName}: ${e.message}")
                            }
                        }
                        align(Align.Start, Align.Center).sizeConstraints(width = 14.rem).card.col {
                            subtext("Cell View")
                            try {
                                module.cellView(case.context, case.value)(this)
                            } catch (e: Throwable) {
                                text("Cell view failed: ${e::class.simpleName}: ${e.message}")
                            }
                        }
                    }
                    card.col {
                        h2("Current Value")
                        text { ::content { case.value().toString() } }
                    }
                    card.col {
                        link {
                            text("← Back to Home")
                            ::to { { HomePage() } }
                        }
                    }
                }
            }
        }
    }
}
