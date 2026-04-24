@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.components.CodeBlockSemantic
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.serializableProperties
import com.lightningkite.services.database.listElement
import com.lightningkite.services.database.default
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind

/**
 * Renderer for List<DataClass> as CSV text.
 *
 * Each row is a data class instance, columns are fields.
 * Supports editing via CSV text input.
 *
 * by Claude
 */
public object CsvRenderer : Renderer<List<Any?>> {
    override val name: String = "CSV"

    override fun priority(context: RenderContext<List<Any?>>, module: FormModule): Float {
        // Only match lists of data classes (objects with properties)
        if (context.serializer.descriptor.kind != StructureKind.LIST) return -1f
        val elementSerializer = context.serializer.listElement() ?: return -1f
        if (elementSerializer.descriptor.kind != StructureKind.CLASS) return -1f
        if (elementSerializer.serializableProperties.isNullOrEmpty()) return -1f

        // Low priority - should be selected via switcher
        return 0.2f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<List<Any?>>, value: MutableReactive<List<Any?>>, module: FormModule): ViewWriter.() -> Unit {
        val elementSerializer = context.serializer.listElement() as? KSerializer<Any?> ?: return { text("Not a valid list") }
        val properties = elementSerializer.serializableProperties?.toList() ?: return { text("Not a data class") }

        return {
            val errorMessage = Signal<String?>(null)

            col {
                themed(CodeBlockSemantic).textArea {
                    content bind value.lens(
                        get = { list -> toCsv(list, properties) },
                        modify = { original, csv ->
                            try {
                                errorMessage.value = null
                                fromCsv(csv, properties, elementSerializer)
                            } catch (e: Exception) {
                                errorMessage.value = "CSV parse error: ${e.message}"
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
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<List<Any?>>, value: Reactive<List<Any?>>, module: FormModule): ViewWriter.() -> Unit {
        val elementSerializer = context.serializer.listElement() as? KSerializer<Any?> ?: return { text("Not a valid list") }
        val properties = elementSerializer.serializableProperties?.toList() ?: return { text("Not a data class") }

        return {
            themed(CodeBlockSemantic).text {
                wraps = true
                ::content { toCsv(value(), properties) }
            }
        }
    }

    override fun cellView(context: RenderContext<List<Any?>>, value: Reactive<List<Any?>>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { "${value().size} rows" } }
    }

    override fun columnWidth(context: RenderContext<List<Any?>>, module: FormModule): Double = 15.0

    // ===== CSV Helpers =====

    @Suppress("UNCHECKED_CAST")
    private fun toCsv(list: List<Any?>, properties: List<SerializableProperty<*, *>>): String {
        val sb = StringBuilder()

        // Header row
        sb.appendLine(properties.joinToString(",") { escapeCsvField(it.name) })

        // Data rows
        for (item in list) {
            if (item == null) continue
            val row = properties.map { prop ->
                val typedProp = prop as SerializableProperty<Any, Any?>
                val fieldValue = typedProp.get(item)
                escapeCsvField(fieldValue?.toString() ?: "")
            }
            sb.appendLine(row.joinToString(","))
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromCsv(csv: String, properties: List<SerializableProperty<*, *>>, elementSerializer: KSerializer<Any?>): List<Any?> {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // Parse header to get column order
        val header = parseCsvLine(lines[0])
        val columnMap = header.mapIndexed { index, name -> name to index }.toMap()

        // Parse data rows
        return lines.drop(1).map { line ->
            val fields = parseCsvLine(line)

            // Start with default instance
            var instance = createDefaultInstance(elementSerializer)

            // Set each property from CSV
            for (prop in properties) {
                val colIndex = columnMap[prop.name] ?: continue
                if (colIndex >= fields.size) continue

                val fieldValue = fields[colIndex]
                val typedProp = prop as SerializableProperty<Any, Any?>

                // Parse the field value based on the property type
                val parsedValue = parseFieldValue(fieldValue, prop)
                if (parsedValue != null || prop.serializer.descriptor.isNullable) {
                    instance = typedProp.setCopy(instance, parsedValue)
                }
            }

            instance
        }
    }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())

        return fields
    }

    @Suppress("UNCHECKED_CAST")
    private fun createDefaultInstance(serializer: KSerializer<Any?>): Any {
        // Use the default() extension to create a default instance - by Claude
        return serializer.default() as Any
    }

    private fun parseFieldValue(value: String, prop: SerializableProperty<*, *>): Any? {
        if (value.isBlank() && prop.serializer.descriptor.isNullable) return null

        val typeName = prop.serializer.descriptor.serialName
        return when {
            typeName == "kotlin.String" -> value
            typeName == "kotlin.Int" -> value.toIntOrNull()
            typeName == "kotlin.Long" -> value.toLongOrNull()
            typeName == "kotlin.Double" -> value.toDoubleOrNull()
            typeName == "kotlin.Float" -> value.toFloatOrNull()
            typeName == "kotlin.Boolean" -> value.lowercase() in listOf("true", "1", "yes")
            typeName == "kotlin.Short" -> value.toShortOrNull()
            typeName == "kotlin.Byte" -> value.toByteOrNull()
            else -> value  // Fallback to string representation
        }
    }
}

public fun FormModule.registerCsv() {
    register(Selector(kind = StructureKind.LIST), CsvRenderer)
}
