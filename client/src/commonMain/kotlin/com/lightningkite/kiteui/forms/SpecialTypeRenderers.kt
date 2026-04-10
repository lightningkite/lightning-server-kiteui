package com.lightningkite.kiteui.forms

import com.lightningkite.EmailAddress
import com.lightningkite.GeoCoordinate
import com.lightningkite.PhoneNumber
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.toEmailAddress
import com.lightningkite.toPhoneNumber
import kotlin.uuid.Uuid

// ===== UUID =====
// by Claude

object UuidRenderer : Renderer<Uuid> {
    override val name: String = "Uuid"  // by Claude
    override fun form(context: RenderContext<Uuid>, value: MutableReactive<Uuid>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        row {
            expanding.textInput {
                content bind value.lens(
                    get = { it.toString() },
                    modify = { old, new ->
                        try {
                            Uuid.parse(new)
                        } catch (e: Exception) {
                            old
                        }
                    }
                )
            }
            button {
                icon(Icon.sync, "Regenerate")
                onClick { value set Uuid.random() }
            }
        }
    }

    override fun view(context: RenderContext<Uuid>, value: Reactive<Uuid>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text { ::content { value().toString() } }
    }

    override fun columnWidth(context: RenderContext<Uuid>, module: FormModule) = 24.0
}

// ===== EmailAddress =====

object EmailAddressRenderer : Renderer<EmailAddress> {
    override val name: String = "Email"  // by Claude
    override fun form(context: RenderContext<EmailAddress>, value: MutableReactive<EmailAddress>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            textInput {
                content bind value.lens(
                    get = { it.raw },
                    set = { it.toEmailAddress() }
                )
            }
            errorText()
        }
    }

    override fun view(context: RenderContext<EmailAddress>, value: Reactive<EmailAddress>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        externalLink {
            text {
                ::content { value().raw }
                wraps = false
                ellipsis = true
            }
            ::to { value().url }
        }
    }

    override fun cellView(context: RenderContext<EmailAddress>, value: Reactive<EmailAddress>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text {
            ::content { value().raw }
            wraps = false
            ellipsis = true
        }
    }

    override fun columnWidth(context: RenderContext<EmailAddress>, module: FormModule) = 20.0
}

// ===== PhoneNumber =====

object PhoneNumberRenderer : Renderer<PhoneNumber> {
    override val name: String = "Phone"  // by Claude
    override fun form(context: RenderContext<PhoneNumber>, value: MutableReactive<PhoneNumber>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            textInput {
                content bind value.lens(
                    get = { it.raw },
                    set = { it.toPhoneNumber() }
                )
            }
            errorText()
        }
    }

    override fun view(context: RenderContext<PhoneNumber>, value: Reactive<PhoneNumber>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        externalLink {
            text {
                ::content { value().raw }
                wraps = false
                ellipsis = true
            }
            ::to { value().url }
        }
    }

    override fun cellView(context: RenderContext<PhoneNumber>, value: Reactive<PhoneNumber>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text {
            ::content { value().raw }
            wraps = false
            ellipsis = true
        }
    }

    override fun columnWidth(context: RenderContext<PhoneNumber>, module: FormModule) = 15.0
}

// ===== GeoCoordinate =====

object GeoCoordinateRenderer : Renderer<GeoCoordinate> {
    override val name: String = "Coordinates"  // by Claude
    override fun form(context: RenderContext<GeoCoordinate>, value: MutableReactive<GeoCoordinate>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        row {
            expanding.fieldTheme.numberInput {
                hint = "Latitude"
                content bind value.lens(
                    get = { it.latitude },
                    modify = { old, new -> old.copy(latitude = new ?: 0.0) }
                )
            }
            expanding.fieldTheme.numberInput {
                hint = "Longitude"
                content bind value.lens(
                    get = { it.longitude },
                    modify = { old, new -> old.copy(longitude = new ?: 0.0) }
                )
            }
        }
    }

    override fun view(context: RenderContext<GeoCoordinate>, value: Reactive<GeoCoordinate>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text { ::content { "${value().latitude}, ${value().longitude}" } }
    }

    override fun columnWidth(context: RenderContext<GeoCoordinate>, module: FormModule) = 20.0

    override fun labeledForm(
        context: RenderContext<GeoCoordinate>,
        value: MutableReactive<GeoCoordinate>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { form(context, value, module)() }
    }

    override fun labeledView(
        context: RenderContext<GeoCoordinate>,
        value: Reactive<GeoCoordinate>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { view(context, value, module)() }
    }
}

// ===== Registration =====

fun FormModule.registerSpecialTypes() {
    register(Selector(type = "kotlin.uuid.Uuid"), UuidRenderer)
    register(Selector(type = "com.lightningkite.uuid.Uuid"), UuidRenderer)
    register(Selector(type = "com.lightningkite.Uuid"), UuidRenderer)
    register(Selector(type = "com.lightningkite.EmailAddress"), EmailAddressRenderer)
    register(Selector(type = "com.lightningkite.PhoneNumber"), PhoneNumberRenderer)
    register(Selector(type = "com.lightningkite.GeoCoordinate"), GeoCoordinateRenderer)
}
