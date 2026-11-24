package com.lightningkite.lightningserver.files

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.fetch
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
/** Uploads this file through [api]: gets an upload URL, PUTs the file there, and has the server verify it. */
public suspend fun FileReference.toServerFile(api: ClientFileUploadEndpoints): ServerFile {
    val target = api.getFileUploadUrl()
    val response = fetch(target.uploadUrl, HttpMethod.PUT, body = this)
    check(response.ok) { "Upload to ${target.uploadUrl} failed with status ${response.status}" }
    val remote = api.verifyUpload(target.token)
    LocalFileRegistry.fileReference[remote] = this
    return remote
}
