package com.lightningkite.kiteui.monitoring

import com.lightningkite.UUID
import com.lightningkite.kiteui.AppScope
import com.lightningkite.kiteui.Build
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.RequestBodyText
import com.lightningkite.kiteui.RequestResponse
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.userAgent
import com.lightningkite.lightningserver.monitoring.FunnelStart
import com.lightningkite.serialization.UUIDSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.builtins.serializer
import kotlin.math.exp

private suspend fun funnelHit(path: String, content: String): RequestResponse? {
    return try {
        fetch(
            url = "https://monitoring.cs.lightningkite.com/funnelInstance/$path",
            method = HttpMethod.POST,
            body = RequestBodyText(content, "application/json")
        )
    } catch(t: Throwable) {
        t.printStackTrace()
        null
    }
}

//error
//step
//success

class FunnelControl(val id: Deferred<UUID?>) {
    fun error(error: String) = AppScope.async {
        id.await()?.let {
            funnelHit("error/$it", DefaultJson.encodeToString(String.serializer(), error))
        }
    }
    fun step(step: Int) = AppScope.async {
        id.await()?.let {
            funnelHit("step/$it", step.toString())
        }
    }
    fun success() = AppScope.async {
        id.await()?.let {
            funnelHit("success/$it", "{}")
        }
    }
}

object Funnels {
    var namespace: String? = null
    var token: String? = null
    var user: String? = null
    val completableDeferredNull = CompletableDeferred<UUID?>(null)
}
fun funnel(name: String, expirationMinutes: Int = 20): FunnelControl {
    val namespace = Funnels.namespace ?: return FunnelControl(Funnels.completableDeferredNull)
    val token = Funnels.token ?: return FunnelControl(Funnels.completableDeferredNull)
    return FunnelControl(AppScope.async {
        funnelHit("start", DefaultJson.encodeToString(FunnelStart.serializer(), FunnelStart(
            funnel = "${namespace}/$name",
            userAgent = Platform.userAgent,
//            user = Funnels.user,
            version = Build.version,
            token = token,
            expireAfterMinutes = expirationMinutes
        )))?.let {
            if(it.ok) it.text().let { DefaultJson.decodeFromString(UUIDSerializer, it) }
            else null
        }
    })
}