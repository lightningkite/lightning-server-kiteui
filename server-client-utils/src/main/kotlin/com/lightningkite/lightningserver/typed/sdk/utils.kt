package com.lightningkite.lightningserver.typed.sdk

import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.services.data.ExperimentalLightningServer
import com.lightningkite.services.data.KFile

@OptIn(ExperimentalLightningServer::class)
private class MergedFormat(val formats: List<SDK.Format>): SDK.Format {
    context(_: ServerRuntime)
    override fun write(archive: Archive) =
        formats.forEach { it.write(archive) }
}

public operator fun SDK.Format.plus(other: SDK.Format): SDK.Format = when {
    this is MergedFormat && other is MergedFormat -> MergedFormat(formats + other.formats)
    this is MergedFormat -> MergedFormat(formats + other)
    other is MergedFormat -> MergedFormat(listOf(this) + other.formats)
    else -> MergedFormat(listOf(this, other))
}