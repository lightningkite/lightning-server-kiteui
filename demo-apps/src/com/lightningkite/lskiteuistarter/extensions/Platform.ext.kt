package com.lightningkite.lskiteuistarter.extensions

import com.lightningkite.kiteui.Platform
import com.lightningkite.lskiteuistarter.AppPlatform


fun Platform.toAppPlatform(): AppPlatform = when (this) {
    Platform.iOS -> AppPlatform.iOS
    Platform.Android -> AppPlatform.Android
    Platform.Web -> AppPlatform.Web
    Platform.Desktop -> AppPlatform.Desktop
}

