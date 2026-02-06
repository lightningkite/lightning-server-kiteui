// by Claude - JVM stub implementations (FCM not needed for SSR)
package com.lightningkite.lskiteuistarter.utils

actual fun fcmSetup(): Unit {}

actual suspend fun requestNotificationPermissions(): Unit {}

actual suspend fun notificationPermissions(): Boolean? = null
