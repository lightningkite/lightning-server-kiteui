package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.StartupTask
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.definition.generalSettings
import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lightningserver.typed.AuthAccess
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lightningserver.typed.startupOnce
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.lskiteuistarter._id
import com.lightningkite.lskiteuistarter.email
import com.lightningkite.lskiteuistarter.role
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.ModelPermissions
import com.lightningkite.services.database.condition
import com.lightningkite.services.database.eq
import com.lightningkite.services.database.insertOne
import com.lightningkite.services.database.inside
import com.lightningkite.services.database.or
import com.lightningkite.services.database.updateRestrictions
import kotlin.uuid.Uuid

object SealedPolymorphicModel : ServerBuilder() {


    val info = Server.database.modelInfo(
        auth = UserAuth.require(),
        permissions = { permissions(this) },
    )
    val rest = path include ModelRestEndpoints(info)

    context(server: ServerRuntime)
    suspend fun permissions(auth: AuthAccess<User>): ModelPermissions<SealedPolymorhphicModel> {
        return if (auth.userRole() < UserRole.Admin) {
            ModelPermissions(read = Condition.Always)
        } else {
            ModelPermissions.allowAll()
        }
    }
}