package com.lightningkite.kiteui.forms

import com.lightningkite.UUID
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.uuid
import kotlinx.datetime.*

object BuiltinRendering {
    init {
        ViewRenderer.forTypeWithField<Boolean>(FormSize.Inline) { field, it ->
            text {
                ::content {
                    if (it()) "✓ ${field?.descriptionOrDisplayName ?: ""}"
                    else "✘ ${field?.descriptionOrDisplayName ?: ""}"
                }
            }
        }
        ViewRenderer.forTypeWithField<Boolean?>(FormSize.Inline) { field, it ->
            text {
                ::content {
                    when (it()) {
                        true -> "✓ ${field?.descriptionOrDisplayName ?: ""}"
                        false -> "✘ ${field?.descriptionOrDisplayName ?: ""}"
                        null -> "- ${field?.descriptionOrDisplayName ?: ""}"
                    }
                }
            }
        }
        FormRenderer.forTypeWithField<Boolean>(FormSize.Inline) { field, it ->
            row {
                centered - checkbox {
                    checked bind it
                }
                field?.descriptionOrDisplayName?.let {
                    centered - text(it)
                }
            }
        }
        FormRenderer.forType<Boolean?>(FormSize.Inline) { it ->
            fieldTheme - select {
                bind(it, Constant(listOf(null, true, false))) {
                    when (it) {
                        true -> "Yes"
                        false -> "No"
                        null -> "N/A"
                    }
                }
            }
        }
        FormRenderer.forType<Byte?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt()?.toByte() }) } }
        FormRenderer.forType<Short?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt()?.toShort() }) } }
        FormRenderer.forType<Int?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt() }) } }
        FormRenderer.forType<Long?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it?.toDouble() }, set = { it?.toLong() }) } }
        FormRenderer.forType<Byte>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toInt()?.toByte() ?: o }) } }
        FormRenderer.forType<Short>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toInt()?.toShort() ?: o }) } }
        FormRenderer.forType<Int>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toInt() ?: o }) } }
        FormRenderer.forType<Long>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toLong() ?: o }) } }
        FormRenderer.forType<Byte?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it?.toString(16) ?: "" }, set = { it?.toByteOrNull(16) }) } }
        FormRenderer.forType<Short?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it?.toString(16) ?: "" }, set = { it?.toShortOrNull(16) }) } }
        FormRenderer.forType<Int?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it?.toString(16) ?: "" }, set = { it?.toIntOrNull(16) }) } }
        FormRenderer.forType<Long?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it?.toString(16) ?: "" }, set = { it?.toLongOrNull(16) }) } }
        FormRenderer.forType<Byte>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it.toString(16) }, modify = { o, it -> it?.toByteOrNull(16) ?: o }) } }
        FormRenderer.forType<Short>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it.toString(16) }, modify = { o, it -> it?.toShortOrNull(16) ?: o }) } }
        FormRenderer.forType<Int>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it.toString(16) }, modify = { o, it -> it?.toIntOrNull(16) ?: o }) } }
        FormRenderer.forType<Long>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it -> fieldTheme - textField { content bind it.lens(get = { it.toString(16) }, modify = { o, it -> it?.toLongOrNull(16) ?: o }) } }
        FormRenderer.forType<Float?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it?.toDouble() }, set = { it?.toFloat() }) } }
        FormRenderer.forType<Double?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it } }
        FormRenderer.forType<Float>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toFloat() ?: o }) } }
        FormRenderer.forType<Double>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.nullable() } }
        FormRenderer.forType<Char>(FormSize.Inline, name = "Character") { it -> fieldTheme - textField { content bind it.lens(get = { it.toString() }, modify = { o, it -> it.firstOrNull() ?: o }) } }
        FormRenderer.forType<Char?>(FormSize.Inline, name = "Character") { it -> fieldTheme - textField { content bind it.lens(get = { it.toString() }, modify = { o, it -> it.firstOrNull() }) } }
        FormRenderer.forType<String>(FormSize.Inline, name = "Text") { it -> fieldTheme - textField { content bind it } }
        FormRenderer.forType<UUID>(FormSize.Inline) { it ->
            fieldTheme - textField {
                content bind it.lens(get = { it.toString() }, modify = { o, it ->
                    try {
                        uuid(it)
                    } catch (e: Exception) {
                        o
                    }
                })
            }
        }
        FormRenderer.forType<ServerFile>(FormSize.Inline) { it -> text("TODO") }
        FormRenderer.forType<Map<Unit, Unit>>(FormSize.Inline) { it -> text("TODO") }
        FormRenderer.forType<Instant>(FormSize.Inline) { prop ->
            fieldTheme - localDateTimeField {
                content bind prop.lens(
                    get = { it.toLocalDateTime(TimeZone.currentSystemDefault()) },
                    modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) ?: old },
                )
            }
        }
        FormRenderer.forType<Instant?>(FormSize.Inline) { prop ->
            fieldTheme - localDateTimeField {
                content bind prop.lens(
                    get = { it?.toLocalDateTime(TimeZone.currentSystemDefault()) },
                    modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) },
                )
            }
        }
        FormRenderer.forType<LocalDateTime>(FormSize.Inline) { prop -> fieldTheme - localDateTimeField { content bind prop.lens(get = { it }, modify = { old, it -> it ?: old }) } }
        FormRenderer.forType<LocalDateTime?>(FormSize.Inline) { prop -> fieldTheme - localDateTimeField { content bind prop } }
        FormRenderer.forType<LocalDate>(FormSize.Inline) { prop -> fieldTheme - localDateField { content bind prop.lens(get = { it }, modify = { old, it -> it ?: old }) } }
        FormRenderer.forType<LocalDate?>(FormSize.Inline) { prop -> fieldTheme - localDateField { content bind prop } }
        FormRenderer.forType<LocalTime>(FormSize.Inline) { prop -> fieldTheme - localTimeField { content bind prop.lens(get = { it }, modify = { old, it -> it ?: old }) } }
        FormRenderer.forType<LocalTime?>(FormSize.Inline) { prop -> fieldTheme - localTimeField { content bind prop } }
        FormRenderer += HorizontalListRenderer
        FormRenderer += VerticalListRenderer
        FormRenderer += HorizontalSetRenderer
        FormRenderer += VerticalSetRenderer
        FormRenderer += PathPartsRenderer
        FormRenderer += SortPathRenderer
        ViewRenderer += HorizontalListRenderer
        ViewRenderer += VerticalListRenderer
        ViewRenderer += HorizontalSetRenderer
        ViewRenderer += VerticalSetRenderer
        ViewRenderer += SortPathRenderer

        ViewRenderer += ToStringRenderer

        ViewRenderer += EnumFormRenderer
        FormRenderer += EnumFormRenderer

        ViewRenderer += ByFieldRenderer
        FormRenderer += ByFieldRenderer

        ViewRenderer += NullableFormRenderer
        FormRenderer += NullableFormRenderer

        ViewRenderer += ObjectRenderer
        FormRenderer += ObjectRenderer

        ViewRenderer += WrapperViewRenderer
        FormRenderer += WrapperFormRenderer

        FormRenderer += MySealedFormRenderer
    }
}

