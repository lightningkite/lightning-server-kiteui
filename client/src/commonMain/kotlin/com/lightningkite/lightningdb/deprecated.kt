package com.lightningkite.lightningdb

import com.lightningkite.kiteui.Console
import com.lightningkite.kiteui.RetryWebsocket
import kotlinx.serialization.json.Json

fun multiplexSocket(
    url: String,
    path: String,
    params: Map<String, List<String>>,
    json: Json,
    pingTime: Long = 30_000L,
    log: Console? = null
): RetryWebsocket = com.lightningkite.lightningserver.multiplexSocket(
    url = url,
    path = path,
    params = params,
    json = json,
    pingTime = pingTime,
    log = log
)