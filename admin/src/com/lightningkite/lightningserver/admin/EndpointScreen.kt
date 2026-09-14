//
// CODE REVIEW SUMMARY
// ===================
// EndpointPage provides a generic UI for testing arbitrary server endpoints.
// It dynamically generates input forms based on endpoint schema and displays results.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. FORCE UNWRAP (Line 44): The `!!` operator on endpoint lookup will crash if
//    the endpoint doesn't exist. Should show user-friendly "Endpoint not found" page.
//
// 2. NULL AUTHENTICATION (Line 81): `adminAuthentication()` can return null but is
//    passed directly to fetcher. Should handle unauthenticated state gracefully.
//
// 3. MISSING ERROR HANDLING (Lines 80-88): The API call has no try-catch. Errors
//    should be caught and displayed to the user.
//
// 4. LOADING STATE: No visual indicator when request is in progress. The "Submit"
//    button could show a spinner or be disabled during the request.
//
// 5. UNUSED EXTENSION (Line 27): `nullable2` extension has a suppressed warning.
//    Consider documenting why this is necessary or refactoring.
//
// 6. MISSING KDOC: The RoutePage inner class and its methods lack documentation.
//
// 7. HARDCODED GAP (Line 32): `gap = 0.px` is hardcoded. Consider using theme spacing.
//
// 8. RESPONSE DISPLAY (Line 94): Large responses could benefit from collapsible
//    sections or virtual scrolling.
//
package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.encodeURIComponent
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.lightningserver.networking.lightningServer
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.default
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Routable("endpoints/{method}/{path}")
class EndpointPage(val path: String, val method: String) : Page {
    @Suppress("UNCHECKED_CAST")
    private val <T> KSerializer<T>.nullable2: KSerializer<T?> get() = if (this.descriptor.isNullable) this as KSerializer<T?> else (this as KSerializer<Any>).nullable as KSerializer<T?>

    class RoutePage<T>(val formModule: FormModule, val name: String, val type: KSerializer<T>, val value: Signal<T> = Signal(type.default())) {
        fun render(viewWriter: ViewWriter) = with(viewWriter) {
            col {
                gap = 0.px
                subtext(name)
                form(formModule, type, value)
            }
        }
        val stringValue = remember {
            UrlProperties.encodeToString(type, value())
        }
    }

    override fun ElementWriter.CanAddTheme.render() {
        val server = adminServer
        val endpoint = remember { adminServer().schema.endpoints.find { it.path == this@EndpointPage.path && it.method == method }!! }
        scrolling.col {
            reactive<Unit> {
                clearChildren()
                val forms = adminFormModule()
                val inputSerializer = endpoint().input.serializer(server().registry, mapOf())
                val outputSerializer = endpoint().output.serializer(server().registry, mapOf())
                val input = Signal<Any?>(inputSerializer.default())
                val output = RawReactive<Any?>(ReactiveState(null))
                val parameters = endpoint().routes.mapValues {
                    val type = it.value.serializer(server().registry, mapOf())
                    RoutePage(forms, it.key, type)
                }
                val path = remember {
                    var s = endpoint().path
                    for ((name, value) in parameters) {
                        s = s.replace("{$name}", encodeURIComponent(value.stringValue()))
                    }
                    s
                }
                h1 {
                    ::content { "${endpoint().method} ${path()}" }
                }
                if(parameters.isNotEmpty()) {
                    card.col {
                        for ((key, value) in parameters) {
                            value.render(this)
                        }
                    }
                }
                if(inputSerializer != Unit.serializer())
                    card.form(forms, inputSerializer, input)
                atEnd.important.button {
                    text("Submit")
                    onClick {
                        output.state = ReactiveState.notReady
                        output.state = reactiveState {
                            server().fetcher(adminAuthentication()).invoke(
                                url = path(),
                                method = HttpMethod.valueOf(endpoint().method).lightningServer,
                                inSerializer = inputSerializer,
                                body = input.value,
                                outSerializer = outputSerializer,
                            )
                        }
                    }
                }
                separator()
                errorText()
                reactive { output() }
                card.view(forms, outputSerializer.nullable2, output)
            }
        }
    }
}