package com.lightningkite.lightningserver.db

import com.lightningkite.UUID
import com.lightningkite.lightningdb.GenerateDataClassPaths
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.Index
import com.lightningkite.uuid
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable


@GenerateDataClassPaths
@Serializable
data class Item(override val _id: Int, val creation: Int = 0) : HasId<Int>

@GenerateDataClassPaths
@Serializable
data class LargeTestModel(
    override val _id: UUID = uuid(),
    var boolean: Boolean = false,
    var byte: Byte = 0,
    var short: Short = 0,
    @Index var int: Int = 0,
    var long: Long = 0,
    var float: Float = 0f,
    var double: Double = 0.0,
    var char: Char = ' ',
    var string: String = "",
    var uuid: UUID = UUID(0L, 0L),
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
    var uuidNullable: UUID? = null,
    @Contextual var instantNullable: Instant? = null,
    var listNullable: List<Int>? = null,
    var mapNullable: Map<String, Int>? = null,
    var embeddedNullable: ClassUsedForEmbedding? = null,
) : HasId<UUID> {
    companion object
}
@GenerateDataClassPaths
@Serializable
data class ClassUsedForEmbedding(
    var value1:String = "default",
    var value2:Int = 1
)
