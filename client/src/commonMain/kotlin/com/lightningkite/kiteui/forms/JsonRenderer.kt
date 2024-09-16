package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.ThemeDerivation
import com.lightningkite.kiteui.models.systemDefaultFixedWidthFont
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.reactive.bind
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.reactive.lens
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.direct.textArea
import kotlinx.serialization.json.Json

object JsonRenderer : ViewRenderer.Generator, FormRenderer.Generator {
    val json = Json(DefaultJson) { prettyPrint = true }
    override val name: String = "JSON"
    override val basePriority: Float
        get() = 0.1f

    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        return ViewRenderer(module, this, selector) { _, it ->
            ThemeDerivation {
                it.copy(font = it.font.copy(font = systemDefaultFixedWidthFont)).withoutBack
            }.onNext - text {
                ::content {
                    json.encodeToString(selector.serializer, it())
                }
            }
        }
    }

    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        return FormRenderer(module, this, selector) { _, it ->
            ThemeDerivation {
                it.copy(font = it.font.copy(font = systemDefaultFixedWidthFont)).withoutBack
            }.onNext - textArea {
                content bind it.lens(
                    get = { json.encodeToString(selector.serializer, it) },
                    modify = { o, it ->
                        try {
                            json.decodeFromString(selector.serializer, it)
                        } catch (e: Exception) {
                            o
                        }
                    }
                )
            }
        }
    }
}