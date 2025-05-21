package com.lightningkite.serialization

import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningserver.StringArrayFormat
import kotlinx.serialization.encodeToString

val DefaultStringArrayFormat by lazy { StringArrayFormat(DefaultJson.serializersModule) }
inline fun <reified T> T.urlifyToCommaString() = DefaultStringArrayFormat.encodeToString(this)