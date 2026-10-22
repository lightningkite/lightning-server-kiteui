package com.lightningkite.optics

import com.lightningkite.kiteui.views.direct.PhoneNumberFormat
import com.lightningkite.services.data.EmailAddress
import com.lightningkite.services.data.PhoneNumber
import com.lightningkite.reactive.core.MutableRemember
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.lensing.validation.Issue
import com.lightningkite.reactive.lensing.validation.IssueNode
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.services.data.toEmailAddress
import com.lightningkite.services.data.toPhoneNumber
import kotlin.jvm.JvmName

/**
 * A lens for types that must exist in a valid form before being set on the parent, such as [EmailAddress]
 * or [PhoneNumber].
 *
 * [build] should throw an [IllegalArgumentException] with the message to report if the raw data does not
 * meet the criteria for [T]. Values that fail this check are not sent upstream but are kept in a local
 * buffer for continued editing.
 *
 * @param read the in-progress form of a stored value — what the field shows when nothing is buffered.
 * @param build the stored form of an in-progress value, or throws [IllegalArgumentException] with
 * the message to report.
 */
public fun <T, E> MutableValidated<T>.lensWithBuffer(
    read: (T) -> E,
    build: (E) -> T,
): MutableValidated<E> {
    val source = this
    val buffer = MutableRemember(stopListeningWhenOverridden = false) { read(source()) }

    return object : MutableValidated<E>, Reactive<E> by buffer {
        override val node: IssueNode = source.node.child()

        override val state: ReactiveState<E>
            get() = buffer.state

        override suspend fun set(value: E) {
            try {
                source.set(build(value))
                node.report(null)
                buffer.reset()
            } catch (e: IllegalArgumentException) {
                node.report(Issue(e.message ?: "Not a valid value", setValue = false))
                buffer.value = value
            }
        }
    }
}

@JvmName("emailAsString")
public fun MutableValidated<EmailAddress>.asString(): MutableValidated<String> = lensWithBuffer(
    read = { it.raw },
    build = { it.trim().toEmailAddress() },
)

@JvmName("nullableEmailAsString")
public fun MutableValidated<EmailAddress?>.asString(): MutableValidated<String> = lensWithBuffer(
    read = { it?.raw ?: "" },
    build = { it.trim().takeUnless(String::isBlank)?.toEmailAddress() },
)

@JvmName("phoneAsString")
public fun MutableValidated<PhoneNumber>.asString(format: PhoneNumberFormat = PhoneNumberFormat.USA): MutableValidated<String> = lensWithBuffer(
    read = {
        when (format) {
            PhoneNumberFormat.USA -> it.raw.takeLast(10)
        }
    },
    build = { it.toPhoneNumber() },
)

@JvmName("nullablePhoneAsString")
public fun MutableValidated<PhoneNumber?>.asString(format: PhoneNumberFormat = PhoneNumberFormat.USA): MutableValidated<String> = lensWithBuffer(
    read = {
        when (format) {
            PhoneNumberFormat.USA -> it?.raw?.takeLast(10) ?: ""
        }
    },
    build = { it.takeUnless(String::isBlank)?.toPhoneNumber() },
)
