package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewDsl
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.fieldTheme
import kotlin.time.Clock.System.now
import com.lightningkite.reactive.core.reactiveProcess
import kotlinx.coroutines.delay
import kotlin.time.Instant
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


internal val nowBySecond = reactiveProcess {
    while (true) {
        emit(now()); delay(1000)
    }
}

internal data class WithTimestamp<T>(val value: T, val timestamp: Instant = now())
internal fun <T> T.withTimestamp(): WithTimestamp<T> = WithTimestamp(this)

val KeyboardHints.Companion.oneTimeCodeLetters get() = KeyboardHints(
    case = KeyboardCase.Letters,
    type = KeyboardType.Text,
    autocomplete = AutoComplete.OneTimeCode,
    autocorrect = false
)

val Icon.Companion.password: Icon
    get() = Icon(
        1.5.rem,
        1.5.rem,
        -240,
        -1200,
        1440,
        1440,
        listOf("M80-200v-80h800v80H80Zm46-242-52-30 34-60H40v-60h68l-34-58 52-30 34 58 34-58 52 30-34 58h68v60h-68l34 60-52 30-34-60-34 60Zm320 0-52-30 34-60h-68v-60h68l-34-58 52-30 34 58 34-58 52 30-34 58h68v60h-68l34 60-52 30-34-60-34 60Zm320 0-52-30 34-60h-68v-60h68l-34-58 52-30 34 58 34-58 52 30-34 58h68v60h-68l34 60-52 30-34-60-34 60Z")
    )
val Icon.Companion.security: Icon
    get() = Icon(
        1.5.rem,
        1.5.rem,
        -240,
        -1200,
        1440,
        1440,
        listOf("M420-360h120l-23-129q20-10 31.5-29t11.5-42q0-33-23.5-56.5T480-640q-33 0-56.5 23.5T400-560q0 23 11.5 42t31.5 29l-23 129Zm60 280q-139-35-229.5-159.5T160-516v-244l320-120 320 120v244q0 152-90.5 276.5T480-80Zm0-84q104-33 172-132t68-220v-189l-240-90-240 90v189q0 121 68 220t172 132Zm0-316Z")
    )
val Icon.Companion.pinCode: Icon
    get() = Icon(
        1.5.rem,
        1.5.rem,
        -240,
        -1200,
        1440,
        1440,
        listOf("M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160Zm100-200h46v-240h-36l-70 50 24 36 36-26v180Zm124 0h156v-40h-94l-2-2q21-20 34.5-34t21.5-22q18-18 27-36t9-38q0-29-22-48.5T458-600q-26 0-47 15t-29 39l40 16q5-13 14.5-20.5T458-558q15 0 24.5 8t9.5 20q0 11-4 20.5T470-486l-32 32-54 54v40Zm296 0q36 0 58-20t22-52q0-18-10-32t-28-22v-2q14-8 22-20.5t8-29.5q0-27-21-44.5T678-600q-25 0-46.5 14.5T604-550l40 16q4-12 13-19t21-7q13 0 21.5 7.5T708-534q0 14-10 22t-26 8h-18v40h20q20 0 31 8t11 22q0 13-11 22.5t-25 9.5q-17 0-26-7.5T638-436l-40 16q7 29 28.5 44.5T680-360ZM160-240h640v-480H160v480Zm0 0v-480 480Z")
    )


@ViewDsl
@OptIn(ExperimentalContracts::class)
inline fun ViewWriter.fieldNoErrorText(label: String, content: ViewWriter.() -> ViewModifiable): ViewModifiable {
    contract { callsInPlace(content, InvocationKind.EXACTLY_ONCE) }
    return col {
        gap = 0.px
        FieldLabelSemantic.onNext - text(label)
        fieldTheme - content()
    }
}