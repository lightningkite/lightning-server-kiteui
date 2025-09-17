
package com.lightningkite.lightningdb.test

import kotlin.uuid.Uuid
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.services.database.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.services.data.ExpectedPattern
import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.data.MaxLength
import com.lightningkite.services.data.Unique
import com.lightningkite.services.files.ServerFile
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer


@GenerateDataClassPaths
@Serializable
data class ValidatedModel(
    @ExpectedPattern("[a-zA-Z ]+") @MaxLength(15) val name: String,
)
@GenerateDataClassPaths
@Serializable
data class NestedEnumTestModel(
    override val _id: Uuid = Uuid.random(),
    val thing: NestedEnumHolder = NestedEnumHolder()
) : HasId<Uuid> {
    companion object
}

@GenerateDataClassPaths
@Serializable
data class NestedEnumHolder(
    val enumValue: TestEnum = TestEnum.One
)
@GenerateDataClassPaths()
@Serializable
data class User(
    override val _id: Uuid = Uuid.random(),
    @Unique override var email: String,
    @Unique override val phoneNumber: String,
    var age: Long = 0,
    var friends: List<Uuid> = listOf()
) : HasId<Uuid>, HasEmail, HasPhoneNumber {
    companion object
}
@Serializable
enum class TestEnum { One, Two }