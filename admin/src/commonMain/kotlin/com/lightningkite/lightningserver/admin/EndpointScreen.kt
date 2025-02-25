package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.encodeURIComponent
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atEnd
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.serialization.default
import com.lightningkite.serialization.nullable2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

@Routable("endpoints/{method}/{path}")
class EndpointScreen(val path: String, val method: String) : Screen {

    class RouteScreen<T>(val formModule: FormModule, val name: String, val type: KSerializer<T>, val value: Property<T> = Property(type.default())) {
        fun render(viewWriter: ViewWriter) = with(viewWriter) {
            col {
                spacing = 0.px
                subtext(name)
                form(formModule, type, value)
            }
        }
        val stringValue = shared {
            UrlProperties.encodeToString(type, value())
        }
    }

    override fun ViewWriter.render() {
        val server = adminServer
        val endpoint = shared { adminServer().schema.endpoints.find { it.path == this@EndpointScreen.path && it.method == method }!! }
        scrolls - col {
            reactive {
                clearChildren()
                val forms = adminFormModule()
                val inputSerializer = endpoint().input.serializer(server().registry, mapOf())
                val outputSerializer = endpoint().output.serializer(server().registry, mapOf())
                val input = Property<Any?>(inputSerializer.default())
                val output = RawReadable<Any?>(ReadableState(null))
                val parameters = endpoint().routes.mapValues {
                    val type = it.value.serializer(server().registry, mapOf())
                    RouteScreen(forms, it.key, type)
                }
                val path = shared {
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
                    card - col {
                        for ((key, value) in parameters) {
                            value.render(this)
                        }
                    }
                }
                if(inputSerializer != Unit.serializer())
                    card - form(forms, inputSerializer, input)
                atEnd - important - button {
                    text("Submit")
                    onClick {
                        output.state = ReadableState.notReady
                        output.state = readableState {
                            server().fetcher("", adminAuthentication()).invoke(
                                url = path(),
                                method = HttpMethod.valueOf(endpoint().method),
                                jsonBody = DefaultJson.encodeToString(inputSerializer, input.value),
                                outSerializer = outputSerializer,
                            )
                        }
                    }
                }
                separator()
                errorText()
                reactive { output() }
                card - view(forms, outputSerializer.nullable2, output)
            }
        }
    }
}