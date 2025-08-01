package com.lightningkite.template

import com.lightningkite.UUID
import com.lightningkite.lightningserver.auth.RequestAuth
import com.lightningkite.lightningserver.typed.AuthAccessor
import com.lightningkite.lightningserver.typed.auth
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


// A cache key for caching the users role in an access token
object RoleCacheKey : RequestAuth.CacheKey<User, UUID, UserRole>() {
    override val name: String
        get() = "role"
    override val serializer: KSerializer<UserRole>
        get() = UserRole.serializer()
    override val validFor: Duration
        get() = 5.minutes

    override suspend fun calculate(auth: RequestAuth<User>): UserRole = auth.get().role
}

suspend fun RequestAuth<User>.role() = this.get(RoleCacheKey)
suspend fun AuthAccessor<User>.role() = this.auth.get(RoleCacheKey)