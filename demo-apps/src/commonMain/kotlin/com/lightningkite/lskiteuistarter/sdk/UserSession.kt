package com.lightningkite.lskiteuistarter.sdk

import kotlin.uuid.Uuid


class UserSession(
    val api: Api,
    val userId: Uuid,
) : CachedApi(api) {

}
