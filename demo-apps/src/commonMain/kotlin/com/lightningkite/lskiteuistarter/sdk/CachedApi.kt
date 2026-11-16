package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.lightningserver.db.*
import kotlinx.serialization.builtins.*
import kotlin.time.Duration.Companion.minutes

open class CachedApi(
	val uncached: Api,
	defaultRefreshPolicy: RefreshPolicy = RefreshPolicy.maxAge(5.minutes),
) {
	open val appReleases = ModelCache(uncached.appRelease, com.lightningkite.lskiteuistarter.AppRelease.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val users = ModelCache(uncached.user, com.lightningkite.lskiteuistarter.User.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val userNestedTypeModels = ModelCache(uncached.user.nestedTypeModel, com.lightningkite.lskiteuistarter.User.NestedTypeModel.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val sessions = ModelCache(uncached.userAuth, com.lightningkite.lightningserver.sessions.Session.serializer(com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()), defaultRefreshPolicy = defaultRefreshPolicy)
	open val totpSecrets = ModelCache(uncached.userAuth.totp, com.lightningkite.lightningserver.sessions.TotpSecret.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val passwordSecrets = ModelCache(uncached.userAuth.password, com.lightningkite.lightningserver.sessions.PasswordSecret.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val fcmTokens = ModelCache(uncached.fcmToken, com.lightningkite.lskiteuistarter.FcmToken.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val clubs = ModelCache(uncached.race.club, com.lightningkite.lskiteuistarter.Club.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val racers = ModelCache(uncached.race.racer, com.lightningkite.lskiteuistarter.Racer.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val documents = ModelCache(uncached.document, com.lightningkite.lskiteuistarter.Document.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
	open val sealedPolymorhphicModels = ModelCache(uncached.sealedPolymorphicModel, com.lightningkite.lskiteuistarter.SealedPolymorhphicModel.serializer(), defaultRefreshPolicy = defaultRefreshPolicy)
}
