@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.reactive.AppState
import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.schema.ExternalLightningServer
import com.lightningkite.serialization.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlin.js.JsName
import kotlin.jvm.JvmName

interface RendererGenerator {
    val name: String
    val kind: SerialKind? get() = null
    val type: String? get() = null
    val nullable: Boolean get() = false
    val annotation: String? get() = null
    val handlesField: Boolean get() = false
    fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize.Inline
    val basePriority: Float get() = 1f
    fun priority(module: FormModule, selector: FormSelector<*>): Float {
        var amount = basePriority
        if (handlesField && selector.handlesField) amount *= 1.2f
        size(module, selector).let { size ->
            val widthOverage = ((size.approximateWidth - (selector.desiredSize.approximateWidthBound ?: 1000.0)) / (selector.desiredSize.approximateWidthBound ?: 1000.0)).coerceAtLeast(0.0)
            val heightOverage = ((size.approximateHeight - (selector.desiredSize.approximateHeightBound ?: 1000.0)) / (selector.desiredSize.approximateHeightBound ?: 1000.0)).coerceAtLeast(0.0)
            amount *= 1 / (1f + widthOverage.toFloat())
            amount *= 1 / (1f + heightOverage.toFloat())
        }
        return amount
    }

    fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        if (nullable != selector.serializer.descriptor.isNullable) return false
        if (type != null && selector.serializer.descriptor.serialName.substringBefore('/') != type) return false
        if (kind != null && selector.serializer.descriptor.kind != kind) return false
        if (annotation != null && selector.annotations.none { it.fqn == annotation }) return false
        return true
    }
}

interface Renderer<T> {
    val generator: RendererGenerator?
    val selector: FormSelector<T>
    val size: FormSize
    val handlesField: Boolean
}

data class ViewRenderer<T>(
    val module: FormModule,
    override val generator: ViewRenderer.Generator?,
    override val selector: FormSelector<T>,
    override val size: FormSize = generator!!.size(module, selector),
    override val handlesField: Boolean = generator!!.handlesField,
    val render: ViewWriter.(field: SerializableProperty<*, *>?, readable: Readable<T>) -> Unit
) : Renderer<T> {
    interface Generator : RendererGenerator {
        fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T>
    }
}

data class FormRenderer<T>(
    val module: FormModule,
    override val generator: FormRenderer.Generator?,
    override val selector: FormSelector<T>,
    override val size: FormSize = generator!!.size(module, selector),
    override val handlesField: Boolean = generator!!.handlesField,
    val render: ViewWriter.(field: SerializableProperty<*, *>?, writable: Writable<T>) -> Unit
) : Renderer<T> {
    interface Generator : RendererGenerator {
        fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T>
    }
}

data class FormSelector<T>(
    val serializer: KSerializer<T>,
    val annotations: List<SerializableAnnotation>,
    val desiredSize: FormLayoutPreferences = FormLayoutPreferences.Block,
    val handlesField: Boolean = false,
    val withPicker: Boolean = false,
) {
    override fun toString(): String = serializer.descriptor.serialName

    @Suppress("UNCHECKED_CAST")
    @JvmName("copyChangingType")
    @JsName("copyChangingType")
    fun <O> copy(
        serializer: KSerializer<O>,
        annotations: List<SerializableAnnotation> = this.annotations,
        desiredSize: FormLayoutPreferences = this.desiredSize,
        handlesField: Boolean = this.handlesField,
        withPicker: Boolean = this.withPicker,
    ) = FormSelector<O>(
        serializer = serializer,
        annotations = annotations,
        desiredSize = desiredSize,
        handlesField = handlesField,
        withPicker = withPicker,
    )
}

fun <T : HasId<ID>, ID : Comparable<ID>> KSerializer<T>.defaultTitleFields(): List<DataClassPath<T, *>> {
    val serializer = this
    val it = serializer.serializableProperties!!
    val dcps = DataClassPathSerializer(serializer)
    val nameFields: List<DataClassPath<T, *>> = serializer.serializableAnnotations.find {
        it.fqn == "com.lightningkite.lightningdb.AdminTitleFields"
    }?.values?.get("fields")?.let { it as? SerializableAnnotationValue.ArrayValue }
        ?.value
        ?.mapNotNull { it as? SerializableAnnotationValue.StringValue }
        ?.map { it.value }
        ?.toSet()
        ?.let { matching ->
            matching.map { dcps.fromString(it) as DataClassPath<T, *> }
        }
        ?: it.find { it.name == "name" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "title" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "subject" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.map { DataClassPathAccess(DataClassPathSelf(serializer), it) }.take(3)
    return nameFields
}

class FormTypeInfo<T : HasId<ID>, ID : Comparable<ID>>(
    val serializer: KSerializer<T>,
    val cache: () -> ModelCache<T, ID>,
    val screen: (ID) -> (() -> Screen)?,
    val titleFields: List<DataClassPath<T, *>> = serializer.defaultTitleFields(),
    val renderToString: (suspend (ID) -> String)
)

fun <T> ViewWriter.form(
    context: FormModule,
    serializer: KSerializer<T>,
    writable: Writable<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    desiredSize: FormLayoutPreferences = FormLayoutPreferences.Unbound,
    field: SerializableProperty<*, *>? = null,
) {
    val sel = FormSelector<T>(serializer, annotations, desiredSize)
    context.form(sel).render(this, field, writable)
}

fun <T> ViewWriter.view(
    context: FormModule,
    serializer: KSerializer<T>,
    readable: Readable<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    desiredSize: FormLayoutPreferences = FormLayoutPreferences.Unbound,
    field: SerializableProperty<*, *>? = null,
) {
    val sel = FormSelector<T>(serializer, annotations, desiredSize)
    context.view(sel).render(this, field, readable)
}