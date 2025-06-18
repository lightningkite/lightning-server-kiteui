package com.lightningkite.template.sdk

import kotlinx.browser.window

actual fun getDefaultServerBackend(): ApiOption {
    val host = window.location.hostname
    return when {
//        host.contains("lightningkite") -> ApiOption.Dev
//        host.contains("staging") -> ApiOption.Staging
        host.contains("localhost") -> ApiOption.Local
        else -> ApiOption.Local
//        else -> ApiOption.Production
    }
}