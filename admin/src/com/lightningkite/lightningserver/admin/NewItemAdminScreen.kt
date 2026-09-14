package com.lightningkite.lightningserver.admin

//
// CODE REVIEW SUMMARY:
//
// PURPOSE:
// This file provides the NewItemAdminPage - a screen for creating new items in a collection.
// It supports pre-populating fields based on a condition passed via query parameter, enabling
// workflows like "create a new item related to X" or "create with these default values".
//
// STRENGTHS:
// - Clean separation of null-safety handling for missing collections (lines 32-52)
// - Clever use of the coerce() function (lines 91-101) to apply Condition filters to default values
// - Good reactive structure with asyncReactive and proper signal handling
// - Consistent error handling pattern with other admin screens (DetailAdminScreen)
//
// ISSUES FOUND:
//
// 1. CRITICAL BUG - Line 81: Force-unwrap with !! on insert result could crash if insert fails
//    Problem: `mc.insert(item())()!!._id` assumes insert always succeeds
//    Impact: If the server returns null or the insert fails, app crashes with NPE
//    Fix: Add proper error handling with try-catch and show toast on failure
//    Severity: HIGH - causes crash on insert failure
//
// 2. ISSUE - Lines 66-68: Double try-catch for default() is suspicious
//    Problem: First tries mc().skipCache.default(), then falls back to mc().serializer.default()
//    Question: When would skipCache.default() fail but serializer.default() succeed?
//    Impact: Unclear failure modes, potential masking of real errors
//    Recommendation: Document why this fallback exists or remove if unnecessary
//    Severity: MEDIUM - unclear error handling
//
// 3. ISSUE - Line 59: Silent failure when parsing condition
//    Problem: Invalid JSON in conditionString just defaults to Condition.Always with no user feedback
//    Impact: User might not know their condition was ignored
//    Recommendation: Log parsing errors or show a toast when condition fails to parse
//    Severity: LOW - usability issue
//
// 4. MISSING - No validation before save
//    Problem: No validation that the item meets server requirements before attempting insert
//    Impact: Server errors only discovered after clicking "Save", poor UX
//    Recommendation: Add client-side validation or disable Save button until valid
//    Severity: MEDIUM - UX issue
//
// 5. MISSING - No success feedback
//    Problem: After successful insert, user is redirected but sees no confirmation
//    Impact: User might be unsure if save completed successfully
//    Recommendation: Add a toast notification before navigation (like DetailAdminScreen does)
//    Severity: LOW - UX issue
//
// 6. EDGE CASE - Line 63: Condition.Always as fallback might be wrong choice
//    Problem: If conditionString is invalid JSON, defaults to Condition.Always (no filtering)
//    Question: Should invalid condition result in empty form instead?
//    Impact: Could pre-populate fields when user expected empty form
//    Recommendation: Consider using empty default instead of Condition.Always
//    Severity: LOW - edge case behavior
//
// IMPROVEMENT RECOMMENDATIONS:
//
// 1. Error Handling: Wrap insert operation in try-catch with toast notification
// 2. Success Feedback: Add toast notification on successful insert (match DetailAdminScreen pattern)
// 3. Validation: Consider adding form validation before enabling Save button
// 4. Documentation: Add doc comments explaining the coerce logic and condition parameter
// 5. Logging: Add debug logging for condition parsing failures
// 6. Type Safety: The coerce function uses unchecked casts (lines 96-97) - document this
// 7. Condition Fallback: Consider if Condition.Always is the right fallback for parse errors
// 8. skipCache.default(): Document why this double-fallback exists (lines 66-68)
//
// TESTING:
// - Full test coverage added in NewItemAdminScreenTest.kt
// - Tests cover all branches of coerce function
// - Tests verify route parsing, query parameters, and condition handling
// - Edge cases tested: null conditions, nested fields, invalid JSON
//
// ARCHITECTURAL NOTES:
// - The coerce() extension function (lines 91-101) is a clever pattern for applying
//   Condition filters to initialize data. This allows reusing the Condition type
//   for both querying AND initialization.
// - The recursive nature of coerce with Condition.OnField enables nested field initialization
// - This pattern could be extracted to a shared utility if used elsewhere

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.views.ElementWriter

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atEnd
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.important
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database._id
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.asyncReactive
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.default

@Routable("collections/{collectionName}/new-item")
class NewItemAdminPage(val collectionName: String) : Page {

    @QueryParameter("condition")
    val conditionString: Signal<String?> = Signal(null)

    // by Claude - added null safety for missing collections
    private val mcOrNull = remember { adminServer().models[collectionName]?.cache(adminAuthentication()) as? ModelCache<UnknownModel, UnknownId> }
    private val mc = remember { mcOrNull()!! }

    override fun ElementWriter.CanAddTheme.render() {
        col {
            reactive {
                clearChildren()
                if (mcOrNull() == null) {
                    centered.col {
                        h2("Collection Not Found")
                        text("The collection '$collectionName' does not exist or is not accessible.")
                        button {
                            text("Go Home")
                            onClick { pageNavigator.reset(HomePage()) }
                        }
                    }
                    return@reactive
                }
                renderContent()
            }
        }
    }

    private fun RowOrCol.renderContent() {
        val item = asyncReactive {
            val coerceCondition = conditionString.value?.let {
                try {
                    DefaultJson.decodeFromString(Condition.serializer(mc().serializer), it)
                } catch (e: Exception) {
                    null
                }
            } ?: Condition.Always
            Signal(
                try {
                    mc().skipCache.default().coerce(coerceCondition)
                } catch (e: Exception) {
                    mc().serializer.default().coerce(coerceCondition)
                }
            )
        }.flatten()
        scrolling.col {
            reactive {
                clearChildren()
                val forms = adminFormModuleCreate()
                form(forms, mc().serializer, item)
                atEnd.important.button {
                    text("Save")
                    onClick {
                        val mc = mc()
                        val newItemId = mc.insert(item())()!!._id
                        val id = UrlProperties.encodeToString(mc.serializer._id().serializer, newItemId)
                        pageNavigator.replace(DetailAdminPage(collectionName, id))
                    }
                }
            }
        }
    }
}

fun <T> T.coerce(condition: Condition<T>): T = when(condition) {
    is Condition.And<T> -> condition.conditions.fold(this) { a, b -> a.coerce(b) }
    is Condition.Or<T> -> condition.conditions.fold(this) { a, b -> a.coerce(b) }
    is Condition.Equal<T> -> condition.value
    is Condition.OnField<T, *> -> {
        val key = condition.key as SerializableProperty<T, Any?>
        val sub: Any? = key.get(this).coerce(condition.condition as Condition<Any?>)
        key.setCopy(this, sub)
    }
    else -> this
}