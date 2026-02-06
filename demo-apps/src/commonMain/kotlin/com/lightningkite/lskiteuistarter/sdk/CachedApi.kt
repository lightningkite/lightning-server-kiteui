package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.lightningserver.db.ModelCache
import kotlinx.serialization.builtins.serializer

open class CachedApi(val uncached: Api) {
    open val appReleases = ModelCache(uncached.appRelease, com.lightningkite.lskiteuistarter.AppRelease.serializer())
    open val users = ModelCache(uncached.user, com.lightningkite.lskiteuistarter.User.serializer())
    open val sessions = ModelCache(
        uncached.userAuth,
        com.lightningkite.lightningserver.sessions.Session.serializer(
            com.lightningkite.lskiteuistarter.User.serializer(),
            kotlin.uuid.Uuid.serializer()
        )
    )
    open val totpSecrets =
        ModelCache(uncached.userAuth.totp, com.lightningkite.lightningserver.sessions.TotpSecret.serializer())
    open val passwordSecrets =
        ModelCache(uncached.userAuth.password, com.lightningkite.lightningserver.sessions.PasswordSecret.serializer())
    open val fcmTokens = ModelCache(uncached.fcmToken, com.lightningkite.lskiteuistarter.FcmToken.serializer())
}
