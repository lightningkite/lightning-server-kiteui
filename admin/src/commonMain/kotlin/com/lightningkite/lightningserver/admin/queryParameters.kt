package com.lightningkite.lightningserver.admin

// by Claude
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.lensing.lens
import kotlinx.serialization.KSerializer

/**
 * Creates a bidirectional binding between a nullable String signal (typically a URL query parameter)
 * and a typed value via JSON serialization.
 *
 * When reading:
 * - If the string is null or parsing fails, returns the default value
 * - Otherwise returns the deserialized value
 *
 * When writing:
 * - Serializes the value to JSON and stores it in the signal
 *
 * @param serializer The serializer for the target type
 * @param default Lambda returning the default value when the string is null or parsing fails
 * @return A MutableReactiveValue that reads/writes JSON strings to/from this signal
 */
fun <T> Signal<String?>.lensJson(
    serializer: KSerializer<T>,
    default: () -> T
): MutableReactiveValue<T> = lens(
    get = { str ->
        str?.runCatching { DefaultJson.decodeFromString(serializer, this) }?.getOrNull() ?: default()
    },
    set = { DefaultJson.encodeToString(serializer, it) }
)

/**
 * Creates a bidirectional binding between a nullable String signal and a nullable typed value
 * via JSON serialization, where null is the default when parsing fails.
 *
 * @param serializer The serializer for the nullable target type
 * @return A MutableReactiveValue that reads/writes JSON strings to/from this signal
 */
fun <T : Any> Signal<String?>.lensJsonNullable(
    serializer: KSerializer<T?>
): MutableReactiveValue<T?> = lens(
    get = { str ->
        str?.runCatching { DefaultJson.decodeFromString(serializer, this) }?.getOrNull()
    },
    set = { DefaultJson.encodeToString(serializer, it) }
)
