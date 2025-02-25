package com.lightningkite.kiteui.monitoring

import com.lightningkite.UUID
import com.lightningkite.kiteui.AppScope
import com.lightningkite.kiteui.Build
import com.lightningkite.kiteui.HttpHeaders
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.RequestBodyText
import com.lightningkite.kiteui.RequestResponse
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.httpHeaders
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.suppressConnectivityIssues
import com.lightningkite.kiteui.userAgent
import com.lightningkite.lightningserver.monitoring.FunnelStart
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.serialization.UUIDSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.exp

private suspend fun <T> funnelHit(path: String, content: String, outSerializer: KSerializer<T>): T? {
    val fetcher = Funnels.fetcher ?: return null
    return try {
        suppressConnectivityIssues {
            fetcher.invoke(
                url = "meta/funnels/$path",
                method = HttpMethod.POST,
                jsonBody = content,
                outSerializer = outSerializer
            )
        }
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
            funnelHit("error/$it", DefaultJson.encodeToString(String.serializer(), error), Unit.serializer())
        }
    }
    fun step(step: Int) = AppScope.async {
        id.await()?.let {
            funnelHit("step/$it", step.toString(), Unit.serializer())
        }
    }
    fun success() = AppScope.async {
        id.await()?.let {
            funnelHit("success/$it", "{}", Unit.serializer())
        }
    }
}

object Funnels {
    var fetcher: Fetcher? = null
    val completableDeferredNull = CompletableDeferred<UUID?>(null)
}
fun funnel(name: String, expirationMinutes: Int = 20): FunnelControl {
    return FunnelControl(AppScope.async {
        funnelHit("start",  DefaultJson.encodeToString(FunnelStart.serializer(), FunnelStart(
            funnel = name,
            userAgent = Platform.userAgent,
            version = Build.version,
            expireAfterMinutes = expirationMinutes
        )), UUIDSerializer)
    })
}