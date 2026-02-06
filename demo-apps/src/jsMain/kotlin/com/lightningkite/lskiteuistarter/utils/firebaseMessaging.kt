@file:JsModule("firebase/messaging")
@file:JsNonModule
@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)

package com.lightningkite.lskiteuistarter.utils

import kotlin.js.Promise

external interface Messaging

external interface GetTokenOptions {
    var vapidKey: String?
        get() = definedExternally
        set(value) = definedExternally
//    var serviceWorkerRegistration: ServiceWorkerRegistration?
}

external interface NotificationPayload {
    var title: String?
        get() = definedExternally
        set(value) = definedExternally
    var body: String?
        get() = definedExternally
        set(value) = definedExternally
    var image: String?
        get() = definedExternally
        set(value) = definedExternally
    var icon: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface MessagePayload {
    var data: Map<String, String>?
        get() = definedExternally
        set(value) = definedExternally
    var notification: NotificationPayload?
        get() = definedExternally
        set(value) = definedExternally
    var fcmOptions: String?
        get() = definedExternally
        set(value) = definedExternally
    var from: String?
        get() = definedExternally
        set(value) = definedExternally
    var collapseKey: String?
        get() = definedExternally
        set(value) = definedExternally
    var messageId: String?
        get() = definedExternally
        set(value) = definedExternally
}

external fun getMessaging(app: FirebaseApp? = definedExternally): Messaging

external fun getToken(messaging: Messaging, options: GetTokenOptions? = definedExternally): Promise<String>

external fun onMessage(messaging: Messaging, nextOrObserver: (MessagePayload) -> Unit)

