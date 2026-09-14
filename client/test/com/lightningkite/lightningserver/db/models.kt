package com.lightningkite.lightningserver.db

import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.data.Index
import kotlin.uuid.Uuid
import com.lightningkite.services.database.HasId
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable


@GenerateDataClassPaths
@Serializable
data class Item(override val _id: Int, val creation: Int = 0) : HasId<Int>

@GenerateDataClassPaths
@Serializable
data class LargeTestModel(
    override val _id: Uuid = Uuid.random(),
    var boolean: Boolean = false,
    var byte: Byte = 0,
    var short: Short = 0,
    @Index var int: Int = 0,
    var long: Long = 0,
    var float: Float = 0f,
    var double: Double = 0.0,
    var char: Char = ' ',
    var string: String = "",
    var uuid: Uuid = Uuid.random(),
    @Contextual var instant: Instant = Instant.fromEpochMilliseconds(0L),
    var list: List<Int> = listOf(),
    var listEmbedded: List<ClassUsedForEmbedding> = listOf(),
    var set: Set<Int> = setOf(),
    var setEmbedded: Set<ClassUsedForEmbedding> = setOf(),
    var map: Map<String, Int> = mapOf(),
    var embedded: ClassUsedForEmbedding = ClassUsedForEmbedding(),
    var booleanNullable: Boolean? = null,
    var byteNullable: Byte? = null,
    var shortNullable: Short? = null,
    var intNullable: Int? = null,
    var longNullable: Long? = null,
    var floatNullable: Float? = null,
    var doubleNullable: Double? = null,
    var charNullable: Char? = null,
    var stringNullable: String? = null,
    var UuidNullable: Uuid? = null,
    @Contextual var instantNullable: Instant? = null,
    var listNullable: List<Int>? = null,
    var mapNullable: Map<String, Int>? = null,
    var embeddedNullable: ClassUsedForEmbedding? = null,
) : HasId<Uuid> {
    companion object
}
@GenerateDataClassPaths
@Serializable
data class ClassUsedForEmbedding(
    var value1:String = "default",
    var value2:Int = 1
)
