package com.lightningkite.lskiteuistarter.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lightningkite.kiteui.KiteUiActivity
import com.lightningkite.kiteui.PlatformStorage
import com.lightningkite.kiteui.views.AndroidAppContext
import com.lightningkite.lightningserver.*
import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.services.data.*
import com.lightningkite.services.database.*
import com.lightningkite.services.files.*
import com.lightningkite.lskiteuistarter.fcmToken
import kotlin.coroutines.resume
import kotlin.uuid.Uuid
import kotlinx.coroutines.suspendCancellableCoroutine


actual fun fcmSetup(): Unit {
    FirebaseMessaging.getInstance().token.addOnCompleteListener {
        it.result?.let {
            fcmToken.value = it
        }
    }
    val notificationManager =
        AndroidAppContext.applicationCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.cancelAll()

    notificationManager.createNotificationChannel(
        NotificationChannel(
            "default",
            "Default",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
    )
}

actual suspend fun requestNotificationPermissions() {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val result = suspendCancellableCoroutine<KiteUiActivity.PermissionResult> { cont ->
            AndroidAppContext.requestPermissions(android.Manifest.permission.POST_NOTIFICATIONS, onResult = {
                PlatformStorage.set("askedNotificationPermissions", "true")
                cont.resume(it)
            })
        }
        if (result.accepted) {
            fcmSetup()
        }
    }

}

actual suspend fun notificationPermissions(): Boolean? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (PlatformStorage.get("askedNotificationPermissions") == "true")
            AndroidAppContext.applicationCtx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else
            null
    } else true
}

class FCMService : FirebaseMessagingService() {

    companion object {
        const val FROM_NOTIFICATION: String = "fromNotification"
    }

    override fun onNewToken(token: String) {
        fcmToken.value = token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            message.notification?.let { notification ->
                val meta = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
                val builder =
                    NotificationCompat.Builder(
                        this,
                        notification.channelId
                            ?: meta.getString(
                                "com.google.firebase.messaging.default_notification_channel_id"
                            ) ?: "default"
                    )
//                builder.setSmallIcon(icon)
                meta.getInt("com.google.firebase.messaging.default_notification_icon", 0)
                    .takeUnless { it == 0 }
                    ?.let { builder.setSmallIcon(it) }
                meta.getInt("com.google.firebase.messaging.default_notification_color", 0)
                    .takeUnless { it == 0 }
                    ?.let { builder.setColor(it) }
                notification.title?.let { it -> builder.setContentTitle(it) }
                notification.body?.let { it -> builder.setContentText(it) }

                builder.setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        packageManager.getLaunchIntentForPackage(packageName)!!.apply {
                            this.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            for ((key, value) in message.data) {
                                this.putExtra(key, value)
                            }
                            this.putExtra(FROM_NOTIFICATION, true)
                        },
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_ONE_SHOT
                    )
                )
                notification.sound?.let { Uri.parse(it) }?.let { builder.setSound(it) }
                notification.vibrateTimings?.let { builder.setVibrate(it) }
                notification.notificationPriority?.let { builder.setPriority(it) }
                builder.setAutoCancel(true)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                NotificationManagerCompat.from(this)
                    .notify(notification.tag?.hashCode() ?: message.hashCode(), builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}