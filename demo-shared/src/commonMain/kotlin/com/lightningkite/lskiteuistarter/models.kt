package com.lightningkite.lskiteuistarter

import com.lightningkite.EmailAddress
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
    val platform: AppPlatform,
    val releaseDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val requiredUpdate: Boolean,
) : HasId<Uuid>

@Serializable
@GenerateDataClassPaths
data class User(
    override val _id: Uuid = Uuid.random(),
    val email: EmailAddress,
    val name: String = "No Name Specified",
    val role: UserRole = UserRole.User,
) : HasId<Uuid>

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
    val active: Boolean = true,
    val created: Instant = Clock.System.now(),
    val lastRegisteredAt: Instant = created,
    val userAgent: String? = null,
) : HasId<String>
