package com.lightningkite.serialization

import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.services.data.StringArrayFormat
import kotlinx.serialization.encodeToString

public val DefaultStringArrayFormat: StringArrayFormat by lazy { StringArrayFormat(DefaultJson.serializersModule) }
public inline fun <reified T> T.urlifyToCommaString(): String = DefaultStringArrayFormat.encodeToString(this)