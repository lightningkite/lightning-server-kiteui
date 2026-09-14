package com.lightningkite.lskiteuistarter.utils


expect fun fcmSetup(): Unit

expect suspend fun requestNotificationPermissions(): Unit

expect suspend fun notificationPermissions(): Boolean?
