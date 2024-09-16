package com.lightningkite.kiteui.forms

import com.lightningkite.UUID
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.ThemeDerivation
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.models.systemDefaultFixedWidthFont
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.lightningdb.Multiline
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.serialization.SerializableAnnotationValue
import com.lightningkite.serialization.UUIDSerializer
import com.lightningkite.uuid
import kotlinx.datetime.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

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
        FormRenderer.forType<Byte>(FormSize.Inline, name = "Number") { it ->
            fieldTheme - numberField {
                content bind it.lens(
                    get = { it.toDouble() },
                    modify = { o, it -> it?.toInt()?.toByte() ?: o })
            }
        }
        FormRenderer.forType<Short>(FormSize.Inline, name = "Number") { it ->
            fieldTheme - numberField {
                content bind it.lens(
                    get = { it.toDouble() },
                    modify = { o, it -> it?.toInt()?.toShort() ?: o })
            }
        }
        FormRenderer.forType<Int>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toInt() ?: o }) } }
        FormRenderer.forType<Long>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toLong() ?: o }) } }
        FormRenderer.forType<Byte?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(16) ?: "" },
                    set = { it?.toByteOrNull(16) })
            }
        }
        FormRenderer.forType<Short?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(16) ?: "" },
                    set = { it?.toShortOrNull(16) })
            }
        }
        FormRenderer.forType<Int?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(16) ?: "" },
                    set = { it?.toIntOrNull(16) })
            }
        }
        FormRenderer.forType<Long?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(16) ?: "" },
                    set = { it?.toLongOrNull(16) })
            }
        }
        FormRenderer.forType<Byte>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(16) },
                    modify = { o, it -> it?.toByteOrNull(16) ?: o })
            }
        }
        FormRenderer.forType<Short>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(16) },
                    modify = { o, it -> it?.toShortOrNull(16) ?: o })
            }
        }
        FormRenderer.forType<Int>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(16) },
                    modify = { o, it -> it?.toIntOrNull(16) ?: o })
            }
        }
        FormRenderer.forType<Long>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(16) },
                    modify = { o, it -> it?.toLongOrNull(16) ?: o })
            }
        }
        FormRenderer.forType<Byte?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(2) ?: "" },
                    set = { it?.toByteOrNull(2) })
            }
        }
        FormRenderer.forType<Short?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(2) ?: "" },
                    set = { it?.toShortOrNull(2) })
            }
        }
        FormRenderer.forType<Int?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(2) ?: "" },
                    set = { it?.toIntOrNull(2) })
            }
        }
        FormRenderer.forType<Long?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it?.toString(2) ?: "" },
                    set = { it?.toLongOrNull(2) })
            }
        }
        FormRenderer.forType<Byte>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(2) },
                    modify = { o, it -> it?.toByteOrNull(2) ?: o })
            }
        }
        FormRenderer.forType<Short>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(2) },
                    modify = { o, it -> it?.toShortOrNull(2) ?: o })
            }
        }
        FormRenderer.forType<Int>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(2) },
                    modify = { o, it -> it?.toIntOrNull(2) ?: o })
            }
        }
        FormRenderer.forType<Long>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
            fieldTheme - textField {
                content bind it.lens(
                    get = { it.toString(2) },
                    modify = { o, it -> it?.toLongOrNull(2) ?: o })
            }
        }
        FormRenderer.forType<Float?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it?.toDouble() }, set = { it?.toFloat() }) } }
        FormRenderer.forType<Double?>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it } }
        FormRenderer.forType<Float>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toFloat() ?: o }) } }
        FormRenderer.forType<Double>(FormSize.Inline, name = "Number") { it -> fieldTheme - numberField { content bind it.nullable() } }
        FormRenderer.forType<Char>(FormSize.Inline, name = "Character") { it -> fieldTheme - textField { content bind it.lens(get = { it.toString() }, modify = { o, it -> it.firstOrNull() ?: o }) } }
        FormRenderer.forType<Char?>(FormSize.Inline, name = "Character") { it -> fieldTheme - textField { content bind it.lens(get = { it.toString() }, modify = { o, it -> it.firstOrNull() }) } }
        FormRenderer.forType<String>(
            size = { selector ->
                val maxLengthAnno = selector.annotations.find { it.fqn == "com.lightningkite.lightningdb.MaxLength" }?.values
                val maxSize = (maxLengthAnno?.get("size") as? SerializableAnnotationValue.IntValue)?.value
                val averageSize = ((maxLengthAnno?.get("average") as? SerializableAnnotationValue.IntValue)?.value ?: maxSize)?.times(3 / 4.0) ?: 20.0

                FormSize(
                    approximateWidth = averageSize * 3.0 / 4.0,
                    approximateHeight = 1.0
                )
            },
            name = "Text",
            generate = { it -> fieldTheme - textField { content bind it } }
        )
        FormRenderer.forType<String>(
            size = FormSize(40.0, 10.0),
            name = "Large Text",
            annotation = "com.lightningkite.lightningdb.Multiline",
            priority = 2f,
            generate = { it -> fieldTheme - sizeConstraints(minHeight = 10.rem) - textArea { content bind it } }
        )
        ViewRenderer.forType<String>(
            size = FormSize(40.0, 10.0),
            name = "Large Text Summary",
            annotation = "com.lightningkite.lightningdb.Multiline",
            priority = 0.8f,
            generate = { it ->
                text {
                    ::content { it().substringBefore('\n') }
                    wraps = false
                    ellipsis = true
                }
            }
        )
        FormRenderer.forType<UUID>(FormSize(24.0, 1.0), UUIDSerializer) {
            fieldTheme - textField {
                content bind it.lens(get = { it.toString() }, modify = { o, it ->
                    try {
                        UUID.parse(it)
                    } catch (e: Exception) {
                        o
                    }
                })
            }
        }
        FormRenderer.forType<UUID>(FormSize(24.0, 1.0)) {
            fieldTheme - textField {
                content bind it.lens(get = { it.toString() }, modify = { o, it ->
                    try {
                        UUID.parse(it)
                    } catch (e: Exception) {
                        o
                    }
                })
            }
        }
        ViewRenderer.forType<UUID>(FormSize(24.0, 1.0), UUIDSerializer) {
            text { ::content { it().toString() } }
        }
        ViewRenderer.forType<UUID>(FormSize(24.0, 1.0)) {
            text { ::content { it().toString() } }
        }
        FormRenderer.forType<ServerFile>(FormSize.Inline) { it -> text("TODO") }
        FormRenderer.forType<Map<Unit, Unit>>(FormSize.Inline) { it -> text("TODO") }
        FormRenderer.forType<Instant>(FormSize(approximateWidth = 16.0, approximateHeight = 1.0)) { prop ->
            fieldTheme - localDateTimeField {
                content bind prop.lens(
                    get = { it.toLocalDateTime(TimeZone.currentSystemDefault()) },
                    modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) ?: old },
                )
            }
        }
        FormRenderer.forType<Instant?>(FormSize(approximateWidth = 16.0, approximateHeight = 1.0)) { prop ->
            fieldTheme - localDateTimeField {
                content bind prop.lens(
                    get = { it?.toLocalDateTime(TimeZone.currentSystemDefault()) },
                    modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) },
                )
            }
        }
        FormRenderer.forType<LocalDateTime>(FormSize(approximateWidth = 16.0, approximateHeight = 1.0)) { prop -> fieldTheme - localDateTimeField { content bind prop.lens(get = { it }, modify = { old, it -> it ?: old }) } }
        FormRenderer.forType<LocalDateTime?>(FormSize(approximateWidth = 16.0, approximateHeight = 1.0)) { prop -> fieldTheme - localDateTimeField { content bind prop } }
        FormRenderer.forType<LocalDate>(FormSize(approximateWidth = 11.0, approximateHeight = 1.0)) { prop -> fieldTheme - localDateField { content bind prop.lens(get = { it }, modify = { old, it -> it ?: old }) } }
        FormRenderer.forType<LocalDate?>(FormSize(approximateWidth = 11.0, approximateHeight = 1.0)) { prop -> fieldTheme - localDateField { content bind prop } }
        FormRenderer.forType<LocalTime>(FormSize(approximateWidth = 5.0, approximateHeight = 1.0)) { prop -> fieldTheme - localTimeField { content bind prop.lens(get = { it }, modify = { old, it -> it ?: old }) } }
        FormRenderer.forType<LocalTime?>(FormSize(approximateWidth = 5.0, approximateHeight = 1.0)) { prop -> fieldTheme - localTimeField { content bind prop } }

        ViewRenderer.forType<Instant>(FormSize(approximateWidth = 16.0, approximateHeight = 1.0)) { prop -> text { ::content { prop().renderToString() } } }
        ViewRenderer.forType<LocalDateTime>(FormSize(approximateWidth = 16.0, approximateHeight = 1.0)) { prop -> text { ::content { prop().renderToString() } } }
        ViewRenderer.forType<LocalDate>(FormSize(approximateWidth = 11.0, approximateHeight = 1.0)) { prop -> text { ::content { prop().renderToString() } } }
        ViewRenderer.forType<LocalTime>(FormSize(approximateWidth = 5.0, approximateHeight = 1.0)) { prop -> text { ::content { prop().renderToString() } } }

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

        ViewRenderer += JsonRenderer
        FormRenderer += JsonRenderer

        ViewRenderer += ServerFileRenderer
        FormRenderer += ServerFileRenderer

        ViewRenderer += TableRenderer
        FormRenderer += TableRenderer

        ViewRenderer += ForeignKeyRenderer
        FormRenderer += ForeignKeyRenderer
    }
}

object JsonRenderer : ViewRenderer.Generator, FormRenderer.Generator {
    val json = Json(DefaultJson) { prettyPrint = true }
    override val name: String = "JSON"
    override val basePriority: Float
        get() = 0.1f

    override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> {
        return ViewRenderer(this, selector) { _, it ->
            ThemeDerivation {
                it.copy(font = it.font.copy(font = systemDefaultFixedWidthFont)).withoutBack
            }.onNext - text {
                ::content {
                    json.encodeToString(selector.serializer, it())
                }
            }
        }
    }

    override fun <T> form(selector: FormSelector<T>): FormRenderer<T> {
        return FormRenderer(this, selector) { _, it ->
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
