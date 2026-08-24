@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.data.References
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.SerializableAnnotation
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.TypedId
import com.lightningkite.services.database.serializableAnnotations
import com.lightningkite.services.database.serializableProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * A model whose primary key is NOT a foreign key: its [ID] references the model itself
 * (the common pattern used by Lightning Server's `_id` value classes).
 */
@Serializable
@GenerateDataClassPaths
data class FkTestOrg(
    override val _id: FkTestOrg.ID = FkTestOrg.ID(Uuid.random()),
    val name: String = "",
) : HasId<FkTestOrg.ID> {
    @Serializable
    @JvmInline
    @References(FkTestOrg::class)
    value class ID(override val raw: Uuid) : TypedId<Uuid, ID> {
        override fun toString(): String = raw.toString()
    }
}

/**
 * A model whose primary key IS an intentional foreign key to a different type.
 * Used for one-to-ones, e.g. a profile whose primary key is the user it belongs to.
 */
@Serializable
@GenerateDataClassPaths
data class FkTestUser(
    override val _id: Uuid = Uuid.random(),
    val name: String = "",
) : HasId<Uuid>

@Serializable
@GenerateDataClassPaths
data class FkTestProfile(
    override val _id: FkTestProfile.ID = FkTestProfile.ID(Uuid.random()),
    val bio: String = "",
) : HasId<FkTestProfile.ID> {
    @Serializable
    @JvmInline
    @References(FkTestUser::class)
    value class ID(override val raw: Uuid) : TypedId<Uuid, ID> {
        override fun toString(): String = raw.toString()
    }
}

/**
 * The foreign key renderer must not hijack the `_id` field when that primary key merely
 * references its own model. But it must still kick in when the primary key is an intentional
 * foreign key to a *different* type (one-to-one).
 */
class ForeignKeyRendererPrimaryKeyTest {

    private val module = FormModule().apply { registerForeignKey() }

    private fun fieldContext(serializer: KSerializer<*>, parentSerialName: String): RenderContext<Any?> {
        @Suppress("UNCHECKED_CAST")
        val prop = serializer.serializableProperties!!.first { it.name == "_id" }
        return RenderContext(prop.serializer as KSerializer<Any?>, prop.serializableAnnotationsWithPkAnnotationIfId(parentSerialName))
    }

    @Test
    fun selfReferencingPrimaryKey_isTreatedAsPrimaryKey_notForeignKey() {
        val ctx = fieldContext(FkTestOrg.serializer(), FkTestOrg.serializer().descriptor.serialName)

        assertTrue(
            ctx.hasAnnotation(ForeignKeyRenderer.IS_PRIMARY_KEY_FQN),
            "FkTestOrg._id references FkTestOrg itself, so it should be flagged as a primary key"
        )
        assertEquals(-1f, ForeignKeyRenderer.priority(ctx, module))
        assertFalse(
            module.selectAll(ctx).any { it === ForeignKeyRenderer },
            "ForeignKeyRenderer should not be selected for a self-referencing primary key"
        )
    }

    @Test
    fun primaryKeyReferencingDifferentType_stillActivatesForeignKeyRenderer() {
        val ctx = fieldContext(FkTestProfile.serializer(), FkTestProfile.serializer().descriptor.serialName)

        assertFalse(
            ctx.hasAnnotation(ForeignKeyRenderer.IS_PRIMARY_KEY_FQN),
            "FkTestProfile._id references a different type (User), so it should NOT be flagged as a primary key"
        )

        // Resolve the @References target so ForeignKeyRenderer can activate.
        val targetFqn = (ctx.serializer.serializableAnnotations
            .first { it.fqn == "com.lightningkite.services.data.References" }
            .values["references"] as? SerializableAnnotationValue.ClassValue)?.fqn
            ?: error("expected @References to resolve to a class value")
        module.typeInfo = { name -> if (name == targetFqn) TypeInfo(
            FkTestUser.serializer(),
            cache = { error("cache not exercised by priority()") },
            page = { null },
            renderToString = { "" },
        ) else null }

        assertEquals(2f, ForeignKeyRenderer.priority(ctx, module))
        assertTrue(
            module.selectAll(ctx).any { it === ForeignKeyRenderer },
            "ForeignKeyRenderer should be selected when the primary key is an FK to a different type"
        )
    }

    /**
     * Reproduces the admin's runtime path, where models are deserialized from kschema and the
     * `@References` annotation lives on the `_id` *field* while the ID serializer's type annotations
     * are empty (see [FieldRefProperty.serializableAnnotations]). This is exactly how the live
     * `User._id` looks at runtime.
     */
    private class FieldRefProperty(
        private val refFqn: String,
    ) : SerializableProperty<Any, Any?> {
        override val name: String get() = "_id"
        override val serializer: KSerializer<Any?> = String.serializer() as KSerializer<Any?>
        override fun get(receiver: Any): Any? = null
        override fun setCopy(receiver: Any, value: Any?): Any = receiver
        override val serializableAnnotations: List<SerializableAnnotation> get() = listOf(
            SerializableAnnotation(
                "com.lightningkite.services.data.References",
                mapOf("references" to SerializableAnnotationValue.ClassValue(refFqn))
            )
        )
    }

    @Test
    fun runtimeFieldAnnotation_selfReferencingPrimaryKey_isTreatedAsPrimaryKey() {
        // _id field references this model itself -> flagged, FK disabled.
        val prop = FieldRefProperty("com.example.Self")
        val annotated = prop.serializableAnnotationsWithPkAnnotationIfId("com.example.Self")

        assertTrue(
            annotated.any { it.fqn == ForeignKeyRenderer.IS_PRIMARY_KEY_FQN },
            "Runtime-shape self FK on _id should be flagged as a primary key"
        )
    }

    @Test
    fun runtimeFieldAnnotation_primaryKeyReferencingDifferentType_isNotTreatedAsPrimaryKey() {
        // _id field references a *different* type -> not flagged (the one-to-one case).
        val prop = FieldRefProperty("com.example.User")
        val annotated = prop.serializableAnnotationsWithPkAnnotationIfId("com.example.Profile")

        assertFalse(
            annotated.any { it.fqn == ForeignKeyRenderer.IS_PRIMARY_KEY_FQN },
            "Runtime-shape FK to a different type should NOT be flagged as a primary key"
        )
    }
}
