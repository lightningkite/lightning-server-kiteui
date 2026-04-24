package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.lensing.lens

/**
 * Hex renderer for Byte values.
 *
 * Displays and edits bytes in hexadecimal format (e.g., "0xFF").
 *
 * by Claude
 */
public object HexByteRenderer : Renderer<Byte> {
    override val name: String = "Hex"

    override fun priority(context: RenderContext<Byte>, module: FormModule): Float = 0.5f

    override fun form(context: RenderContext<Byte>, value: MutableReactive<Byte>, module: FormModule): ViewWriter.() -> Unit = {
        val errorMessage = Signal<String?>(null)

        col {
            fieldTheme.textInput {
                align = Align.End
                content bind value.lens(
                    get = { "0x${it.toUByte().toString(16).uppercase().padStart(2, '0')}" },
                    modify = { original, newText ->
                        try {
                            errorMessage.value = null
                            val cleaned = newText.removePrefix("0x").removePrefix("0X")
                            cleaned.toUByte(16).toByte()
                        } catch (e: Exception) {
                            errorMessage.value = "Invalid hex"
                            original
                        }
                    }
                )
            }
            text {
                ::shown { errorMessage() != null }
                ::content { errorMessage() ?: "" }
            }
        }
    }

    override fun view(context: RenderContext<Byte>, value: Reactive<Byte>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { "0x${value().toUByte().toString(16).uppercase().padStart(2, '0')}" } }
    }

    override fun cellForm(context: RenderContext<Byte>, value: MutableReactive<Byte>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<Byte>, module: FormModule): Double = 6.0
}

/**
 * Hex renderer for Short values.
 *
 * Displays and edits shorts in hexadecimal format (e.g., "0xFFFF").
 *
 * by Claude
 */
public object HexShortRenderer : Renderer<Short> {
    override val name: String = "Hex"

    override fun priority(context: RenderContext<Short>, module: FormModule): Float = 0.5f

    override fun form(context: RenderContext<Short>, value: MutableReactive<Short>, module: FormModule): ViewWriter.() -> Unit = {
        val errorMessage = Signal<String?>(null)

        col {
            fieldTheme.textInput {
                align = Align.End
                content bind value.lens(
                    get = { "0x${it.toUShort().toString(16).uppercase().padStart(4, '0')}" },
                    modify = { original, newText ->
                        try {
                            errorMessage.value = null
                            val cleaned = newText.removePrefix("0x").removePrefix("0X")
                            cleaned.toUShort(16).toShort()
                        } catch (e: Exception) {
                            errorMessage.value = "Invalid hex"
                            original
                        }
                    }
                )
            }
            text {
                ::shown { errorMessage() != null }
                ::content { errorMessage() ?: "" }
            }
        }
    }

    override fun view(context: RenderContext<Short>, value: Reactive<Short>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { "0x${value().toUShort().toString(16).uppercase().padStart(4, '0')}" } }
    }

    override fun cellForm(context: RenderContext<Short>, value: MutableReactive<Short>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<Short>, module: FormModule): Double = 8.0
}

/**
 * Hex renderer for Int values.
 *
 * Displays and edits ints in hexadecimal format (e.g., "0xFFFFFFFF").
 *
 * by Claude
 */
public object HexIntRenderer : Renderer<Int> {
    override val name: String = "Hex"

    override fun priority(context: RenderContext<Int>, module: FormModule): Float = 0.5f

    override fun form(context: RenderContext<Int>, value: MutableReactive<Int>, module: FormModule): ViewWriter.() -> Unit = {
        val errorMessage = Signal<String?>(null)

        col {
            fieldTheme.textInput {
                align = Align.End
                content bind value.lens(
                    get = { "0x${it.toUInt().toString(16).uppercase().padStart(8, '0')}" },
                    modify = { original, newText ->
                        try {
                            errorMessage.value = null
                            val cleaned = newText.removePrefix("0x").removePrefix("0X")
                            cleaned.toUInt(16).toInt()
                        } catch (e: Exception) {
                            errorMessage.value = "Invalid hex"
                            original
                        }
                    }
                )
            }
            text {
                ::shown { errorMessage() != null }
                ::content { errorMessage() ?: "" }
            }
        }
    }

    override fun view(context: RenderContext<Int>, value: Reactive<Int>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { "0x${value().toUInt().toString(16).uppercase().padStart(8, '0')}" } }
    }

    override fun cellForm(context: RenderContext<Int>, value: MutableReactive<Int>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<Int>, module: FormModule): Double = 12.0
}

/**
 * Hex renderer for Long values.
 *
 * Displays and edits longs in hexadecimal format (e.g., "0xFFFFFFFFFFFFFFFF").
 *
 * by Claude
 */
public object HexLongRenderer : Renderer<Long> {
    override val name: String = "Hex"

    override fun priority(context: RenderContext<Long>, module: FormModule): Float = 0.5f

    override fun form(context: RenderContext<Long>, value: MutableReactive<Long>, module: FormModule): ViewWriter.() -> Unit = {
        val errorMessage = Signal<String?>(null)

        col {
            fieldTheme.textInput {
                align = Align.End
                content bind value.lens(
                    get = { "0x${it.toULong().toString(16).uppercase().padStart(16, '0')}" },
                    modify = { original, newText ->
                        try {
                            errorMessage.value = null
                            val cleaned = newText.removePrefix("0x").removePrefix("0X")
                            cleaned.toULong(16).toLong()
                        } catch (e: Exception) {
                            errorMessage.value = "Invalid hex"
                            original
                        }
                    }
                )
            }
            text {
                ::shown { errorMessage() != null }
                ::content { errorMessage() ?: "" }
            }
        }
    }

    override fun view(context: RenderContext<Long>, value: Reactive<Long>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { "0x${value().toULong().toString(16).uppercase().padStart(16, '0')}" } }
    }

    override fun cellForm(context: RenderContext<Long>, value: MutableReactive<Long>, module: FormModule): ViewWriter.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<Long>, module: FormModule): Double = 18.0
}

public fun FormModule.registerHex() {
    register(Selector(type = "kotlin.Byte"), HexByteRenderer)
    register(Selector(type = "kotlin.Short"), HexShortRenderer)
    register(Selector(type = "kotlin.Int"), HexIntRenderer)
    register(Selector(type = "kotlin.Long"), HexLongRenderer)
}
