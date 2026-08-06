package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.Condition

/**
 * Whether every value matching [narrow] also matches [broad] - that is, whether [narrow] is at least
 * as strict, so a claim about [broad] covers a query for [narrow].
 *
 * Decided structurally, and conservative in one direction only: returning false costs a request,
 * while a true this cannot justify serves a list missing rows, which is the one thing
 * [CoverageStore] must never do.  Every rule below is therefore one that holds by the shape of the
 * conditions alone.
 *
 * Recursion is over field types as well as conditions - `OnField` compares what is inside it - which
 * is why this is a free function rather than a member.
 */
internal fun <V> impliesCondition(narrow: Condition<V>, broad: Condition<V>): Boolean {
    if (narrow == broad) return true
    if (broad.matchesEverything()) return true
    if (narrow.matchesNothing()) return true

    // Composites first, so every rule after this sees one condition at a time.  Each branch shrinks
    // one side, so this terminates.
    when {
        broad is Condition.And -> return broad.conditions.all { impliesCondition(narrow, it) }
        broad is Condition.Or -> return broad.conditions.any { impliesCondition(narrow, it) }
        narrow is Condition.And -> return narrow.conditions.any { impliesCondition(it, broad) }
        narrow is Condition.Or -> return narrow.conditions.all { impliesCondition(it, broad) }
    }

    // A condition satisfied by a known, finite set of values implies anything all of those values
    // satisfy - which can simply be evaluated.  One rule, and it covers every kind of condition
    // there is: `int eq 7` implies `int gt 5`, `_id inside {a, b}` implies `_id inside {a, b, c}`,
    // and `string eq "cat"` implies a regex that matches "cat".
    narrow.exactValues()?.let { values ->
        @Suppress("UNCHECKED_CAST")
        return values.all { (broad as Condition<Any?>)(it) }
    }

    // Negations reverse: excluding more is stricter than excluding less.
    narrow.negated()?.let { narrowInner ->
        broad.negated()?.let { broadInner -> return impliesCondition(broadInner, narrowInner) }
    }

    // The same wrapper on both sides: compare what is inside it.
    unwrapMatching(narrow, broad)?.let { (a, b) -> return impliesCondition(a, b) }

    // A bound implies a looser bound on the same side of the same field.
    val n = narrow.asBound() ?: return false
    val b = broad.asBound() ?: return false
    if (n.lower != b.lower) return false
    val c = compareBoundValues(n.value, b.value) ?: return false
    // Inclusive narrow bounds need strict room when the broad bound excludes its own endpoint.
    return if (n.lower) {
        if (b.inclusive) c >= 0 else c > 0 || (c == 0 && !n.inclusive)
    } else {
        if (b.inclusive) c <= 0 else c < 0 || (c == 0 && !n.inclusive)
    }
}

/**
 * Whether this condition is satisfied by every value, for the several ways of writing that.
 *
 * `Always` is only the obvious one: an exclusion that excludes nothing and a conjunction of no
 * requirements are the same statement, and a caller composing conditions programmatically produces
 * them without meaning anything unusual by it.
 */
private fun Condition<*>.matchesEverything(): Boolean = when (this) {
    is Condition.Always -> true
    is Condition.NotInside<*> -> values.isEmpty()
    is Condition.And<*> -> conditions.all { it.matchesEverything() }
    is Condition.Not<*> -> condition.matchesNothing()
    else -> false
}

/** The mirror of [matchesEverything]: satisfied by nothing at all. */
private fun Condition<*>.matchesNothing(): Boolean = when (this) {
    is Condition.Never -> true
    is Condition.Inside<*> -> values.isEmpty()
    is Condition.Or<*> -> conditions.all { it.matchesNothing() }
    is Condition.Not<*> -> condition.matchesEverything()
    else -> false
}

/** The values that satisfy this condition, when there is a finite known set of them. */
private fun <V> Condition<V>.exactValues(): Set<Any?>? = when (this) {
    is Condition.Equal<*> -> setOf(value)
    is Condition.Inside<*> -> values
    else -> null
}

/** What this condition excludes, for the conditions that are a negation of something. */
private fun <V> Condition<V>.negated(): Condition<Any?>? {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is Condition.Not<*> -> condition as Condition<Any?>
        is Condition.NotEqual<*> -> Condition.Equal(value)
        is Condition.NotInside<*> -> Condition.Inside(values)
        else -> null
    }
}

/**
 * The conditions inside [narrow] and [broad] when both are the same kind of wrapper over the same
 * key, or null when they are not comparable that way.
 *
 * `all`/`any` over elements both preserve implication: if every element satisfies the stricter
 * condition then every element satisfies the looser one, and likewise for "some element".
 */
@Suppress("UNCHECKED_CAST")
private fun <V> unwrapMatching(narrow: Condition<V>, broad: Condition<V>): Pair<Condition<Any?>, Condition<Any?>>? =
    when {
        narrow is Condition.OnField<*, *> && broad is Condition.OnField<*, *> ->
            if (narrow.key == broad.key) narrow.condition as Condition<Any?> to broad.condition as Condition<Any?>
            else null

        narrow is Condition.OnKey<*> && broad is Condition.OnKey<*> ->
            if (narrow.key == broad.key) narrow.condition as Condition<Any?> to broad.condition as Condition<Any?>
            else null

        narrow is Condition.IfNotNull<*> && broad is Condition.IfNotNull<*> ->
            narrow.condition as Condition<Any?> to broad.condition as Condition<Any?>

        narrow is Condition.ListAllElements<*> && broad is Condition.ListAllElements<*> ->
            narrow.condition as Condition<Any?> to broad.condition as Condition<Any?>

        narrow is Condition.ListAnyElements<*> && broad is Condition.ListAnyElements<*> ->
            narrow.condition as Condition<Any?> to broad.condition as Condition<Any?>

        narrow is Condition.SetAllElements<*> && broad is Condition.SetAllElements<*> ->
            narrow.condition as Condition<Any?> to broad.condition as Condition<Any?>

        narrow is Condition.SetAnyElements<*> && broad is Condition.SetAnyElements<*> ->
            narrow.condition as Condition<Any?> to broad.condition as Condition<Any?>

        else -> null
    }

/** One side of a range: everything above [value], or everything below it. */
private class Bound(val value: Any?, val lower: Boolean, val inclusive: Boolean)

private fun <V> Condition<V>.asBound(): Bound? = when (this) {
    is Condition.GreaterThan<*> -> Bound(value, lower = true, inclusive = false)
    is Condition.GreaterThanOrEqual<*> -> Bound(value, lower = true, inclusive = true)
    is Condition.LessThan<*> -> Bound(value, lower = false, inclusive = false)
    is Condition.LessThanOrEqual<*> -> Bound(value, lower = false, inclusive = true)
    else -> null
}

/**
 * Compares two bound values, or null when they cannot be compared and no conclusion may be drawn.
 *
 * The same-type check is what makes the unchecked cast safe: conditions on one field always carry
 * values of that field's type, but nothing in the type system says so once they are erased.
 *
 * KNOWN GAP on Kotlin/JS: `Byte`/`Short` have no distinct boxed runtime representation there (both
 * are plain JS numbers), so `::class` equality can spuriously agree for a `Short` bound compared
 * against a same-valued `Int` bound, where JVM's distinct box classes correctly disagree. Verified
 * this cannot be fixed by dispatching on `is Byte`/`is Short` instead: Kotlin/JS implements those as
 * numeric-range checks, so an in-range `Int` value also satisfies `is Short`, which regressed 10
 * previously-passing JS property tests when tried (their genuine same-type `Short`/`Byte` bound
 * comparisons collapsed to false since `is Short` was misidentifying real `Int` bounds too).
 * A correct fix needs the field's static type threaded through from the call site rather than
 * inferred from the erased runtime value - out of scope here since every other numeric type and the
 * other 259+ tests in this module depend on the current, JVM-correct behavior of this function.
 */
@Suppress("UNCHECKED_CAST")
private fun compareBoundValues(a: Any?, b: Any?): Int? {
    if (a !is Comparable<*> || b == null) return null
    if (a::class != b::class) return null
    return (a as Comparable<Any>).compareTo(b)
}
