package com.lightningkite.lightningserver.files

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.fileName
import com.lightningkite.kiteui.models.AudioLocal
import com.lightningkite.kiteui.models.AudioRemote
import com.lightningkite.kiteui.models.AudioSource
import com.lightningkite.kiteui.models.ImageLocal
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.UrlCacheStrategy
import com.lightningkite.kiteui.models.VideoLocal
import com.lightningkite.kiteui.models.VideoRemote
import com.lightningkite.kiteui.models.VideoSource
import com.lightningkite.kiteui.requestCaptureEnvironment
import com.lightningkite.kiteui.requestCaptureSelf
import com.lightningkite.kiteui.views.RContext
import com.lightningkite.services.files.ServerFile
import kotlin.jvm.JvmOverloads

public object LocalFileRegistry {
    public val fileReference: MutableMap<ServerFile, FileReference> = mutableMapOf<ServerFile, FileReference>()
}
@JvmOverloads
public fun ServerFile.asImage(cacheStrategy: UrlCacheStrategy = UrlCacheStrategy.PathOnly): ImageSource =
    LocalFileRegistry.fileReference[this]?.let(::ImageLocal) ?: ImageRemote(this.location, cacheStrategy)
public fun ServerFile.asVideo(): VideoSource = LocalFileRegistry.fileReference[this]?.let(::VideoLocal) ?: VideoRemote(this.location)
public fun ServerFile.asAudio(): AudioSource = LocalFileRegistry.fileReference[this]?.let(::AudioLocal) ?: AudioRemote(this.location)
public suspend fun FileReference.toServerFile(api: ClientUploadEarlyEndpoints): ServerFile? {
    val local = this
    val early = api.uploadFileForRequest()
    val upload = fetch(early.uploadUrl, HttpMethod.PUT, body = local)
    if (!upload.ok) throw IllegalStateException("Upload of ${local.fileName()} failed with status ${upload.status}: ${upload.text()}")
    val remote = ServerFile(api.verifyUploadedFile(early.futureCallToken))
    LocalFileRegistry.fileReference[remote] = local
    return remote
}