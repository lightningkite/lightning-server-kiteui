package com.lightningkite.kiteui.forms

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleCaseTest {
    @Test
    fun splitsCamelCase() {
        assertEquals("Elapsed Seconds", "elapsedSeconds".titleCase())
        assertEquals("Sealed Polymorphic Model", "SealedPolymorphicModel".titleCase())
    }

    @Test
    fun keepsAcronymsWhole() {
        assertEquals("Time Based OTP Proof", "TimeBasedOTPProof".titleCase())
        assertEquals("User ID", "userID".titleCase())
        assertEquals("ID", "ID".titleCase())
        assertEquals("HTML Parser", "HTMLParser".titleCase())
    }

    @Test
    fun splitsAfterDigits() {
        assertEquals("Line2 Address", "line2Address".titleCase())
    }
}
