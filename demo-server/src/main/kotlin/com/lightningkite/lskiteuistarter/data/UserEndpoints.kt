package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.StartupTask
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.definition.generalSettings
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
import com.lightningkite.toEmailAddress
import kotlin.uuid.Uuid

object UserEndpoints : ServerBuilder() {

    val info = Server.database.modelInfo(
        auth = UserAuth.require(),
        permissions = {
            val allowedRoles = UserRole.entries.filter { it <= auth.userRole() }
            val admin: Condition<User> =
                if (this.auth.userRole() >= UserRole.Admin) condition { it.role inside allowedRoles } else Condition.Never
            val self = condition<User> { it._id eq auth.id }
            ModelPermissions(
                create = admin,
                read = admin or self,
                update = admin or self,
                updateRestrictions = updateRestrictions {
                    it.role.requires(admin) { it.inside(allowedRoles) }
                },
                delete = admin or self,
            )
        }
    )

    val rest = path include ModelRestEndpoints(info)
//    val socketUpdates = ModelRestUpdatesWebsocket(path, Server.database, info)

    val initAdminUser = path.path("initAdminUser") bind startupOnce(Server.database) {
        println("Adding user")
        val email = "joseph+root@lightningkite.com".toEmailAddress()
        info.table().deleteMany(condition { it.email.eq(email) })
        info.table().insertOne(
            User(
                _id = Uuid.Companion.fromLongs(0L, 10L),
                email = email,
                name = "Joseph Root",
                role = UserRole.Root
            )
        )
    }

    // Debug admin token output - only when debug mode is enabled in settings
    val debugAdminToken = path.path("debug-admin-token") bind StartupTask {
        if (generalSettings().debug) {
            println("Admin token: '${UserAuth.session.createSession(Uuid.fromLongs(0L, 10L)).second}'")
        }
    }
}