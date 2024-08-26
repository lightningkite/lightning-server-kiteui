package com.lightningkite.kiteui.forms

import com.lightningkite.UUID
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.*
import com.lightningkite.uuid
import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.*
import kotlin.random.Random

abstract class FormRendererForType<T>(val serializer: KSerializer<T>, val alwaysSize: FormSize = FormSize.Large) :
    FormRenderer<T> {
    override fun size(selector: FormSelector<T>): FormSize = alwaysSize
    override fun renderReadOnly(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        readable: Readable<T>
    ): Unit = with(writer) {
        defaultFieldWrapper(field) {
            text { ::content { readable()?.let(::format) ?: "N/A" } }
        }
    }

    open fun format(item: T): String = item.toString()

    init {
        @Suppress("LeakingThis")
        FormRenderer.forSerialName[serializer.descriptor.serialName] = this
    }
}

fun FormRenderer.Companion.builtins() {
    object : FormRendererForType<Boolean>(Boolean.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Boolean>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Boolean>
        ): Unit = with(writer) {
            val prop = writable
            row {
                centered - checkbox {
                    checked bind prop
                }
                centered - text(field?.descriptionOrDisplayName ?: "Check")
            }
        }

        override fun renderReadOnly(
            writer: ViewWriter,
            selector: FormSelector<Boolean>,
            field: SerializableProperty<*, *>?,
            readable: Readable<Boolean>
        ): Unit = with(writer) {
            text {
                ::content {
                    if (readable()) "✓ ${field?.descriptionOrDisplayName ?: ""}"
                    else "✘ ${field?.descriptionOrDisplayName ?: ""}"
                }
            }
        }
    }

    object : FormRendererForType<Boolean?>(Boolean.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Boolean?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Boolean?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - select {
                    bind(prop, Constant(listOf(null, true, false))) {
                        when (it) {
                            true -> "Yes"
                            false -> "No"
                            null -> "N/A"
                        }
                    }
                }
            }
        }

        override fun renderReadOnly(
            writer: ViewWriter,
            selector: FormSelector<Boolean?>,
            field: SerializableProperty<*, *>?,
            readable: Readable<Boolean?>
        ): Unit = with(writer) {
            text {
                ::content {
                    when (readable()) {
                        true -> "✓ ${field?.descriptionOrDisplayName ?: ""}"
                        false -> "✘ ${field?.descriptionOrDisplayName ?: ""}"
                        null -> "${field?.descriptionOrDisplayName ?: ""} N/A"
                    }
                }
            }
        }
    }
    object : FormRendererForType<Byte>(Byte.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Byte>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Byte>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Byte -> it.toDouble() },
                        modify = { old, it: Double? -> it?.toInt()?.toByte() ?: old })
                }
            }
        }
    }

    object : FormRendererForType<Short>(Short.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Short>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Short>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Short -> it.toDouble() },
                        modify = { old, it: Double? -> it?.toInt()?.toShort() ?: old })
                }
            }
        }
    }
    object : FormRendererForType<Int>(Int.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Int>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Int>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Int -> it.toDouble() },
                        modify = { old, it: Double? -> it?.toInt() ?: old })
                }
            }
        }
    }
    object : FormRendererForType<Long>(Long.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Long>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Long>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Long -> it.toDouble() },
                        modify = { old, it: Double? -> it?.toLong() ?: old })
                }
            }
        }
    }
    object : FormRendererForType<Float>(Float.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Float>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Float>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Float -> it.toDouble() },
                        modify = { old, it: Double? -> it?.toFloat() ?: old })
                }
            }
        }
    }
    object : FormRendererForType<Double>(Double.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Double>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Double>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Double -> it },
                        modify = { old, it: Double? -> it ?: old })
                }
            }
        }
    }
    object : FormRendererForType<Byte?>(Byte.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Byte?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Byte?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Byte? -> it?.toDouble() },
                        set = { it: Double? -> it?.toInt()?.toByte() })
                }
            }
        }
    }
    object : FormRendererForType<Short?>(Short.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Short?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Short?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Short? -> it?.toDouble() },
                        set = { it: Double? -> it?.toInt()?.toShort() })
                }
            }
        }
    }
    object : FormRendererForType<Int?>(Int.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Int?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Int?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Int? -> it?.toDouble() },
                        set = { it: Double? -> it?.toInt() })
                }
            }
        }
    }
    object : FormRendererForType<Long?>(Long.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Long?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Long?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Long? -> it?.toDouble() },
                        set = { it: Double? -> it?.toLong() })
                }
            }
        }
    }
    object : FormRendererForType<Float?>(Float.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Float?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Float?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it: Float? -> it?.toDouble() },
                        set = { it: Double? -> it?.toFloat() })
                }
            }
        }
    }
    object : FormRendererForType<Double?>(Double.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Double?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Double?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - numberField {
                    field?.hint?.let { hint = it }
                    content bind prop
                }
            }
        }
    }
    object : FormRendererForType<Char>(Char.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Char>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Char>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - textField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it.toString() },
                        modify = { old, it -> it.firstOrNull() ?: old }
                    )
                }
            }
        }
    }
    object : FormRendererForType<Char?>(Char.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Char?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Char?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - textField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it?.toString() ?: "" },
                        modify = { old, it -> it.firstOrNull() }
                    )
                }
            }
        }
    }
    object : FormRendererForType<String>(String.serializer()) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<String>,
            field: SerializableProperty<*, *>?,
            writable: Writable<String>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                if (selector.annotations.any { it.fqn == "com.lightningkite.lightningdb.Multiline" }) {
                    fieldTheme - textArea {
                        field?.hint?.let { hint = it }
                        content bind prop
                    }
                } else {
                    fieldTheme - textField {
                        field?.hint?.let { hint = it }
                        content bind prop
                    }
                }
            }
        }
    }
    object : FormRendererForType<UUID>(UUIDSerializer) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<UUID>,
            field: SerializableProperty<*, *>?,
            writable: Writable<UUID>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - textField {
                    field?.hint?.let { hint = it }
                    content bind prop.lens(
                        get = { it.toString() },
                        modify = { old, current ->
                            try {
                                uuid(current)
                            } catch (e: Exception) {
                                old
                            }
                        }
                    )
                }
            }
        }
    }

    object : FormRendererForType<Instant>(Instant.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Instant>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Instant>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localDateTimeField {
                    content bind prop.lens(
                        get = { it.toLocalDateTime(TimeZone.currentSystemDefault()) },
                        modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) ?: old },
                    )
                }
            }
        }

        override fun format(item: Instant): String = item.renderToString()
    }
    object : FormRendererForType<Instant?>(Instant.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Instant?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Instant?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localDateTimeField {
                    content bind prop.lens(
                        get = { it?.toLocalDateTime(TimeZone.currentSystemDefault()) },
                        modify = { old, it -> it?.toInstant(TimeZone.currentSystemDefault()) },
                    )
                }
            }
        }

        override fun format(item: Instant?): String = item?.renderToString() ?: "N/A"
    }
    object : FormRendererForType<LocalDateTime>(LocalDateTime.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<LocalDateTime>,
            field: SerializableProperty<*, *>?,
            writable: Writable<LocalDateTime>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localDateTimeField {
                    content bind prop.lens(
                        get = { it },
                        modify = { old, it -> it ?: old },
                    )
                }
            }
        }

        override fun format(item: LocalDateTime): String = item.renderToString()
    }

    object : FormRendererForType<LocalDateTime?>(LocalDateTime.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<LocalDateTime?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<LocalDateTime?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localDateTimeField {
                    content bind prop
                }
            }
        }

        override fun format(item: LocalDateTime?): String = item?.renderToString() ?: "N/A"
    }

    object : FormRendererForType<LocalDate>(LocalDate.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<LocalDate>,
            field: SerializableProperty<*, *>?,
            writable: Writable<LocalDate>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localDateField {
                    content bind prop.lens(
                        get = { it },
                        modify = { old, it -> it ?: old },
                    )
                }
            }
        }

        override fun format(item: LocalDate): String = item.renderToString()
    }

    object : FormRendererForType<LocalDate?>(LocalDate.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<LocalDate?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<LocalDate?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localDateField {
                    content bind prop
                }
            }
        }

        override fun format(item: LocalDate?): String = item?.renderToString() ?: "N/A"
    }

    object : FormRendererForType<LocalTime>(LocalTime.serializer(), alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<LocalTime>,
            field: SerializableProperty<*, *>?,
            writable: Writable<LocalTime>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localTimeField {
                    content bind prop.lens(
                        get = { it },
                        modify = { old, it -> it ?: old },
                    )
                }
            }
        }

        override fun format(item: LocalTime): String = item.renderToString()
    }

    object : FormRendererForType<LocalTime?>(LocalTime.serializer().nullable, alwaysSize = FormSize.Small) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<LocalTime?>,
            field: SerializableProperty<*, *>?,
            writable: Writable<LocalTime?>
        ): Unit = with(writer) {
            val prop = writable
            defaultFieldWrapper(field) {
                fieldTheme - localTimeField {
                    content bind prop
                }
            }
        }

        override fun format(item: LocalTime?): String = item?.renderToString() ?: "N/A"
    }
    object : FormRendererForType<List<Any?>>(ListSerializer(GenericPlaceholderSerializer)) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<List<Any?>>,
            field: SerializableProperty<*, *>?,
            writable: Writable<List<Any?>>
        ): Unit = with(writer) {
            val prop = writable
            val inner = selector.serializer.tryTypeParameterSerializers3() ?: arrayOf()
            defaultFieldWrapper(field) {
                card - col {
                    col {
                        forEachUpdating(prop.lensByElementAssumingSetNeverManipulates()) {
                            row {
                                @Suppress("UNCHECKED_CAST")
                                expanding - form(
                                    inner[0] as KSerializer<Any?>,
                                    it.flatten(),
                                    selector.annotations,
                                    null
                                )
                                centered - button {
                                    icon(Icon.deleteForever, "Delete")
                                    onClick {
                                        prop set prop().toMutableList().apply {
                                            removeAt(it().index.value)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    button {
                        centered - row {
                            centered - icon(Icon.add, "")
                            centered - text("Add Entry")
                        }
                        onClick {
                            prop set prop() + inner[0].default()
                        }
                    }
                }
            }
        }
    }
    object : FormRendererForType<Set<Any?>>(SetSerializer(GenericPlaceholderSerializer)) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Set<Any?>>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Set<Any?>>
        ): Unit = with(writer) {
            val prop = writable
            val inner = selector.serializer.tryTypeParameterSerializers3() ?: arrayOf()
            defaultFieldWrapper(field) {
                card - col {
                    col {
                        forEachUpdating(prop.lens(
                            get = { it.toList() },
                            set = { it.toSet() }
                        ).lensByElementAssumingSetNeverManipulates()) {
                            row {
                                @Suppress("UNCHECKED_CAST")
                                expanding - form(
                                    inner[0] as KSerializer<Any?>,
                                    it.flatten(),
                                    selector.annotations,
                                    null
                                )
                                centered - button {
                                    icon(Icon.deleteForever, "Delete")
                                    onClick { prop set prop().minus(it.state.getOrNull()) }
                                }
                            }
                        }
                    }
                    button {
                        centered - row {
                            centered - icon(Icon.add, "")
                            centered - text("Add Entry")
                        }
                        onClick {
                            prop set prop() + inner[0].default()
                        }
                    }
                }
            }
        }
    }
    object : FormRendererForType<Map<Any?, Any?>>(
        MapSerializer(
            GenericPlaceholderSerializer,
            GenericPlaceholderSerializer
        )
    ) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<Map<Any?, Any?>>,
            field: SerializableProperty<*, *>?,
            writable: Writable<Map<Any?, Any?>>
        ): Unit = with(writer) {
            val prop = writable
            val inner = selector.serializer.tryTypeParameterSerializers3() ?: arrayOf()
            defaultFieldWrapper(field) {
                text("Sorry, we haven't made a map editor yet.")
            }
        }
    }
    object : FormRendererForType<DataClassPathPartial<Any?>>(
        DataClassPathSerializer(GenericPlaceholderSerializer)
    ) {
        override fun render(
            writer: ViewWriter,
            selector: FormSelector<DataClassPathPartial<Any?>>,
            field: SerializableProperty<*, *>?,
            writable: Writable<DataClassPathPartial<Any?>>
        ): Unit = with(writer) {
            val serializer = selector.serializer as DataClassPathSerializer<Any?>
            defaultFieldWrapper(field) {
                val properties = writable.lens(
                    get = { it.properties },
                    set = {
                        var out: DataClassPath<Any?, Any?> = DataClassPathSelf(serializer.inner)
                        var lastNullable = false
                        for (prop in it) {
                            if (lastNullable) {
                                @Suppress("UNCHECKED_CAST")
                                out = DataClassPathAccess<Any?, Any, Any?>(
                                    DataClassPathNotNull(out),
                                    prop as SerializableProperty<Any, Any?>
                                )
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                out = DataClassPathAccess<Any?, Any, Any?>(
                                    out as DataClassPath<Any?, Any>,
                                    prop as SerializableProperty<Any, Any?>
                                )
                            }
                        }
                        out
                    }
                )
                col {
//                    text { ::content { properties().joinToString(", ") { it.name} }}
                    row {
                        forEachUpdating(properties.lensByElementAssumingSetNeverManipulates().lens {
                            it + object : ListItemWritable<SerializableProperty<*, *>?> {
                                override val index: ImmediateReadable<Int> get() = Constant(it.size)
                                override val value: SerializableProperty<*, *>? = null
                                override fun addListener(listener: () -> Unit): () -> Unit = {}
                                override suspend fun set(value: SerializableProperty<*, *>?) {
                                    if (value != null) {
                                        properties.set(properties() + value)
                                    }
                                }
                            }
                        }) {
                            select {
                                val options = shared {
                                    (properties().getOrNull(it().index() - 1)
                                        ?.let { it.serializer.let { it.nullElement() ?: it }.serializableProperties ?: arrayOf() }
                                        ?: serializer.inner.serializableProperties ?: arrayOf()).toList().let {
                                        listOf(null) + it
                                    }
                                }
                                ::opacity {
                                    if(options().size == 1) 0.0 else 1.0
                                }
                                bind(
                                    edits = it.flatten().withWrite { value ->
                                        properties.set(properties().take(it().index()) + (value?.let { listOf(it) }
                                            ?: listOf()))
                                    },
                                    data = options,
                                    render = { it?.displayName ?: "+" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}