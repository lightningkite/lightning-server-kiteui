package com.lightningkite.kiteui.forms

import com.lightningkite.*
import com.lightningkite.Temperature.Companion.celsius
import com.lightningkite.Temperature.Companion.fahrenheit
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.readable.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.canvas.TextAlign
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.serialization.SerializableAnnotationValue
import com.lightningkite.serialization.UUIDSerializer
import kotlinx.datetime.*
import kotlin.time.Duration

fun FormModule.defaults() {
    viewForTypeWithField<Boolean>(FormSize.Inline) { field, it ->
        text {
            ::content {
                if (it()) "✓ ${field?.descriptionOrDisplayName ?: ""}"
                else "✘ ${field?.descriptionOrDisplayName ?: ""}"
            }
        }
    }
    viewForTypeWithField<Boolean?>(FormSize.Inline) { field, it ->
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
    formForTypeWithField<Boolean>(FormSize.Inline) { field, it ->
        row {
            centered - checkbox {
                checked bind it
            }
            field?.descriptionOrDisplayName?.let {
                centered - text(it)
            }
        }
    }
    formForType<Boolean?>(FormSize.Inline) { it ->
        select {
            bind(it, Constant(listOf(null, true, false))) {
                when (it) {
                    true -> "Yes"
                    false -> "No"
                    null -> "N/A"
                }
            }
        }
    }
    formForType<Byte?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt()?.toByte() })
        }
    }
    formForType<Short?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt()?.toShort() })
        }
    }
    formForType<Int?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt() })
        }
    }
    formForType<Long?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toLong() })
        }
    }
    formForType<Byte>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End
            content bind it.lens(
                get = { it.toDouble() },
                modify = { o, it -> it?.toInt()?.toByte() ?: o })
        }
    }
    formForType<Short>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End
            content bind it.lens(
                get = { it.toDouble() },
                modify = { o, it -> it?.toInt()?.toShort() ?: o })
        }
    }
    formForType<Int>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toInt() ?: o })
        }
    }
    formForType<Long>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toLong() ?: o })
        }
    }
    formForType<Byte?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toByteOrNull(16) })
        }
    }
    formForType<Short?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toShortOrNull(16) })
        }
    }
    formForType<Int?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toIntOrNull(16) })
        }
    }
    formForType<Long?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toLongOrNull(16) })
        }
    }
    formForType<Byte>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toByteOrNull(16) ?: o })
        }
    }
    formForType<Short>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toShortOrNull(16) ?: o })
        }
    }
    formForType<Int>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toIntOrNull(16) ?: o })
        }
    }
    formForType<Long>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toLongOrNull(16) ?: o })
        }
    }
    formForType<Byte?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toByteOrNull(2) })
        }
    }
    formForType<Short?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toShortOrNull(2) })
        }
    }
    formForType<Int?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toIntOrNull(2) })
        }
    }
    formForType<Long?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toLongOrNull(2) })
        }
    }
    formForType<Byte>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toByteOrNull(2) ?: o })
        }
    }
    formForType<Short>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toShortOrNull(2) ?: o })
        }
    }
    formForType<Int>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toIntOrNull(2) ?: o })
        }
    }
    formForType<Long>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme - textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toLongOrNull(2) ?: o })
        }
    }
    formForType<Float?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toFloat() })
        }
    }
    formForType<Double?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it
        }
    }
    formForType<Float>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toFloat() ?: o })
        }
    }
    formForType<Double>(FormSize.Inline, name = "Number") { it ->
        fieldTheme - numberInput {
            align = Align.End; content bind it.nullable()
        }
    }
    formForType<Char>(
        FormSize.Inline,
        name = "Character"
    ) { it ->
        fieldTheme - textInput {
            content bind it.lens(
                get = { it.toString() },
                modify = { o, it -> it.firstOrNull() ?: o })
        }
    }
    formForType<Char?>(
        FormSize.Inline,
        name = "Character"
    ) { it ->
        fieldTheme - textInput {
            content bind it.lens(
                get = { it.toString() },
                modify = { o, it -> it.firstOrNull() })
        }
    }
    formForType<String>(
        size = { selector ->
            val maxLengthAnno =
                selector.annotations.find { it.fqn == "com.lightningkite.lightningdb.MaxLength" }?.values
            val maxSize =
                (maxLengthAnno?.get("size") as? SerializableAnnotationValue.IntValue)?.value?.takeUnless { it == -1 }
            val averageSize =
                (maxLengthAnno?.get("average") as? SerializableAnnotationValue.IntValue)?.value?.takeUnless { it == -1 }
                    ?.toDouble()
                    ?: maxSize?.div(8.0)
                    ?: 20.0

            FormSize(
                approximateWidth = averageSize,
                approximateHeight = 1.0
            )
        },
        name = "Text",
        generate = { it -> fieldTheme - textInput { content bind it } }
    )
    viewForType<String>(
        size = { selector ->
            val maxLengthAnno =
                selector.annotations.find { it.fqn == "com.lightningkite.lightningdb.MaxLength" }?.values
            val maxSize =
                (maxLengthAnno?.get("size") as? SerializableAnnotationValue.IntValue)?.value?.takeUnless { it == -1 }
            val averageSize =
                (maxLengthAnno?.get("average") as? SerializableAnnotationValue.IntValue)?.value?.takeUnless { it == -1 }
                    ?.toDouble()
                    ?: maxSize?.div(8.0)
                    ?: 20.0

            FormSize(
                approximateWidth = averageSize,
                approximateHeight = 1.0
            )
        },
        name = "Text",
        generate = { it -> text { ::content { it() } } }
    )
    formForType<String>(
        size = FormSize(40.0, 10.0),
        name = "Large Text",
        annotation = "com.lightningkite.lightningdb.Multiline",
        priority = 2f,
        generate = { it -> sizeConstraints(minHeight = 10.rem) - fieldTheme - textArea { content bind it } }
    )
    viewForType<String>(
        size = FormSize(40.0, 3.0),
        name = "Large Text Summary",
        annotation = "com.lightningkite.lightningdb.Multiline",
        priority = 0.8f,
        generate = { it ->
            sizeConstraints(maxHeight = 3.rem) - text {
                ::content { it().substringBefore('\n') }
                wraps = false
                ellipsis = true
            }
        }
    )
    viewForType<String>(
        size = FormSize(40.0, 10.0),
        name = "Large Text",
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
    formForType<EmailAddress>(
        size = FormSize(20.0, 1.0),
        name = "Email Address",
        priority = 1f,
        generate = { it ->
            col {
                fieldTheme - textInput {
                    content bind it.lens(
                        get = { it.raw },
                        set = { it.toEmailAddress() }
                    )
                }
                errorText()
            }
        }
    )
    viewForType<EmailAddress>(
        size = FormSize(20.0, 1.0),
        name = "Email Address",
        priority = 1f,
        generate = { it ->
            externalLink {
                text {
                    ::content { it().raw }
                    wraps = false
                    ellipsis = true
                }
                ::to { it().url }
            }
        }
    )
    formForType<PhoneNumber>(
        size = FormSize(20.0, 1.0),
        name = "Phone Number",
        priority = 1f,
        generate = { it ->
            col {
                fieldTheme - textInput {
                    content bind it.lens(
                        get = { it.raw },
                        set = { it.toPhoneNumber() }
                    )
                }
                errorText()
            }
        }
    )
    viewForType<PhoneNumber>(
        size = FormSize(20.0, 1.0),
        name = "Phone Number",
        priority = 1f,
        generate = { it ->
            externalLink {
                text {
                    ::content { it().raw }
                    wraps = false
                    ellipsis = true
                }
                ::to { it().url }
            }
        }
    )
    formForType<TimeZone>(
        size = FormSize(20.0, 1.0),
        name = "Time Zone",
        priority = 1f,
        generate = { it ->
            fieldTheme - select {
                bind(it, Constant(TimeZone.availableZoneIds.map { TimeZone.of(it) }), { it.id })
            }
        }
    )
    formForType<TimeZone?>(
        size = FormSize(20.0, 1.0),
        name = "Time Zone",
        priority = 1f,
        generate = { it ->
            fieldTheme - select {
                bind(it, Constant(listOf(null) + TimeZone.availableZoneIds.map { TimeZone.of(it) }), { it?.id ?: "N/A" })
            }
        }
    )
    viewForType<GeoCoordinate>(
        size = FormSize(20.0, 1.0),
        name = "Geocoordinate - Direct Entry",
        priority = 1f,
        generate = { it ->
            text { ::content { "${it().latitude}, ${it().longitude}" } }
        }
    )
    formForType<GeoCoordinate>(
        size = FormSize(20.0, 1.0),
        name = "Geocoordinate - Direct Entry",
        priority = 1f,
        generate = { it ->
            row {
                expanding - fieldTheme - numberInput {
                    hint = "Latitude"
                    content bind it.lens(get = { it.latitude }, modify = { o, it -> o.copy(latitude = it ?: 0.0) })
                }
                expanding - fieldTheme - numberInput {
                    hint = "Longitude"
                    content bind it.lens(get = { it.longitude }, modify = { o, it -> o.copy(longitude = it ?: 0.0) })
                }
            }
        }
    )
    formForType<UUID>(FormSize(24.0, 1.0), UUIDSerializer) {
        fieldTheme - row {
            expanding - textInput {
                content bind it.lens(get = { it.toString() }, modify = { o, it ->
                    try {
                        UUID.parse(it)
                    } catch (e: Exception) {
                        o
                    }
                })
            }
            button {
                icon(Icon.sync, "Regenerate")
                onClick { it set UUID.random() }
            }
        }
    }
    formForType<UUID>(FormSize(24.0, 1.0)) {
        fieldTheme - row {
            expanding - textInput {
                content bind it.lens(get = { it.toString() }, modify = { o, it ->
                    try {
                        UUID.parse(it)
                    } catch (e: Exception) {
                        o
                    }
                })
            }
            button {
                icon(Icon.sync, "Regenerate")
                onClick { it set UUID.random() }
            }
        }
    }
    viewForType<UUID>(FormSize(24.0, 1.0), UUIDSerializer) {
        text { ::content { it().toString() } }
    }
    viewForType<UUID>(FormSize(24.0, 1.0)) {
        text { ::content { it().toString() } }
    }

    formForType<Map<Unit, Unit>>(FormSize.Inline) { it -> text("TODO") }
    formForType<Instant>(FormSize(approximateWidth = 17.0, approximateHeight = 1.0)) { prop ->
        fieldTheme - localDateTimeField {
            content bind prop.lens(
                get = { it.toLocalDateTime(TimeZone.currentSystemDefault()) },
                modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) ?: old },
            )
        }
    }
    formForType<Instant?>(FormSize(approximateWidth = 17.0, approximateHeight = 1.0)) { prop ->
        fieldTheme - localDateTimeField {
            content bind prop.lens(
                get = { it?.toLocalDateTime(TimeZone.currentSystemDefault()) },
                modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) },
            )
        }
    }
    formForType<LocalDateTime>(FormSize(approximateWidth = 17.0, approximateHeight = 1.0)) { prop ->
        fieldTheme - localDateTimeField {
            content bind prop.lens(
                get = { it },
                modify = { old, it -> it ?: old })
        }
    }
    formForType<LocalDateTime?>(
        FormSize(
            approximateWidth = 17.0,
            approximateHeight = 1.0
        )
    ) { prop -> fieldTheme - localDateTimeField { content bind prop } }
    formForType<LocalDate>(FormSize(approximateWidth = 11.0, approximateHeight = 1.0)) { prop ->
        fieldTheme - localDateField {
            content bind prop.lens(
                get = { it },
                modify = { old, it -> it ?: old })
        }
    }
    formForType<LocalDate?>(
        FormSize(
            approximateWidth = 12.0,
            approximateHeight = 1.0
        )
    ) { prop -> fieldTheme - localDateField { content bind prop } }
    formForType<LocalTime>(FormSize(approximateWidth = 5.0, approximateHeight = 1.0)) { prop ->
        fieldTheme - localTimeField {
            content bind prop.lens(
                get = { it },
                modify = { old, it -> it ?: old })
        }
    }
    formForType<LocalTime?>(
        FormSize(
            approximateWidth = 5.0,
            approximateHeight = 1.0
        )
    ) { prop -> fieldTheme - localTimeField { content bind prop } }

    viewForType<Instant>(
        FormSize(
            approximateWidth = 17.0,
            approximateHeight = 1.0
        )
    ) { prop -> text { ::content { prop().renderToString() } } }
    viewForType<LocalDateTime>(
        FormSize(
            approximateWidth = 17.0,
            approximateHeight = 1.0
        )
    ) { prop -> text { ::content { prop().renderToString() } } }
    viewForType<LocalDate>(
        FormSize(
            approximateWidth = 12.0,
            approximateHeight = 1.0
        )
    ) { prop -> text { ::content { prop().renderToString() } } }
    viewForType<LocalTime>(
        FormSize(
            approximateWidth = 5.0,
            approximateHeight = 1.0
        )
    ) { prop -> text { ::content { prop().renderToString() } } }


//    formForType<Duration>(
//        FormSize(
//            approximateWidth = 20.0,
//            approximateHeight = 1.0
//        )
//    ) { prop -> fieldTheme - localTimeField { content bind prop } }
//    viewForType<Duration>(
//        FormSize(
//            approximateWidth = 17.0,
//            approximateHeight = 1.0
//        )
//    ) { prop -> text { ::content { prop().renderToString() } } }

    fun ViewWriter.temperatureInput(writable: Writable<Temperature?>) = row {
        val celsius = Property(false)
        expanding - numberInput {
            align = Align.End
            content bind shared { if (celsius()) writable()?.celsius else writable()?.fahrenheit }.withWrite {
                writable.set(if (celsius()) it?.celsius else it?.fahrenheit)
            }
        }
        select {
            bind(celsius, Constant(listOf(true, false))) { if (it) "°C" else "°F" }
        }
    }
    formForType<Temperature?>(FormSize.Inline, name = "Temperature") { it -> fieldTheme - temperatureInput(it) }
    formForType<Temperature>(
        FormSize.Inline,
        name = "Temperature"
    ) { it -> fieldTheme - temperatureInput(it.nullable()) }

    viewForType<Condition<Int>>(FormSize.Inline, name = "Programmer-y Text") { it ->
        text { ::content { it().toString() } }
    }

    this += HorizontalListRenderer as FormRenderer.Generator
    this += HorizontalListRenderer as ViewRenderer.Generator
    this += VerticalListRenderer as FormRenderer.Generator
    this += VerticalListRenderer as ViewRenderer.Generator
    this += HorizontalSetRenderer as FormRenderer.Generator
    this += HorizontalSetRenderer as ViewRenderer.Generator
    this += VerticalSetRenderer as FormRenderer.Generator
    this += VerticalSetRenderer as ViewRenderer.Generator
    this += PathPartsRenderer
    this += SortPathRenderer as FormRenderer.Generator
    this += SortPathRenderer as ViewRenderer.Generator

    this += ToStringRenderer

    this += EnumFormRenderer as FormRenderer.Generator
    this += EnumFormRenderer as ViewRenderer.Generator

    this += ByFieldRenderer as FormRenderer.Generator
    this += ByFieldRenderer as ViewRenderer.Generator

    this += NullableFormRenderer as FormRenderer.Generator
    this += NullableFormRenderer as ViewRenderer.Generator

    this += ObjectRenderer as FormRenderer.Generator
    this += ObjectRenderer as ViewRenderer.Generator

    this += WrapperViewRenderer
    this += WrapperFormRenderer

    this += MySealedFormRenderer

    this += JsonRenderer as FormRenderer.Generator
    this += JsonRenderer as ViewRenderer.Generator

    this += ServerFileRenderer as FormRenderer.Generator
    this += ServerFileRenderer as ViewRenderer.Generator

    this += TableRenderer as FormRenderer.Generator
    this += TableRenderer as ViewRenderer.Generator

    this += ForeignKeyRenderer as FormRenderer.Generator
    this += ForeignKeyRenderer as ViewRenderer.Generator

    this += InlineFormRenderer as FormRenderer.Generator
    this += InlineFormRenderer as ViewRenderer.Generator
}

