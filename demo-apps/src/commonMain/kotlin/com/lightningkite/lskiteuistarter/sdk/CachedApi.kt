package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.lightningserver.db.*
import kotlinx.serialization.builtins.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class CachedApi(
	val uncached: Api,
	defaultMaxAge: Duration = Duration.INFINITE,
	defaultPullFrequency: Duration = 5.minutes,
) {
	open val appReleases = ModelCache(uncached.appRelease, com.lightningkite.lskiteuistarter.AppRelease.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val users = ModelCache(uncached.user, com.lightningkite.lskiteuistarter.User.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val userNestedTypeModels = ModelCache(uncached.user.nestedTypeModel, com.lightningkite.lskiteuistarter.User.NestedTypeModel.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val sessions = ModelCache(uncached.userAuth, com.lightningkite.lightningserver.sessions.Session.serializer(com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val totpSecrets = ModelCache(uncached.userAuth.totp, com.lightningkite.lightningserver.sessions.TotpSecret.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val passwordSecrets = ModelCache(uncached.userAuth.password, com.lightningkite.lightningserver.sessions.PasswordSecret.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val fcmTokens = ModelCache(uncached.fcmToken, com.lightningkite.lskiteuistarter.FcmToken.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val clubs = ModelCache(uncached.race.club, com.lightningkite.lskiteuistarter.Club.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val racers = ModelCache(uncached.race.racer, com.lightningkite.lskiteuistarter.Racer.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val documents = ModelCache(uncached.document, com.lightningkite.lskiteuistarter.Document.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
	open val sealedPolymorhphicModels = ModelCache(uncached.sealedPolymorphicModel, com.lightningkite.lskiteuistarter.SealedPolymorhphicModel.serializer(), defaultMaxAge = defaultMaxAge, defaultPullFrequency = defaultPullFrequency)
}
