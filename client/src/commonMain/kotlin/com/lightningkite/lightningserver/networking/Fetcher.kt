package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.RequestBodyText
import com.lightningkite.kiteui.connectivityFetch
import com.lightningkite.kiteui.httpHeaders
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.batchFetch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json


interface Fetcher {
    suspend operator fun <T> invoke(url: String, method: HttpMethod, jsonBody: String?, outSerializer: KSerializer<T>): T
}

class BulkFetcher(val base: String, val json: Json, val token: (suspend () -> String?)) : Fetcher {
    override suspend fun <T> invoke(
        url: String,
        method: HttpMethod,
        jsonBody: String?,
        outSerializer: KSerializer<T>
    ): T {
        return batchFetch("$base/$url", method, token, jsonBody, outSerializer, json)
    }
}

class ConnectivityOnlyFetcher(val base: String, val json: Json, val token: (suspend () -> String?)) : Fetcher {
    override suspend fun <T> invoke(
        url: String,
        method: HttpMethod,
        jsonBody: String?,
        outSerializer: KSerializer<T>
    ): T {
        println("ConnectivityOnlyFetcher: $method $url $jsonBody, ${outSerializer.descriptor.serialName}")
        val token = token()
        println("token: $token")
        return connectivityFetch("$base/$url", method, {
            if (token != null) httpHeaders(
                "Authorization" to token,
                "Accept" to "application/json"
            ) else httpHeaders("Accept" to "application/json")
        }, RequestBodyText(jsonBody ?: "{}", "application/json")).let {
            if (!it.ok) {
                val text = it.text()
                try {
                    val e = json.decodeFromString(LSError.serializer(), text)
                    throw LsErrorException(it.status, e)
                } catch (e: Exception) {
                    throw LsErrorException(it.status, LSError(it.status.toInt(), "Unknown", message = text))
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                if (outSerializer.descriptor.serialName == "kotlin.Unit") return Unit as T
                json.decodeFromString(outSerializer, it.text())
            }
        }
    }
}