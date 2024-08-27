package com.lightningkite.lightningserver

import com.lightningkite.kiteui.navigation.DefaultJson
import kotlinx.serialization.json.Json
import com.lightningkite.kiteui.*
import com.lightningkite.lightningserver.typed.BulkRequest
import com.lightningkite.lightningserver.typed.BulkResponse
import com.lightningkite.uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.typeOf

private class DomainRequestHandler(
    val domain: String,
    val json: Json = DefaultJson,
    val delay: suspend (ms: Long) -> Unit = { ms -> kotlinx.coroutines.delay(ms) }
) {
    inner class TokenHandler(
        val token: (suspend () -> String)?,
        val delay: suspend (ms: Long) -> Unit = { ms -> kotlinx.coroutines.delay(ms) }
    ) {
        var byId = HashMap<String, BulkHandler>()
        var scheduled = false
        fun queue(id: String, bulk: BulkHandler) {
            byId.put(id, bulk)
            if (!scheduled) {
                scheduled = true
                launchGlobal {
                    delay(50)
                    fetch()
                }
            }
        }

        suspend fun fetch() {
            val todo = byId
            byId = HashMap()
            scheduled = false
            connectivityFetch(
                url = "$domain/meta/bulk",
                method = HttpMethod.POST,
                headers = {
                    httpHeaders(listOfNotNull(
                        token?.invoke()?.let { "Authorization" to "Bearer ${it}" }
                    ))
                },
                body = RequestBodyText(
                    json.encodeToString(todo.mapValues { it.value.request }),
                    "application/json"
                )
            ).let { it: RequestResponse ->
                if (!it.ok) {
                    val failed = Exception(it.status.toString() + ": " + it.text())
                    todo.values.forEach { it.response.resumeWithException(failed) }
                } else {
                    val responses = json.decodeFromString<Map<String, BulkResponse>>(it.text())
                    todo.forEach {
                        responses[it.key]?.let { response ->
                            it.value.response.resume(response)
                        } ?: it.value.response.resumeWithException(Exception("Bulk key ${it.key} not found"))
                    }
                }
            }
        }
    }

    val byToken = HashMap<(suspend () -> String)?, TokenHandler>()
    fun token(token: (suspend () -> String)?) = byToken.getOrPut(token) { TokenHandler(token, delay) }
}

private val queuedRequests = HashMap<String, DomainRequestHandler>()

private class BulkHandler(val request: BulkRequest, val response: Continuation<BulkResponse>)

suspend fun <OUT> batchFetch(
    url: String,
    method: HttpMethod = HttpMethod.GET,
    token: (suspend () -> String)? = null,
    bodyJson: String?,
    type: KSerializer<OUT>,
    json: Json = DefaultJson,
): OUT {
    return suspendCoroutineCancellable<BulkResponse> {
        val pathStartsAt = if (url.startsWith("http")) url.indexOf('/', 8) else 0
        val domain = url.substring(0, pathStartsAt)
        val path = url.substring(pathStartsAt)
        val id = uuid().toString()
        val bulk = BulkHandler(
            request = BulkRequest(
                path,
                method = method.name,
                body = bodyJson
            ),
            response = it
        )
        queuedRequests.getOrPut(domain) { DomainRequestHandler(domain) }.token(token).queue(id, bulk)
        return@suspendCoroutineCancellable {}
    }.let { it: BulkResponse ->
        try {
            if (it.error == null && type.descriptor.serialName == Unit.serializer().descriptor.serialName) Unit as OUT
            else if (it.result != null) json.decodeFromString(type, it.result!!)
            else {
                throw LsErrorException(it.error?.http?.toShort() ?: 0.toShort(), it.error ?: LSError(0)).also { e ->
                    Exception("HTTP Failure: $method ${url}", e).report("bulk")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
suspend inline fun <reified OUT> batchFetch(
    url: String,
    method: HttpMethod = HttpMethod.GET,
    noinline token: (suspend () -> String)? = null,
    bodyJson: String?,
    json: Json = DefaultJson,
): OUT = batchFetch(url, method, token, bodyJson, json.serializersModule.serializer(typeOf<OUT>()) as KSerializer<OUT>, json)

class LsErrorException(val status: Short, val error: LSError): IllegalStateException("$status: ${error.message}")
