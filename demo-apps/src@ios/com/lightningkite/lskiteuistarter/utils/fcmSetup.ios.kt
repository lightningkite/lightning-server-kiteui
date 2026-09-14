package com.lightningkite.lskiteuistarter.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

actual fun fcmSetup() {
}

actual suspend fun requestNotificationPermissions() {
    println("requestNotificationPermissions: started")
    val result = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings: UNNotificationSettings? ->
                println("requestNotificationPermissions: ${settings?.authorizationStatus}")
                when {
                    settings == null -> cont.resume(UNAuthorizationStatusDenied)
                    settings.authorizationStatus == UNAuthorizationStatusNotDetermined -> {
                        val options = listOf(UNAuthorizationOptionSound, UNAuthorizationOptionBadge)
                        UNUserNotificationCenter.currentNotificationCenter()
                            .requestAuthorizationWithOptions(options.reduce { acc, it -> acc or it }) { approved, e ->
                                dispatch_async(dispatch_get_main_queue()) {
                                    cont.resume(
                                        if (approved) {
                                            UNAuthorizationStatusAuthorized
                                        } else UNAuthorizationStatusDenied
                                    )
                                }
                            }
                    }

                    else -> cont.resume(settings.authorizationStatus)
                }
            }
        println("requestNotificationPermissions: sent")
    }
    println("requestNotificationPermissions: status ${decodeStatus[result] ?: result.toString()}")
    if (result == UNAuthorizationStatusAuthorized) {
        MyMessaging.enableAutoInit()
        fcmSetup()
    }
}

private val decodeStatus = mapOf(
    UNAuthorizationStatusAuthorized to "UNAuthorizationStatusAuthorized",
    UNAuthorizationStatusDenied to "UNAuthorizationStatusDenied",
    UNAuthorizationStatusEphemeral to "UNAuthorizationStatusEphemeral",
    UNAuthorizationStatusNotDetermined to "UNAuthorizationStatusNotDetermined",
    UNAuthorizationStatusProvisional to "UNAuthorizationStatusProvisional",
)

object MyMessaging {
    var enableAutoInit: ()->Unit = {}
}

actual suspend fun notificationPermissions(): Boolean? {
    println("notificationPermissions: started")
    return suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler {
                dispatch_async(dispatch_get_main_queue()) {
                    cont.resume(it?.authorizationStatus?.let {
                        println("notificationPermissions: ${decodeStatus[it] ?: it.toString()}")
                        when (it) {
                            UNAuthorizationStatusAuthorized -> true
                            UNAuthorizationStatusDenied -> false
                            else -> null
                        }
                    }.also { if (it == true) MyMessaging.enableAutoInit() })
                }
            }
    }
}