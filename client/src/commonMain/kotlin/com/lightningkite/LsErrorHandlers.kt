package com.lightningkite.kiteui.exceptions

import com.lightningkite.kiteui.exceptions.ExceptionHandlers
import com.lightningkite.kiteui.exceptions.ExceptionMessage
import com.lightningkite.kiteui.exceptions.ExceptionToMessage
import com.lightningkite.kiteui.exceptions.ExceptionToMessages
import com.lightningkite.kiteui.report
import com.lightningkite.lightningserver.LsErrorException

fun ExceptionToMessages.installLsError() {
    this += ExceptionToMessage<LsErrorException> {
        it.report()
        ExceptionMessage(
            title = "Error",
            body = it.error.message.takeUnless { it.isBlank() } ?: when(it.status) {
                400.toShort() -> "Incorrectly formed information was sent."
                401.toShort() -> "You're not authenticated properly."
                403.toShort() -> "You're not allowed to do this."
                500.toShort() -> "Something's wrong with the server."
                else -> "Got a code ${it.status} from the server."
            }
        )
    }
}