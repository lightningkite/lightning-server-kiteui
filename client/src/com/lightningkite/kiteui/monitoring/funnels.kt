package com.lightningkite.kiteui.monitoring

import kotlin.uuid.Uuid
import com.lightningkite.kiteui.*
import com.lightningkite.lightningserver.networking.lightningServer
import com.lightningkite.lightningserver.typed.Fetcher
import com.lightningkite.lightningserver.typed.FunnelStart
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

private suspend fun <I, T> funnelHit(path: String, inSerializer: KSerializer<I>, body: I, outSerializer: KSerializer<T>): T? {
    val fetcher = Funnels.fetcher ?: return null
    return try {
        suppressConnectivityIssues {
            fetcher(
                url = "meta/funnels/$path",
                method = HttpMethod.POST.lightningServer,
                inSerializer = inSerializer,
                body = body,
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

public class FunnelControl(public val id: Deferred<Uuid?>) {
    public fun error(error: String): Deferred<Unit?> = AppScope.async {
        id.await()?.let {
            funnelHit("error/$it", String.serializer(), error, Unit.serializer())
        }
    }
    public fun step(step: Int): Deferred<Unit?> = AppScope.async {
        id.await()?.let {
            funnelHit("step/$it", Int.serializer(), step, Unit.serializer())
        }
    }
    public fun success(): Deferred<Unit?> = AppScope.async {
        id.await()?.let {
            funnelHit("success/$it", Unit.serializer(), Unit, Unit.serializer())
        }
    }
}

public object Funnels {
    public var fetcher: Fetcher? = null
    public val completableDeferredNull: CompletableDeferred<Uuid?> = CompletableDeferred<Uuid?>(null)
}
public fun funnel(name: String, expirationMinutes: Int = 20): FunnelControl {
    return FunnelControl(AppScope.async {
        funnelHit("start",  FunnelStart.serializer(), FunnelStart(
            funnel = name,
            userAgent = Platform.userAgent,
            version = Build.version,
            expireAfterMinutes = expirationMinutes
        ), Uuid.serializer())
    })
}