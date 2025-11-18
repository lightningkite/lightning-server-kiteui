/**
 * Built-in renderer registrations for common types.
 *
 * This file contains the default renderer implementations for:
 * - Primitives (Boolean, Int, Long, Float, Double, Char, String, etc.)
 * - Date/time types (Instant, LocalDateTime, LocalDate, LocalTime)
 * - Special types (UUID, EmailAddress, PhoneNumber, GeoCoordinate, TimeZone, Temperature)
 * - Collections (List, Set via dedicated renderer objects)
 * - Complex types (Enums, Objects, Nullables, Sealed classes, etc.)
 *
 * ## Usage
 * Call [FormModule.defaults] to register all built-in renderers:
 * ```kotlin
 * val formModule = FormModule().apply { defaults() }
 * ```
 *
 * ## Renderer Variants
 * Many types have multiple renderers with different priorities:
 * - Numbers: Decimal (priority 1.0), Hexadecimal (0.9), Binary (0.8)
 * - Strings: Normal (calculated size), Large Text for @Multiline (priority 2.0)
 *
 * The priority system ensures the most appropriate renderer is selected based on
 * annotations and size constraints.
 *
 * ## Registration Order
 * Renderers are registered in roughly this order:
 * 1. Primitive types (Boolean, numbers, Char, String)
 * 2. Date/time types (Instant, LocalDateTime, LocalDate, LocalTime)
 * 3. Special types (UUID, Email, Phone, Geo, TimeZone, Temperature)
 * 4. Collections (List, Set with horizontal/vertical variants)
 * 5. Complex type handlers (Enum, Object, Nullable, Sealed, etc.)
 */
package com.lightningkite.kiteui.forms

import com.lightningkite.*
import com.lightningkite.Temperature.Companion.celsius
import com.lightningkite.Temperature.Companion.fahrenheit
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.services.database.Condition
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.nullable
import com.lightningkite.reactive.extensions.withWrite
import com.lightningkite.services.database.SerializableAnnotationValue
import kotlinx.serialization.builtins.serializer
import kotlinx.datetime.*
import kotlin.uuid.Uuid

/**
 * Registers all built-in renderers to this [FormModule].
 *
 * This is the primary entry point for setting up the default renderer registry.
 * It registers renderers for all common Kotlin and kotlinx types, plus Lightning Server
 * extensions like EmailAddress, PhoneNumber, etc.
 *
 * ## What Gets Registered
 * - **Primitives**: Boolean (checkbox), numbers (textInput/numberInput), Char, String
 * - **Date/Time**: Instant, LocalDateTime, LocalDate, LocalTime
 * - **Special Types**: UUID, EmailAddress, PhoneNumber, GeoCoordinate, TimeZone, Temperature
 * - **Collections**: List, Set (horizontal/vertical renderers)
 * - **Complex Types**: Enums, Objects, Nullables, Sealed classes, JSON, ServerFile, Tables, Foreign keys
 *
 * ## Typical Usage
 * ```kotlin
 * val formModule = FormModule().apply {
 *     defaults() // Register all built-in renderers
 *     // Add custom renderers here
 * }
 * ```
 *
 * ## Extension Point
 * After calling defaults(), you can:
 * - Override specific renderers by registering higher-priority generators
 * - Add renderers for custom types
 * - Disable renderers by filtering FormModule.generators
 */
fun FormModule.defaults() {
    // ===== Boolean Renderers =====
    // View renderer: Shows checkmark/X with optional field label
    viewForTypeWithField<Boolean>(FormSize.Inline) { field, it ->
        text {
            ::content {
                if (it()) "✓ ${field?.descriptionOrDisplayName ?: ""}"
                else "✘ ${field?.descriptionOrDisplayName ?: ""}"
            }
        }
    }
    // Nullable view renderer: Shows checkmark/X/dash for null
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
    // Form renderer: Standard checkbox with optional label
    formForTypeWithField<Boolean>(FormSize.Inline) { field, it ->
        row {
            centered.checkbox {
                checked bind it
            }
            field?.descriptionOrDisplayName?.let {
                centered.text(it)
            }
        }
    }
    // Nullable form renderer: Dropdown with Yes/No/N/A options
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

    // ===== Number Renderers =====
    // Multiple variants per type: Decimal (priority 1.0), Hexadecimal (0.9), Binary (0.8)
    // Lower priority variants allow users to choose alternative representations

    // Nullable number renderers (Byte, Short, Int, Long) - Decimal representation
    formForType<Byte?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt()?.toByte() })
        }
    }
    formForType<Short?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt()?.toShort() })
        }
    }
    formForType<Int?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toInt() })
        }
    }
    formForType<Long?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toLong() })
        }
    }

    // Non-nullable number renderers (Byte, Short, Int, Long) - Decimal representation
    // These use lens.modify to preserve old value if input is invalid
    formForType<Byte>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End
            content bind it.lens(
                get = { it.toDouble() },
                modify = { o, it -> it?.toInt()?.toByte() ?: o })
        }
    }
    formForType<Short>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End
            content bind it.lens(
                get = { it.toDouble() },
                modify = { o, it -> it?.toInt()?.toShort() ?: o })
        }
    }
    formForType<Int>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toInt() ?: o })
        }
    }
    formForType<Long>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toLong() ?: o })
        }
    }

    // Hexadecimal number renderers - Lower priority (0.9) than decimal
    // Useful for debugging, memory addresses, color codes, etc.
    formForType<Byte?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toByteOrNull(16) })
        }
    }
    formForType<Short?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toShortOrNull(16) })
        }
    }
    formForType<Int?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toIntOrNull(16) })
        }
    }
    formForType<Long?>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(16) ?: "" },
                set = { it?.toLongOrNull(16) })
        }
    }
    formForType<Byte>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toByteOrNull(16) ?: o })
        }
    }
    formForType<Short>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toShortOrNull(16) ?: o })
        }
    }
    formForType<Int>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toIntOrNull(16) ?: o })
        }
    }
    formForType<Long>(FormSize.Inline, name = "Hexadecimal", priority = 0.9f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(16) },
                modify = { o, it -> it?.toLongOrNull(16) ?: o })
        }
    }

    // Binary number renderers - Lowest priority (0.8)
    // Useful for bit manipulation, flags, and low-level programming
    formForType<Byte?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toByteOrNull(2) })
        }
    }
    formForType<Short?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toShortOrNull(2) })
        }
    }
    formForType<Int?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toIntOrNull(2) })
        }
    }
    formForType<Long?>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it?.toString(2) ?: "" },
                set = { it?.toLongOrNull(2) })
        }
    }
    formForType<Byte>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toByteOrNull(2) ?: o })
        }
    }
    formForType<Short>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toShortOrNull(2) ?: o })
        }
    }
    formForType<Int>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toIntOrNull(2) ?: o })
        }
    }
    formForType<Long>(FormSize.Inline, name = "Binary", priority = 0.8f) { it ->
        fieldTheme.textInput {
            align = Align.End
            content bind it.lens(
                get = { it.toString(2) },
                modify = { o, it -> it?.toLongOrNull(2) ?: o })
        }
    }

    // Floating-point number renderers (Float, Double)
    // Only decimal representation provided (no hex/binary)
    formForType<Float?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it?.toDouble() }, set = { it?.toFloat() })
        }
    }
    formForType<Double?>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it
        }
    }
    formForType<Float>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.lens(get = { it.toDouble() }, modify = { o, it -> it?.toFloat() ?: o })
        }
    }
    formForType<Double>(FormSize.Inline, name = "Number") { it ->
        fieldTheme.numberInput {
            align = Align.End; content bind it.nullable()
        }
    }

    // ===== Character Renderers =====
    // Uses textInput with lens that takes only the first character
    formForType<Char>(
        FormSize.Inline,
        name = "Character"
    ) { it ->
        fieldTheme.textInput {
            content bind it.lens(
                get = { it.toString() },
                modify = { o, it -> it.firstOrNull() ?: o })
        }
    }
    formForType<Char?>(
        FormSize.Inline,
        name = "Character"
    ) { it ->
        fieldTheme.textInput {
            content bind it.lens(
                get = { it.toString() },
                modify = { o, it -> it.firstOrNull() })
        }
    }

    // ===== String Renderers =====
    // String renderer with dynamic sizing based on @MaxLength annotation
    // The size calculation helps the layout system choose appropriate renderers for constrained spaces
    formForType<String>(
        size = { selector ->
            val maxLengthAnno =
                selector.annotations.find { it.fqn == "com.lightningkite.lightningdb.MaxLength" }?.values
            val maxSize =
                (maxLengthAnno?.get("size") as? SerializableAnnotationValue.IntValue)?.value?.takeUnless { it == -1 }
            // Average size is either explicit or 1/8 of max (or default to 20 chars)
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
        generate = { it -> fieldTheme.textInput { content bind it } }
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

    // Multiline string renderers - higher priority (2.0) when @Multiline annotation is present
    // Form uses textarea with minimum height for editing larger text blocks
    formForType<String>(
        size = FormSize(40.0, 10.0),
        name = "Large Text",
        annotation = "com.lightningkite.lightningdb.Multiline",
        priority = 2f, // Higher priority ensures this is selected for @Multiline fields
        generate = { it -> sizeConstraints(minHeight = 10.rem).fieldTheme.textArea { content bind it } }
    )
    // View renderers for @Multiline: compact summary (priority 0.8) and full text (priority 0.8)
    // Both show only first line to save space in tables/lists
    viewForType<String>(
        size = FormSize(40.0, 3.0),
        name = "Large Text Summary",
        annotation = "com.lightningkite.lightningdb.Multiline",
        priority = 0.8f,
        generate = { it ->
            sizeConstraints(maxHeight = 3.rem).text {
                ::content { it().substringBefore('\n') } // Show only first line
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
                ::content { it().substringBefore('\n') } // Only show first line to save space
                wraps = false
                ellipsis = true
            }
        }
    )

    // ===== Special Types =====

    // EmailAddress - validates email format and displays as clickable mailto: link in views
    formForType<EmailAddress>(
        size = FormSize(20.0, 1.0),
        name = "Email Address",
        priority = 1f,
        generate = { it ->
            col {
                fieldTheme.textInput {
                    // Lens converts between String (UI) and EmailAddress (model)
                    content bind it.lens(
                        get = { it.raw },
                        set = { it.toEmailAddress() }
                    )
                }
                errorText() // Shows validation errors if email is invalid
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
                ::to { it().url } // Generates mailto: link
            }
        }
    )

    // PhoneNumber - validates phone format and displays as clickable tel: link in views
    formForType<PhoneNumber>(
        size = FormSize(20.0, 1.0),
        name = "Phone Number",
        priority = 1f,
        generate = { it ->
            col {
                fieldTheme.textInput {
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
                ::to { it().url } // Generates tel: link
            }
        }
    )

    // TimeZone - dropdown with all available time zones from kotlinx.datetime
    formForType<TimeZone>(
        size = FormSize(20.0, 1.0),
        name = "Time Zone",
        priority = 1f,
        generate = { it ->
            fieldTheme.select {
                bind(it, Constant(TimeZone.availableZoneIds.map { TimeZone.of(it) }), { it.id })
            }
        }
    )
    formForType<TimeZone?>(
        size = FormSize(20.0, 1.0),
        name = "Time Zone",
        priority = 1f,
        generate = { it ->
            fieldTheme.select {
                bind(it, Constant(listOf(null) + TimeZone.availableZoneIds.map { TimeZone.of(it) }), { it?.id ?: "N/A" })
            }
        }
    )

    // GeoCoordinate - displays lat/lng coordinates
    viewForType<GeoCoordinate>(
        size = FormSize(20.0, 1.0),
        name = "Geocoordinate - Direct Entry",
        priority = 1f,
        generate = { it ->
            text { ::content { "${it().latitude}, ${it().longitude}" } } // Simple lat, lng display
        }
    )

    // GeoCoordinate form - two number inputs for latitude and longitude
    formForType<GeoCoordinate>(
        size = FormSize(20.0, 1.0),
        name = "Geocoordinate - Direct Entry",
        priority = 1f,
        generate = { it ->
            row {
                expanding.fieldTheme.numberInput {
                    hint = "Latitude"
                    // TODO: Add validation - latitude should be constrained to [-90, 90]
                    content bind it.lens(get = { it.latitude }, modify = { o, it -> o.copy(latitude = it ?: 0.0) })
                }
                expanding.fieldTheme.numberInput {
                    hint = "Longitude"
                    // TODO: Add validation - longitude should be constrained to [-180, 180]
                    content bind it.lens(get = { it.longitude }, modify = { o, it -> o.copy(longitude = it ?: 0.0) })
                }
            }
        }
    )

    // ===== UUID Renderers =====
    // TODO: Duplicate UUID registrations - one with explicit serializer, one without
    // These appear identical. Likely one is unnecessary or the distinction should be documented.
    // The explicit serializer version may be for handling UUID subtypes or custom serialization.
    formForType<Uuid>(FormSize(24.0, 1.0), Uuid.serializer()) {
        fieldTheme.row {
            expanding.textInput {
                content bind it.lens(get = { it.toString() }, modify = { o, it ->
                    try {
                        Uuid.parse(it)
                    } catch (e: Exception) {
                        o
                    }
                })
            }
            button {
                icon(Icon.sync, "Regenerate")
                onClick { it set Uuid.random() }
            }
        }
    }
    formForType<Uuid>(FormSize(24.0, 1.0)) {
        fieldTheme.row {
            expanding.textInput {
                content bind it.lens(get = { it.toString() }, modify = { o, it ->
                    try {
                        Uuid.parse(it)
                    } catch (e: Exception) {
                        o
                    }
                })
            }
            button {
                icon(Icon.sync, "Regenerate")
                onClick { it set Uuid.random() }
            }
        }
    }
    viewForType<Uuid>(FormSize(24.0, 1.0), Uuid.serializer()) {
        text { ::content { it().toString() } }
    }
    viewForType<Uuid>(FormSize(24.0, 1.0)) {
        text { ::content { it().toString() } }
    }

    // ===== Date/Time Renderers =====

    // Map renderer placeholder - not yet implemented
    // TODO: Implement proper Map<K, V> renderer or remove this registration
    formForType<Map<Unit, Unit>>(FormSize.Inline) { it -> text("TODO") }

    // Instant - stored as UTC, displayed/edited in system timezone
    formForType<Instant>(FormSize(approximateWidth = 17.0, approximateHeight = 1.0)) { prop ->
        fieldTheme.localDateTimeField {
            content bind prop.lens(
                get = { it.toLocalDateTime(TimeZone.currentSystemDefault()) },
                modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) ?: old },
            )
        }
    }
    formForType<Instant?>(FormSize(approximateWidth = 17.0, approximateHeight = 1.0)) { prop ->
        fieldTheme.localDateTimeField {
            content bind prop.lens(
                get = { it?.toLocalDateTime(TimeZone.currentSystemDefault()) },
                modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) },
            )
        }
    }

    // LocalDateTime - no timezone conversion needed
    formForType<LocalDateTime>(FormSize(approximateWidth = 17.0, approximateHeight = 1.0)) { prop ->
        fieldTheme.localDateTimeField {
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
    ) { prop -> fieldTheme.localDateTimeField { content bind prop } }

    // LocalDate - date without time component
    formForType<LocalDate>(FormSize(approximateWidth = 11.0, approximateHeight = 1.0)) { prop ->
        fieldTheme.localDateField {
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
    ) { prop -> fieldTheme.localDateField { content bind prop } }

    // LocalTime - time without date component
    formForType<LocalTime>(FormSize(approximateWidth = 5.0, approximateHeight = 1.0)) { prop ->
        fieldTheme.localTimeField {
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
    ) { prop -> fieldTheme.localTimeField { content bind prop } }

    // View renderers for date/time types - use locale-specific rendering
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

    // ===== Temperature Renderer =====

//    // TODO: Duration support is commented out - either implement or remove
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

    /**
     * Temperature input with unit selection (Celsius/Fahrenheit).
     *
     * Uses a clever lens pattern to convert between units dynamically based on the selected unit.
     * The value is always stored internally as a Temperature object, but displayed in the user's
     * chosen unit. When the unit changes, the numeric value is re-interpreted in the new unit.
     *
     * @param mutable The temperature value to edit (nullable)
     */
    fun ViewWriter.temperatureInput(mutable: MutableReactive<Temperature?>) = row {
        val celsius = Signal(false) // Tracks which unit is currently displayed (false = Fahrenheit)
        expanding.numberInput {
            align = Align.End
            // Convert value to/from selected unit when reading/writing
            // This lens re-interprets the value whenever the unit changes
            content bind remember { if (celsius()) mutable()?.celsius else mutable()?.fahrenheit }.withWrite {
                mutable.set(if (celsius()) it?.celsius else it?.fahrenheit)
            }
        }
        select {
            bind(celsius, Constant(listOf(true, false))) { if (it) "°C" else "°F" }
        }
    }

    formForType<Temperature?>(FormSize.Inline, name = "Temperature") { it -> fieldTheme.temperatureInput(it) }
    formForType<Temperature>(
        FormSize.Inline,
        name = "Temperature"
    ) { it -> fieldTheme.temperatureInput(it.nullable()) }

    // ===== Misc Type Renderers =====

    // Fallback renderer for complex condition types - just shows toString()
    // TODO: This could be improved with a custom condition builder/editor UI
    // Currently not user-friendly for non-developers
    viewForType<Condition<Int>>(FormSize.Inline, name = "Programmer-y Text") { it ->
        text { ::content { it().toString() } }
    }

    // ===== Collection and Complex Type Renderers =====
    // These are object-based generators that handle entire categories of types
    // They implement more sophisticated matching and rendering logic

    // List renderers - horizontal (inline) and vertical (block) variants
    this += HorizontalListRenderer as FormRenderer.Generator
    this += HorizontalListRenderer as ViewRenderer.Generator
    this += VerticalListRenderer as FormRenderer.Generator
    this += VerticalListRenderer as ViewRenderer.Generator

    // Set renderers - similar to List but prevents duplicates
    this += HorizontalSetRenderer as FormRenderer.Generator
    this += HorizontalSetRenderer as ViewRenderer.Generator
    this += VerticalSetRenderer as FormRenderer.Generator
    this += VerticalSetRenderer as ViewRenderer.Generator

    // Specialized renderers for Lightning Server path types (for query building)
    // These allow building type-safe database queries in the admin panel
    this += PathPartsRenderer
    this += SortPathRenderer as FormRenderer.Generator
    this += SortPathRenderer as ViewRenderer.Generator

    // Generic fallback for types that have a meaningful toString()
    // Used as a last resort when no other renderer matches
    this += ToStringRenderer

    // ===== Core Type System Renderers =====

    // Enum renderer - dropdown for single-choice enums
    this += EnumFormRenderer as FormRenderer.Generator
    this += EnumFormRenderer as ViewRenderer.Generator

    // ByField renderer - handles @ByField annotation for indexing into lists/maps
    this += ByFieldRenderer as FormRenderer.Generator
    this += ByFieldRenderer as ViewRenderer.Generator

    // Nullable wrapper - adds "N/A" option and handles null values gracefully
    this += NullableFormRenderer as FormRenderer.Generator
    this += NullableFormRenderer as ViewRenderer.Generator

    // Object renderer - renders data classes by showing all fields vertically
    // This is the main renderer for complex data classes
    this += ObjectRenderer as FormRenderer.Generator
    this += ObjectRenderer as ViewRenderer.Generator

    // Wrapper renderers - handle single-field wrapper classes (@JvmInline, etc.)
    // Unwraps and delegates to the wrapped type's renderer
    this += WrapperViewRenderer
    this += WrapperFormRenderer

    // Sealed class renderer - provides type selection dropdown and renders the selected subtype
    this += MySealedFormRenderer

    // ===== Advanced/Specialized Renderers =====

    // Json renderer - displays raw JSON with syntax highlighting and editing
    this += JsonRenderer as FormRenderer.Generator
    this += JsonRenderer as ViewRenderer.Generator

    // ServerFile - handles file upload/download with progress indicators and preview
    this += ServerFileRenderer as FormRenderer.Generator
    this += ServerFileRenderer as ViewRenderer.Generator

    // Table renderer - displays Map<K, V> as an editable key-value table
    this += TableRenderer as FormRenderer.Generator
    this += TableRenderer as ViewRenderer.Generator

    // ForeignKey renderer - displays related entities with search/select UI
    // Requires FormTypeInfo registration for the referenced type via FormModule.typeInfo
    this += ForeignKeyRenderer as FormRenderer.Generator
    this += ForeignKeyRenderer as ViewRenderer.Generator

    // Inline renderer - alternative to ObjectRenderer that uses compact horizontal layout
    // Better for small objects that fit on one line
    this += InlineFormRenderer as FormRenderer.Generator
    this += InlineFormRenderer as ViewRenderer.Generator
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. **Number Renderer Consolidation**
 *    - The decimal/hex/binary renderers for Byte/Short/Int/Long have a lot of duplication
 *    - Consider extracting a reusable numberRendererWithBase() helper function
 *    - Could parameterize the radix (2, 10, 16) and nullable handling
 *
 * 2. **String Renderer Size Calculation**
 *    - The @MaxLength size calculation is duplicated between form and view renderers
 *    - Extract this logic into a reusable function: calculateStringRendererSize(selector)
 *    - The 1/8 heuristic for average from max size might not be accurate for all use cases
 *
 * 3. **View Renderer Consistency for Multiline**
 *    - Two @Multiline view renderers with same priority (0.8) and similar logic but different sizes
 *    - Consider consolidating or clarifying when each should be used
 *    - The size-based selection might not work as intended with identical priorities
 *
 * 4. **UUID Renderer Duplication**
 *    - There are two identical UUID form renderers registered (lines ~465 and ~482)
 *    - One with Uuid.serializer() and one without - clarify the difference or remove duplication
 *    - Same issue exists for UUID view renderers
 *
 * 5. **Temperature Input Reusability**
 *    - The temperatureInput() helper is defined as a local function
 *    - Consider making it a top-level or extension function for reuse in custom renderers
 *    - The unit conversion logic could be useful elsewhere
 *
 * 6. **Condition<Int> Fallback**
 *    - The "Programmer-y Text" renderer for Condition<Int> is very basic
 *    - Should be enhanced with a proper condition builder/editor UI
 *    - Currently just shows toString() which is not user-friendly
 *
 * 7. **Commented-Out Duration Renderers**
 *    - Duration renderers are commented out (lines ~589-600)
 *    - Either implement properly or remove the commented code
 *    - Consider if kotlin.time.Duration support is needed
 *
 * 8. **Missing Renderer Coverage**
 *    - No renderers for: UByte, UShort, UInt, ULong (unsigned types)
 *    - No renderer for ByteArray (could show hex dump or base64)
 *    - No renderer for URL/URI types (could validate and show as links)
 *
 * 9. **Email/Phone Validation**
 *    - EmailAddress and PhoneNumber renderers use errorText() but don't show what's invalid
 *    - Consider adding inline validation messages with specific error details
 *    - Could show format examples (e.g., "Expected format: user@domain.com")
 *
 * 10. **GeoCoordinate Renderer**
 *     - Only provides direct lat/lng input, no map picker
 *     - Could add a map-based renderer with higher priority for better UX
 *     - Should validate latitude (-90 to 90) and longitude (-180 to 180) ranges
 *
 * 11. **Registration Order**
 *     - The order of renderer registration could affect performance
 *     - Consider grouping by priority or frequency of use
 *     - Document why specific renderers are registered in their current order
 *
 * 12. **Type-Specific Comments**
 *     - Add comments explaining why certain types have multiple renderers
 *     - Document the priority strategy for number renderers (why hex is 0.9, binary is 0.8)
 *     - Explain when InlineFormRenderer vs ObjectRenderer should be preferred
 *
 * 13. **Missing Doc Comments**
 *     - The temperatureInput helper function lacks KDoc
 *     - Consider adding brief comments for each registration block explaining the UI behavior
 *     - Document any gotchas or special behaviors
 *
 * 14. **Platform-Specific Renderers**
 *     - Some renderers might benefit from platform-specific implementations (e.g., native date pickers)
 *     - Consider adding expect/actual for renderers that could use native controls
 *     - Document which renderers are platform-agnostic vs platform-specific
 *
 * 15. **Performance Considerations**
 *     - All renderers are registered eagerly - consider lazy registration for rarely-used types
 *     - The lens conversions allocate lambdas - could be cached/reused
 *     - Profile renderer selection performance with many registered renderers
 */
