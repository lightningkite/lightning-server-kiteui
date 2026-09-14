package com.lightningkite.kiteui.exceptions

import com.lightningkite.kiteui.report
import com.lightningkite.kiteui.views.ElementContext
import com.lightningkite.lightningserver.LsErrorException

public fun ExceptionHandlersTree.installLsError() {
    this += ExceptionToMessage<LsErrorException> {
        it.report()
        ExceptionMessage(
            title = "Error",
            body = it.error.message.takeUnless { it.isBlank() } ?: when (it.status) {
                400 -> "Incorrectly formed information was sent."
                401 -> "You're not authenticated properly."
                403 -> "You're not allowed to do this."
                500 -> "Something's wrong with the server."
                else -> "Got a code ${it.status} from the server."
            }
        )
    }
}