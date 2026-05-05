package com.lightningkite.lskiteuistarter

import com.lightningkite.services.data.*
import com.lightningkite.services.database.HasId
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid


@Serializable
enum class AppPlatform {
    iOS,
    Android,
    Web,
    Desktop,
    ;

    companion object
}

@GenerateDataClassPaths
@Serializable
data class AppRelease(
    override val _id: Uuid = Uuid.random(),
    val version: String,
    val platform: AppPlatform?,
    val releaseDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val requiredUpdate: Boolean,
    val testNestedArrayTwice: List<ObjectWithArray>? =  null
) : HasId<Uuid>

@Serializable
data class ObjectWithArray(
    val array:List<Int>,
    val title: String,
)

@Serializable
@GenerateDataClassPaths
data class User(
    override val _id: Uuid = Uuid.random(),
    val email: EmailAddress,
    val name: String = "No Name Specified",
    val role: UserRole = UserRole.User,
) : HasId<Uuid> {
    @Serializable
    @GenerateDataClassPaths
    data class NestedTypeModel(
        override val _id: Uuid = Uuid.random(),
        val name: String = ""
    ) : HasId<Uuid>
}

@Serializable
enum class UserRole {
    NoOne,
    User,
    Admin,
    Developer,
    Root
}

@Serializable
@GenerateDataClassPaths
data class FcmToken(
    @MaxLength(160, 142) override val _id: String,
    @Index @References(User::class) val user: Uuid,
//    val active: Boolean = true,
//    val created: Instant = Clock.System.now(),
//    val lastRegisteredAt: Instant = created,
//    val userAgent: String? = null,
    val test: Test?=null
) : HasId<String>


@Serializable
data class Test(
    val fieldOne:String,
    val fieldTwo: Double,
//    val testArray:List<Test> = emptyList()
)

@Serializable
@GenerateDataClassPaths
data class SealedPolymorhphicModel(
    override val _id: Uuid = Uuid.random(),
    val title: String = "Test",

    val sealedClassItems:List<SealedClassItem> = emptyList(),
    val header:SealedClassItem?=null

): HasId<Uuid>


@Serializable
sealed class SealedClassItem {
    abstract val id: String
    abstract val description: String

    @Serializable
    data class Paragraph(
        override val id: String ="test",
        override val description: String,
        val text: String,
    ) : SealedClassItem()

    @Serializable
    data class Image(
        override val id: String = "test2",
        val url: String,
        override val description: String,
        ) : SealedClassItem()

    @Serializable
    data class Header(
        override val id: String = "test2",
        override val description: String,
        val header:String = "Header",
        val strenght: Double = 1.0
    ) : SealedClassItem()

    @Serializable
    data class NestedArrayTest(
        override val id: String = "test2",
        override val description: String,

        val nestedItems: List<Test> = emptyList(),
//        val nestedComponents: List<Component> = emptyList()
        ): SealedClassItem()

    @Serializable
    data class NestedObject(
        override val id: String = "test2",
        override val description: String,
        val testObject:Test = Test(
            fieldOne = "test1",
            fieldTwo = 1.0,
        )
    ) : SealedClassItem()
}