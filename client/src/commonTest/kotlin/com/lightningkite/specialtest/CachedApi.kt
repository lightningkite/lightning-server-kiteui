package com.lightningkite.specialtest

import com.lightningkite.lightningserver.db.ModelCache
import kotlinx.serialization.ContextualSerializer

open class CachedApi2(val uncached: Api2) {
val funnelInstance: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.UUID> = ModelCache(uncached.funnelInstance, com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer())
val funnelSummary: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.UUID> = ModelCache(uncached.funnelSummary, com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer())
val otpSecret: ModelCache<com.lightningkite.lightningserver.auth.proof.OtpSecret, com.lightningkite.UUID> = ModelCache(uncached.otpSecret, com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer())
val passwordSecret: ModelCache<com.lightningkite.lightningserver.auth.proof.PasswordSecret, com.lightningkite.UUID> = ModelCache(uncached.passwordSecret, com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer())
val testModel: ModelCache<com.lightningkite.lightningserver.demo.TestModel, com.lightningkite.UUID> = ModelCache(uncached.testModel, com.lightningkite.lightningserver.demo.TestModel.serializer())
val userSession: ModelCache<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningserver.demo.User, com.lightningkite.UUID>, com.lightningkite.UUID> = ModelCache(uncached.userSession, com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.lightningserver.demo.User.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())))
}
