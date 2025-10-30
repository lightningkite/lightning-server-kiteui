package com.lightningkite.lightningserver.db

import kotlin.uuid.Uuid
import com.lightningkite.services.database.*
import com.lightningkite.services.data.ExpectedPattern
import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.data.Index
import com.lightningkite.services.data.MaxLength
import kotlinx.serialization.Serializable


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
    @Index(unique = true) override var email: String,
    @Index(unique = true) override val phoneNumber: String,
    var age: Long = 0,
    var friends: List<Uuid> = listOf()
) : HasId<Uuid>, HasEmail, HasPhoneNumber {
    companion object
}
@Serializable
enum class TestEnum { One, Two }