package com.lightningkite.template

import com.lightningkite.EmailAddress
import com.lightningkite.UUID
import com.lightningkite.lightningdb.GenerateDataClassPaths
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.Index
import com.lightningkite.lightningdb.MaxLength
import com.lightningkite.lightningdb.References
import com.lightningkite.now
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable


@Serializable
@GenerateDataClassPaths
data class User(
    override val _id: UUID = UUID.random(),
    val email: EmailAddress,
    val name: String = "",
    val role: UserRole = UserRole.User,
) : HasId<UUID>

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
    @Index @References(User::class) val user: UUID,
    val active: Boolean = true,
    val created: Instant = now(),
    val lastRegisteredAt: Instant = created,
    val userAgent: String? = null,
) : HasId<String>


@Serializable
@GenerateDataClassPaths
data class SpammyMessage(
    override val _id: UUID = UUID.random(),
    val content: String,
    @Index val sentAt: Instant = now(),
) : HasId<UUID>