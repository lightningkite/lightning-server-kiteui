package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.ForbiddenException
import com.lightningkite.lightningserver.NotFoundException
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.pathing.arg1
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.lskiteuistarter._id
import com.lightningkite.lskiteuistarter.user
import com.lightningkite.services.database.ModelPermissions
import com.lightningkite.services.database.condition
import com.lightningkite.services.database.deleteOneById
import com.lightningkite.services.database.eq
import com.lightningkite.services.database.get
import com.lightningkite.services.database.modification
import com.lightningkite.services.database.or
import com.lightningkite.services.notifications.Notification
import com.lightningkite.services.notifications.NotificationData

object FcmTokenEndpoints : ServerBuilder() {
    val info = Server.database.modelInfo(
        auth = UserAuth.require(),
        permissions = {
            val admin = condition<FcmToken>(auth.userRole() >= UserRole.Admin)
            val mine = condition<FcmToken> { it.user eq auth.id }
            ModelPermissions(
                create = admin or mine,
                read = admin or mine,
                update = admin or mine,
                delete = admin or mine,
            )
        })
    val rest = path include ModelRestEndpoints(info)

    val registerEndpoint = path.path("register").post bind ApiHttpHandler(
        summary = "Register Token",
        auth = UserAuth.require(),
        implementation = { id: String ->
            info.table().upsertOne(
                condition { it._id eq id },
                modification { it.user assign auth.id },
                FcmToken(id, auth.id, userAgent = request.headers["User-Agent"]?.root ?: "?")
            )
        }
    )
    val clearEndpoint = rest.detailPath.path("clear").post bind ApiHttpHandler(
        summary = "Clear Token",
        auth = noAuth,
        implementation = { _: Unit ->
            info.table().deleteOneById(request.arg1)
        }
    )

    val testEndpoint = rest.detailPath.path("test").post bind ApiHttpHandler(
        summary = "Test In-App Notifications",
        auth = UserAuth.require(),
        implementation = { _: Unit ->
            val token = info.table().get(this.request.arg1) ?: throw NotFoundException("Token not found in database.")
            if (token.user != auth.untypedId && auth.userRole() < UserRole.Admin) throw ForbiddenException("You don't own this token.")
            Server.notifications().send(
                listOf(token._id), NotificationData(
                    notification = Notification(
                        title = "Test Notification",
                        body = "This is the test notification you requested.",
                        link = Server.webUrl()
                    )
                )
            )
            "Notification sent"
        }
    )
}