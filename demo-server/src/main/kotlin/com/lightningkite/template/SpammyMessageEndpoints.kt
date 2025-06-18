package com.lightningkite.template

import com.lightningkite.UUID
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.ModelPermissions
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningdb.collection
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.deleteOneById
import com.lightningkite.lightningdb.eq
import com.lightningkite.lightningdb.get
import com.lightningkite.lightningdb.insertOne
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningdb.or
import com.lightningkite.lightningdb.withPermissions
import com.lightningkite.lightningserver.auth.authOptions
import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.noAuth
import com.lightningkite.lightningserver.core.ServerPath
import com.lightningkite.lightningserver.core.ServerPathGroup
import com.lightningkite.lightningserver.db.ModelRestEndpoints
import com.lightningkite.lightningserver.db.ModelRestUpdatesWebsocket
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
import com.lightningkite.now
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.text.get
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SpammyMessageEndpoints(path: ServerPath) : ServerPathGroup(path) {
    val info = modelInfoWithDefault<User, SpammyMessage, UUID>(
        serialization = ModelSerializationInfo<SpammyMessage, UUID>(),
        authOptions = authOptions<User>(),
        getBaseCollection = { Server.database().collection() },
        forUser = {
            val admin = condition<SpammyMessage>((auth.role() ?: UserRole.NoOne) >= UserRole.Admin)
            val mine = Condition.Always
            it.withPermissions(
                ModelPermissions(
                    create = admin or mine,
                    read = admin or mine,
                    update = admin or mine,
                    delete = admin or mine,
                )
            )
        },
        defaultItem = { SpammyMessage(content = "") }
    )
    val rest = ModelRestEndpoints(path, info)
    val socketUpdates = ModelRestUpdatesWebsocket(path, info)

    val doSpam = path.path("spam").post.api(
        authOptions = noAuth,
        summary = "Spamalot",
        implementation = { _: Unit ->
            var endAt = now() + 30.seconds
            while(now() < endAt) {
                delay(1000.milliseconds)
                info.collection().insertOne(SpammyMessage(content = Random.nextInt().toString()))
            }
        }
    )
}

