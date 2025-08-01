package com.lightningkite.template.sdk

import com.lightningkite.*
import com.lightningkite.lightningdb.*
import com.lightningkite.kiteui.*
import kotlinx.datetime.*
import com.lightningkite.serialization.*
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.auth.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.*

open class CachedApi2(val uncached: Api2) {
val fcmToken: ModelCache<com.lightningkite.template.FcmToken, kotlin.String> = ModelCache(uncached.fcmToken, com.lightningkite.template.FcmToken.serializer())
val funnelInstance: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.UUID> = ModelCache(uncached.funnelInstance, com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer())
val funnelSummary: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.UUID> = ModelCache(uncached.funnelSummary, com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer())
val otpSecret: ModelCache<com.lightningkite.lightningserver.auth.proof.OtpSecret, com.lightningkite.UUID> = ModelCache(uncached.otpSecret, com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer())
val passwordSecret: ModelCache<com.lightningkite.lightningserver.auth.proof.PasswordSecret, com.lightningkite.UUID> = ModelCache(uncached.passwordSecret, com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer())
val spammyMessage: ModelCache<com.lightningkite.template.SpammyMessage, com.lightningkite.UUID> = ModelCache(uncached.spammyMessage, com.lightningkite.template.SpammyMessage.serializer())
val user: ModelCache<com.lightningkite.template.User, com.lightningkite.UUID> = ModelCache(uncached.user, com.lightningkite.template.User.serializer())
val userSession: ModelCache<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.template.User, com.lightningkite.UUID>, com.lightningkite.UUID> = ModelCache(uncached.userSession, com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.template.User.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())))
}
