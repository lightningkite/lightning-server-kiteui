@file:UseContextualSerialization(Uuid::class, ServerFile::class)
package com.lightningkite.lightningserver.demo

import kotlin.uuid.Uuid
import com.lightningkite.services.database.*
import com.lightningkite.services.files.ServerFile
import kotlin.time.Clock.System.now
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
@GenerateDataClassPaths
@AdminTableColumns(["name", "number", "status"])
@Description("A model for testing Lightning Server.")
data class TestModel(
    override val _id: Uuid = Uuid.random(),
    @Contextual val timestamp: Instant = now(),
    val name: String = "No Name",
    @Description("The number") val number: Int = 3123,
    @MimeType("text/html") @Multiline val content: String = "",
    @MimeType("image/*") val file: ServerFile? = null,
    @References(TestModel::class) val replyTo: Uuid? = null,
    @MultipleReferences(TestModel::class) val comments: List<Uuid> = listOf(),
    val privateInfo: String? = null,
    val status: Status = Status.DRAFT,
    @AdminHidden val hiddenField: Boolean = false
) : HasId<Uuid>

@Serializable
enum class Status {
    @DisplayName("Draft") DRAFT,
    @DisplayName("Published") PUBLISHED
}

@Serializable
@GenerateDataClassPaths
data class User(
    override val _id: Uuid = Uuid.random(),
    override val email: String,
    override val hashedPassword: String = "",
    val isSuperUser: Boolean = false,
) : HasId<Uuid>, HasEmail, HasPassword
