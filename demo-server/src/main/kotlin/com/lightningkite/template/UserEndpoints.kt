package com.lightningkite.template

import com.lightningkite.*
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.auth.authOptions
import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.core.*
import com.lightningkite.lightningserver.db.ModelRestEndpoints
import com.lightningkite.lightningserver.db.ModelSerializationInfo
import com.lightningkite.lightningserver.db.modelInfo
import com.lightningkite.lightningserver.db.modelInfoWithDefault
import com.lightningkite.lightningserver.tasks.startupOnce
import com.lightningkite.lightningserver.typed.auth


class UserEndpoints(path: ServerPath) : ServerPathGroup(path) {

    val info = Server.database.modelInfo(
        serialization = ModelSerializationInfo<User, UUID>(),
        authOptions = authOptions<User>(),
        permissions = {
            val allowedRoles = UserRole.entries.filter { it <= auth.role() }
            val admin: Condition<User> =
                if (this.auth.role() >= UserRole.Admin) condition { it.role inside allowedRoles } else Condition.Never
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

    val rest = ModelRestEndpoints(path, info)
//    val socketUpdates = ModelRestUpdatesWebsocket(path, Server.database, info)

    init {
        startupOnce("initAdminUser", Server.database) {
            println("Adding user")
            val email = "joseph+root@lightningkite.com".toEmailAddress()
            info.collection().deleteMany(condition { it.email.eq(email) })
            info.collection().insertOne(
                User(
                    _id = UUID(0L, 10L),
                    email = email,
                    name = "Joseph Root",
                    role = UserRole.Root
                )
            )
        }
    }
}