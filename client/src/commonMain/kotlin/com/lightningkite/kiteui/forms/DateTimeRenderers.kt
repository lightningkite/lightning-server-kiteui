package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.lensing.lens
import kotlinx.datetime.*
import kotlin.time.Instant

// ===== Instant (UTC timestamp) =====
// by Claude

public object InstantRenderer : Renderer<Instant> {
    override val name: String = "Date & Time"  // by Claude
    override fun form(context: RenderContext<Instant>, value: MutableReactive<Instant>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localDateTimeField {
            content bind value.lens(
                get = { it.toLocalDateTime(TimeZone.currentSystemDefault()) },
                modify = { old, new -> new?.toInstant(TimeZone.currentSystemDefault()) ?: old }
            )
        }
    }

    override fun view(context: RenderContext<Instant>, value: Reactive<Instant>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().renderToString() } }
    }

    override fun cellForm(context: RenderContext<Instant>, value: MutableReactive<Instant>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<Instant>, module: FormModule): Double = 17.0
}

public object NullableInstantRenderer : Renderer<Instant?> {
    override val name: String = "Date & Time"  // by Claude
    override fun form(context: RenderContext<Instant?>, value: MutableReactive<Instant?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localDateTimeField {
            content bind value.lens(
                get = { it?.toLocalDateTime(TimeZone.currentSystemDefault()) },
                set = { it?.toInstant(TimeZone.currentSystemDefault()) }
            )
        }
    }

    override fun view(context: RenderContext<Instant?>, value: Reactive<Instant?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.renderToString() ?: "—" } }
    }

    override fun cellForm(context: RenderContext<Instant?>, value: MutableReactive<Instant?>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<Instant?>, module: FormModule): Double = 17.0
}

// ===== LocalDateTime =====

public object LocalDateTimeRenderer : Renderer<LocalDateTime> {
    override val name: String = "Date & Time"  // by Claude
    override fun form(context: RenderContext<LocalDateTime>, value: MutableReactive<LocalDateTime>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localDateTimeField {
            content bind value.lens(
                get = { it },
                modify = { old, new -> new ?: old }
            )
        }
    }

    override fun view(context: RenderContext<LocalDateTime>, value: Reactive<LocalDateTime>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().renderToString() } }
    }

    override fun cellForm(context: RenderContext<LocalDateTime>, value: MutableReactive<LocalDateTime>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<LocalDateTime>, module: FormModule): Double = 17.0
}

public object NullableLocalDateTimeRenderer : Renderer<LocalDateTime?> {
    override val name: String = "Date & Time"  // by Claude
    override fun form(context: RenderContext<LocalDateTime?>, value: MutableReactive<LocalDateTime?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localDateTimeField { content bind value }
    }

    override fun view(context: RenderContext<LocalDateTime?>, value: Reactive<LocalDateTime?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.renderToString() ?: "—" } }
    }

    override fun cellForm(context: RenderContext<LocalDateTime?>, value: MutableReactive<LocalDateTime?>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<LocalDateTime?>, module: FormModule): Double = 17.0
}

// ===== LocalDate =====

public object LocalDateRenderer : Renderer<LocalDate> {
    override val name: String = "Date"  // by Claude
    override fun form(context: RenderContext<LocalDate>, value: MutableReactive<LocalDate>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localDateField {
            content bind value.lens(
                get = { it },
                modify = { old, new -> new ?: old }
            )
        }
    }

    override fun view(context: RenderContext<LocalDate>, value: Reactive<LocalDate>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().renderToString() } }
    }

    override fun cellForm(context: RenderContext<LocalDate>, value: MutableReactive<LocalDate>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<LocalDate>, module: FormModule): Double = 11.0
}

public object NullableLocalDateRenderer : Renderer<LocalDate?> {
    override val name: String = "Date"  // by Claude
    override fun form(context: RenderContext<LocalDate?>, value: MutableReactive<LocalDate?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localDateField { content bind value }
    }

    override fun view(context: RenderContext<LocalDate?>, value: Reactive<LocalDate?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.renderToString() ?: "—" } }
    }

    override fun cellForm(context: RenderContext<LocalDate?>, value: MutableReactive<LocalDate?>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<LocalDate?>, module: FormModule): Double = 11.0
}

// ===== LocalTime =====

public object LocalTimeRenderer : Renderer<LocalTime> {
    override val name: String = "Time"  // by Claude
    override fun form(context: RenderContext<LocalTime>, value: MutableReactive<LocalTime>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localTimeField {
            content bind value.lens(
                get = { it },
                modify = { old, new -> new ?: old }
            )
        }
    }

    override fun view(context: RenderContext<LocalTime>, value: Reactive<LocalTime>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().renderToString() } }
    }

    override fun cellForm(context: RenderContext<LocalTime>, value: MutableReactive<LocalTime>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<LocalTime>, module: FormModule): Double = 6.0
}

public object NullableLocalTimeRenderer : Renderer<LocalTime?> {
    override val name: String = "Time"  // by Claude
    override fun form(context: RenderContext<LocalTime?>, value: MutableReactive<LocalTime?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.localTimeField { content bind value }
    }

    override fun view(context: RenderContext<LocalTime?>, value: Reactive<LocalTime?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.renderToString() ?: "—" } }
    }

    override fun cellForm(context: RenderContext<LocalTime?>, value: MutableReactive<LocalTime?>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<LocalTime?>, module: FormModule): Double = 6.0
}

// ===== TimeZone =====

public object TimeZoneRenderer : Renderer<TimeZone> {
    override val name: String = "Time Zone"  // by Claude
    private val allTimeZones by lazy { Constant(TimeZone.availableZoneIds.map { TimeZone.of(it) }) }

    override fun form(context: RenderContext<TimeZone>, value: MutableReactive<TimeZone>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.select {
            bind(value, allTimeZones) { it.id }
        }
    }

    override fun view(context: RenderContext<TimeZone>, value: Reactive<TimeZone>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().id } }
    }

    override fun cellForm(context: RenderContext<TimeZone>, value: MutableReactive<TimeZone>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<TimeZone>, module: FormModule): Double = 20.0
}

public object NullableTimeZoneRenderer : Renderer<TimeZone?> {
    override val name: String = "Time Zone"  // by Claude
    private val allTimeZones by lazy { Constant(listOf(null) + TimeZone.availableZoneIds.map { TimeZone.of(it) }) }

    override fun form(context: RenderContext<TimeZone?>, value: MutableReactive<TimeZone?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.select {
            bind(value, allTimeZones) { it?.id ?: "N/A" }
        }
    }

    override fun view(context: RenderContext<TimeZone?>, value: Reactive<TimeZone?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.id ?: "—" } }
    }

    override fun cellForm(context: RenderContext<TimeZone?>, value: MutableReactive<TimeZone?>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<TimeZone?>, module: FormModule): Double = 20.0
}

// ===== Registration =====

public fun FormModule.registerDateTimes() {
    register(Selector(type = "kotlin.time.Instant"), InstantRenderer)
    register(Selector(type = "kotlin.time.Instant?"), NullableInstantRenderer)
    register(Selector(type = "kotlinx.datetime.LocalDateTime"), LocalDateTimeRenderer)
    register(Selector(type = "kotlinx.datetime.LocalDateTime?"), NullableLocalDateTimeRenderer)
    register(Selector(type = "kotlinx.datetime.LocalDate"), LocalDateRenderer)
    register(Selector(type = "kotlinx.datetime.LocalDate?"), NullableLocalDateRenderer)
    register(Selector(type = "kotlinx.datetime.LocalTime"), LocalTimeRenderer)
    register(Selector(type = "kotlinx.datetime.LocalTime?"), NullableLocalTimeRenderer)
    register(Selector(type = "kotlinx.datetime.TimeZone"), TimeZoneRenderer)
    register(Selector(type = "kotlinx.datetime.TimeZone?"), NullableTimeZoneRenderer)
}
