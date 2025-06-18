package com.lightningkite.template

import com.lightningkite.UUID
import com.lightningkite.lightningdb.ModelPermissions
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningdb.collection
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.deleteOneById
import com.lightningkite.lightningdb.eq
import com.lightningkite.lightningdb.get
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningdb.or
import com.lightningkite.lightningdb.withPermissions
import com.lightningkite.lightningserver.auth.authOptions
import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.noAuth
import com.lightningkite.lightningserver.core.ServerPath
import com.lightningkite.lightningserver.core.ServerPathGroup
import com.lightningkite.lightningserver.db.ModelRestEndpoints
import com.lightningkite.lightningserver.db.ModelSerializationInfo
import com.lightningkite.lightningserver.db.modelInfoWithDefault
import com.lightningkite.lightningserver.exceptions.ForbiddenException
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.notifications.Notification
import com.lightningkite.lightningserver.notifications.NotificationData
import com.lightningkite.lightningserver.typed.api
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.path1
import com.lightningkite.lightningserver.typed.post
import io.ktor.server.plugins.NotFoundException
import kotlin.text.get

class FcmTokenEndpoints(path: ServerPath) : ServerPathGroup(path) {
    val info = modelInfoWithDefault<User, FcmToken, kotlin.String>(
        serialization = ModelSerializationInfo(),
        authOptions = authOptions<User>(),
        getBaseCollection = { Server.database().collection() },
        forUser = {
            val admin = condition<FcmToken>((auth.role() ?: UserRole.NoOne) >= UserRole.Admin)
            val mine = condition<FcmToken> { it.user eq auth.id }
            it.withPermissions(
                ModelPermissions(
                    create = admin or mine,
                    read = admin or mine,
                    update = admin or mine,
                    delete = admin or mine,
                )
            )
        },
        defaultItem = { FcmToken("", UUID.random()) }
    )
    val rest = ModelRestEndpoints(path, info)

    val registerEndpoint = path.path("register").post.api(
        summary = "Register Token",
        authOptions = authOptions<User>(),
        implementation = { id: String ->
            info.collection().upsertOne(
                condition { it._id eq id },
                modification { it.user assign auth.id },
                FcmToken(id, auth.id, userAgent = rawRequest?.headers["User-Agent"] ?: "?")
            )
        }
    )
    val clearEndpoint = rest.detailPath.path("clear").post.api(
        summary = "Clear Token",
        authOptions = noAuth,
        implementation = { _: Unit ->
            info.collection().deleteOneById(path1)
        }
    )

    val testEndpoint = rest.detailPath.path("test").post.api(
        summary = "Test In-App Notifications",
        authOptions = authOptions<User>(),
        implementation = { _: Unit ->
            val token = info.collection().get(path1) ?: throw NotFoundException()
            if(token.user != auth.id && (auth.role() ?: UserRole.NoOne) < UserRole.Admin) throw ForbiddenException("You don't own this token.")
            Server.notifications().send(
                listOf(token._id), NotificationData(
                    notification = Notification(
                        title = "Test Notification",
                        body = "This is the test notification you requested.",
                        link = Server.webUrl() + "/profile"
                    )
                )
            )
            "Notification sent"
        }
    )
}

