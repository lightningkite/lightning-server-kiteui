package com.lightningkite.lskiteuistarter.utils

import com.lightningkite.kiteui.Log
import com.lightningkite.lightningserver.*
import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.services.data.*
import com.lightningkite.services.database.*
import com.lightningkite.services.files.*
import com.lightningkite.lskiteuistarter.fcmToken
import com.lightningkite.lskiteuistarter.utils.*
import kotlin.coroutines.resume
import kotlin.uuid.Uuid
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.notifications.*
import org.w3c.workers.*

actual fun fcmSetup(): Unit {
    try {
        val firebaseAppOptions: dynamic = object {}
        // TODO: Replace with your own Firebase project information.
        firebaseAppOptions["apiKey"] = "AIzaSyCL8CNiVYE-JoF3JxJffpgHjHV8wfMdqTY"
        firebaseAppOptions["authDomain"] = "ls-kiteui-starter-project.firebaseapp.com"
        firebaseAppOptions["projectId"] = "ls-kiteui-starter-project"
        firebaseAppOptions["storageBucket"] = "ls-kiteui-starter-project.firebasestorage.app"
        firebaseAppOptions["messagingSenderId"] = "763812266707"
        firebaseAppOptions["appId"] = "1:763812266707:web:3ee91ef35b45b8e70c0a26"

        val app = initializeApp(firebaseAppOptions.unsafeCast<FirebaseOptions>())
        val messaging = getMessaging(app)

        window.navigator.serviceWorker
            .register("firebase-messaging-sw.js", RegistrationOptions(scope = "./"))
            .then { serviceWorkerReg ->

                val serviceWorker = serviceWorkerReg.installing ?: serviceWorkerReg.waiting ?: serviceWorkerReg.active
                Log.log("Service worker state: ${serviceWorker?.state}")
                if(serviceWorker?.state == ServiceWorkerState.ACTIVATED) {
                    finishSetup(serviceWorkerReg, messaging)
                } else {
                    serviceWorker?.addEventListener("statechange", { e ->
                        val target = e.target as ServiceWorker
                        Log.log("Service worker state: ${target.state}")
                        if(target.state == ServiceWorkerState.ACTIVATED)
                            finishSetup(serviceWorkerReg, messaging)
                    })
                }


            }.catch {
                it.printStackTrace()
            }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}


private fun finishSetup(
    serviceWorker: ServiceWorkerRegistration,
    messaging: Messaging
) {
    val messagingOptions: dynamic = object {}
    // TODO: Replace with your own Firebase project information.
    messagingOptions["vapidKey"] = "BO5UdFoJvIrw2OLBkB9sas8i2RANMVk0-QUYKJz9fHRVYuFsAEZE8Pzhhl_VW3Wri-aZ5O1cm2Cw8ZLljgJ9jOU"
    messagingOptions["serviceWorkerRegistration"] = serviceWorker

    getToken(
        messaging,
        messagingOptions.unsafeCast<GetTokenOptions>()
    ).then {
        fcmToken.value = it
    }.catch {
        it.printStackTrace()
    }

    onMessage(messaging) { notification ->
        serviceWorker.showNotification(
            notification.notification?.title ?: "Notification",
            NotificationOptions(
                body = notification.notification?.body,
                image = notification.notification?.image,
                icon = notification.notification?.icon,
            )
        )
            .catch { e ->
                console.error("Error sending notificaiton to user", e);
            }
        serviceWorker.update()
    }
    Log.info("FCM Registration complete.")
}

actual suspend fun requestNotificationPermissions(): Unit {
    val result = suspendCancellableCoroutine { cont ->
        Notification.requestPermission { result ->
            cont.resume(result)
        }
    }
    if (result == NotificationPermission.GRANTED) fcmSetup()
}

actual suspend fun notificationPermissions(): Boolean? {
    return if (js("'Notification' in window").unsafeCast<Boolean>())
        when (Notification.permission) {
            NotificationPermission.GRANTED -> true
            NotificationPermission.DENIED -> false
            else -> null
        } else false
}
