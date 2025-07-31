package com.lightningkite.serialization

import com.lightningkite.EmailAddress
import com.lightningkite.PhoneNumber
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableRemember
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.extensions.value
import com.lightningkite.toPhoneNumber
import kotlin.jvm.JvmName

@Suppress("DEPRECATION")
@JvmName("phoneNumberAsString")
fun MutableReactive<PhoneNumber>.asString() = lens(
    get = { it.raw.takeLast(10) },
    set = { PhoneNumber(it) }
)

fun MutableReactive<PhoneNumber>.withStringBuffer(getStr: (PhoneNumber) -> String = { it.raw.takeLast(10) }): MutableReactive<String> {
    val buffer = MutableRemember(stopListeningWhenOverridden = false) {
        getStr(this@withStringBuffer.invoke())
    }

    return object : MutableReactive<String>, Reactive<String> by buffer {
        override suspend fun set(value: String) {
            try {
                this@withStringBuffer set value.toPhoneNumber()
                buffer.reset()
            } catch (_: IllegalArgumentException) {
                buffer.value = value
            }
        }
    }
}

@Suppress("DEPRECATION")
@JvmName("emailAddressAsString")
fun MutableReactive<EmailAddress>.asString() = lens(
    get = { it.raw },
    set = { EmailAddress(it) }
)