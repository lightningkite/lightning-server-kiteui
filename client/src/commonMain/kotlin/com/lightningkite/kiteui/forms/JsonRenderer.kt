package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Semantic
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.ThemeAndBack
import com.lightningkite.kiteui.models.ThemeDerivation
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.models.systemDefaultFixedWidthFont
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.direct.textArea
import com.lightningkite.kiteui.views.fieldTheme
import kotlinx.serialization.json.Json

object JsonRenderer : ViewRenderer.Generator, FormRenderer.Generator {
    val json = Json(DefaultJson) { prettyPrint = true; allowSpecialFloatingPointValues = true; ignoreUnknownKeys = true }
    override val name: String = "JSON"
    override val basePriority: Float
        get() = 0.01f

    object JsonSemantic: Semantic("json") {
        override fun default(theme: Theme): ThemeAndBack = theme.withoutBack(font = theme.font.copy(font = systemDefaultFixedWidthFont))
    }

    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        return ViewRenderer(module, this, selector) { _, it ->
            onNext(JsonSemantic).text {
                ::content {
                    json.encodeToString(selector.serializer, it())
                }
            }
        }
    }

    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        return FormRenderer(module, this, selector) { _, it ->
            onNext(JsonSemantic).textArea {
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