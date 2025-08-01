package com.lightningkite.template

import com.lightningkite.lightningserver.auth.Authentication
import com.lightningkite.lightningserver.auth.authOptions
import com.lightningkite.lightningserver.auth.authRequired
import com.lightningkite.lightningserver.auth.noAuth
import com.lightningkite.lightningserver.auth.user
import com.lightningkite.lightningserver.cache.CacheSettings
import com.lightningkite.lightningserver.core.ServerPath
import com.lightningkite.lightningserver.core.ServerPathGroup
import com.lightningkite.lightningserver.db.DatabaseSettings
import com.lightningkite.lightningserver.email.EmailSettings
import com.lightningkite.lightningserver.http.HttpResponse
import com.lightningkite.lightningserver.http.get
import com.lightningkite.lightningserver.http.handler
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.meta.metaEndpoints
import com.lightningkite.lightningserver.notifications.NotificationSettings
import com.lightningkite.lightningserver.settings.setting
import com.lightningkite.lightningserver.typed.api
import com.lightningkite.lightningserver.websocket.MultiplexWebSocketHandler
import com.lightningkite.lightningserver.websocket.websocket
import com.lightningkite.prepareModelsServerCore

object Server: ServerPathGroup(ServerPath.root) {

    // Settings
    val cache = setting("cache", CacheSettings())
    val database = setting("database", DatabaseSettings())
    val email = setting("email", EmailSettings())
    val notifications = setting("notifications", default = NotificationSettings("console"))
    val webUrl = setting("webUrl", "http://localhost:8080")

    init{
        // Auth keys
        RoleCacheKey

        // Prepare models
        prepareModelsDemoShared()
        prepareModelsServerCore()
        com.lightningkite.prepareModelsShared()

        // Authentication level aliases
        Authentication.isDeveloper = authRequired<User> {
            it.role() >= UserRole.Developer
        }
        Authentication.isSuperUser = authRequired<User> {
            it.role() >= UserRole.Root
        }
        Authentication.isAdmin = authRequired<User> {
            it.role() >= UserRole.Admin
        }
    }

    // Endpoints, tasks, and schedules

    val root = get.handler {
        HttpResponse.plainText("Welcome to Lightning Server!")
    }

    val example = path("example-endpoint").get.api(
        summary = "Example Endpoint",
        authOptions = noAuth,
        implementation = { _: Unit -> 42 }
    )
    val example2 = path("example-endpoint").post.api(
        summary = "Example Endpoint",
        authOptions = authOptions<User>(),
        implementation = { number: Int -> number + 42 }
    )

    val users = UserEndpoints(path("users"))
    val auth = AuthenticationEndpoints(path("auth"))
    val fcmTokens = FcmTokenEndpoints(path("fcmTokens"))
    val spammyMessages = SpammyMessageEndpoints(path("spammyMessages"))

    val multiplex = path("multiplex").websocket(MultiplexWebSocketHandler(cache))
    val meta = path("meta").metaEndpoints()
}