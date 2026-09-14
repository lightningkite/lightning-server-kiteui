// Shared setup for the forms-engine demo pages under this package.
//
// com.lightningkite.kiteui.forms is a large declarative form/renderer engine that nothing else
// in demo-apps exercises. These pages render and edit real, persisted models through it: a plain
// data class (AppRelease), the sealed-polymorphic showcase model (SealedPolymorhphicModel), and
// Document, a fixture built to carry a foreign key, file uploads, and Set/Map fields at once.
package com.lightningkite.lskiteuistarter.forms

import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.TypeInfo
import com.lightningkite.kiteui.forms.defaults
import com.lightningkite.lightningserver.files.toServerFile
import com.lightningkite.lskiteuistarter.User
import com.lightningkite.lskiteuistarter.sdk.UserSession
import com.lightningkite.reactive.context.awaitOnce

/**
 * A [FormModule] wired for this app: file uploads go through the server's early-upload endpoint,
 * and `@References(User::class)` fields resolve through the session's user cache so
 * [com.lightningkite.kiteui.forms.ForeignKeyRenderer] can search, display, and link to users.
 */
fun UserSession.formModule(): FormModule = FormModule().apply {
    defaults()
    fileUpload = { fileRef ->
        fileRef.toServerFile(api.uploadEarlyEndpoint)
            ?: throw IllegalStateException("Upload failed: server did not return a file reference")
    }
    typeInfo = { typeName ->
        if (typeName == User.serializer().descriptor.serialName) {
            TypeInfo(
                serializer = User.serializer(),
                cache = { users },
                page = { id -> { com.lightningkite.lskiteuistarter.UserDetailPage(id.toString()) } },
                renderToString = { id -> users.item(id).awaitOnce()?.name ?: "Unknown user" },
            )
        } else null
    }
}
