package com.lightningkite.lightningserver.typed.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralizeTest {
    @Test fun regularAndIrregularWords() {
        assertEquals("todos", "todo".pluralize())
        assertEquals("users", "user".pluralize())
        assertEquals("categories", "category".pluralize())
        assertEquals("statuses", "status".pluralize())
        assertEquals("people", "person".pluralize())
        assertEquals("matrices", "matrix".pluralize())
        assertEquals("vertices", "vertex".pluralize())
        assertEquals("indices", "index".pluralize())
        assertEquals("mice", "mouse".pluralize())
    }

    @Test fun rulesOnlyMatchTheEndOfTheWord() {
        assertEquals("complexes", "complex".pluralize())
        assertEquals("annexes", "annex".pluralize())
        assertEquals("matrixRows", "matrixRow".pluralize())
        assertEquals("personnels", "personnel".pluralize())
        assertEquals("childcares", "childcare".pluralize())
    }
}
