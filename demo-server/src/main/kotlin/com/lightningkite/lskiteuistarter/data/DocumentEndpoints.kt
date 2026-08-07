package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lskiteuistarter.Document
import com.lightningkite.lskiteuistarter.Server
import com.lightningkite.lskiteuistarter.UserAuth
import com.lightningkite.services.database.ModelPermissions

/**
 * CRUD for [Document], the forms-engine demo's fixture model. No sensitive data lives here, so
 * any signed-in demo user gets full access - the point is exercising the forms engine and
 * ModelCache write path, not modeling a real permission scheme.
 */
object DocumentEndpoints : ServerBuilder() {
    val info = Server.database.modelInfo(
        auth = UserAuth.require(),
        tableName = "Document",
        permissions = { ModelPermissions.allowAll<Document>() },
    )
    val rest = path include ModelRestEndpoints(info)
}
