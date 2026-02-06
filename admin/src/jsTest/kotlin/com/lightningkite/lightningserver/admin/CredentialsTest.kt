package com.lightningkite.lightningserver.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Tests for credentials.kt
 *
 * Tests the AdminCredentials and AdminSettings data classes, as well as
 * the helper functions and reactive properties used for admin authentication.
 */
class CredentialsTest {

    // ============================================
    // AdminCredentials Tests
    // ============================================

    @Test
    fun adminCredentials_defaultValues() {
        val creds = AdminCredentials()
        assertNull(creds.session)
        assertNull(creds.userType)
    }

    @Test
    fun adminCredentials_withValues() {
        val creds = AdminCredentials(
            session = "test-session-token",
            userType = "Admin"
        )
        assertEquals("test-session-token", creds.session)
        assertEquals("Admin", creds.userType)
    }

    @Test
    fun adminCredentials_partialValues_sessionOnly() {
        val creds = AdminCredentials(session = "token123")
        assertEquals("token123", creds.session)
        assertNull(creds.userType)
    }

    @Test
    fun adminCredentials_partialValues_userTypeOnly() {
        val creds = AdminCredentials(userType = "User")
        assertNull(creds.session)
        assertEquals("User", creds.userType)
    }

    @Test
    fun adminCredentials_copy() {
        val original = AdminCredentials(session = "old", userType = "Admin")
        val modified = original.copy(session = "new")
        assertEquals("new", modified.session)
        assertEquals("Admin", modified.userType)
    }

    @Test
    fun adminCredentials_equality() {
        val creds1 = AdminCredentials(session = "token", userType = "User")
        val creds2 = AdminCredentials(session = "token", userType = "User")
        assertEquals(creds1, creds2)
        assertEquals(creds1.hashCode(), creds2.hashCode())
    }

    @Test
    fun adminCredentials_inequality() {
        val creds1 = AdminCredentials(session = "token1", userType = "User")
        val creds2 = AdminCredentials(session = "token2", userType = "User")
        assertFalse(creds1 == creds2)
    }

    // ============================================
    // AdminSettings Tests
    // ============================================

    @Test
    fun adminSettings_defaultValues() {
        val settings = AdminSettings()
        assertFalse(settings.showHiddenFields)
        assertFalse(settings.editAllFields)
        assertFalse(settings.showAlternativeEditOptions)
        assertFalse(settings.showEndpoints)
        assertNull(settings.unlockDestructiveActions)
        assertTrue(settings.liveData)
    }

    @Test
    fun adminSettings_allEnabled() {
        val now = Clock.System.now()
        val settings = AdminSettings(
            showHiddenFields = true,
            editAllFields = true,
            showAlternativeEditOptions = true,
            showEndpoints = true,
            unlockDestructiveActions = now,
            liveData = false
        )
        assertTrue(settings.showHiddenFields)
        assertTrue(settings.editAllFields)
        assertTrue(settings.showAlternativeEditOptions)
        assertTrue(settings.showEndpoints)
        assertEquals(now, settings.unlockDestructiveActions)
        assertFalse(settings.liveData)
    }

    @Test
    fun adminSettings_copy() {
        val original = AdminSettings(showHiddenFields = false)
        val modified = original.copy(showHiddenFields = true)
        assertTrue(modified.showHiddenFields)
        assertFalse(modified.editAllFields) // Unchanged
    }

    @Test
    fun adminSettings_equality() {
        val settings1 = AdminSettings(showHiddenFields = true, liveData = false)
        val settings2 = AdminSettings(showHiddenFields = true, liveData = false)
        assertEquals(settings1, settings2)
    }

    @Test
    fun adminSettings_destructiveActionsWithTimestamp() {
        val unlockTime = Clock.System.now()
        val settings = AdminSettings(unlockDestructiveActions = unlockTime)
        assertNotNull(settings.unlockDestructiveActions)
        assertEquals(unlockTime, settings.unlockDestructiveActions)
    }

    // ============================================
    // Edge Case Tests
    // ============================================

    @Test
    fun adminCredentials_emptyStrings() {
        val creds = AdminCredentials(session = "", userType = "")
        assertEquals("", creds.session)
        assertEquals("", creds.userType)
    }

    @Test
    fun adminCredentials_specialCharacters() {
        val creds = AdminCredentials(
            session = "token-with-special!@#\$%^&*()",
            userType = "User Type With Spaces"
        )
        assertEquals("token-with-special!@#\$%^&*()", creds.session)
        assertEquals("User Type With Spaces", creds.userType)
    }

    @Test
    fun adminSettings_distantPastTimestamp() {
        val settings = AdminSettings(unlockDestructiveActions = Instant.DISTANT_PAST)
        assertEquals(Instant.DISTANT_PAST, settings.unlockDestructiveActions)
    }

    @Test
    fun adminSettings_distantFutureTimestamp() {
        val settings = AdminSettings(unlockDestructiveActions = Instant.DISTANT_FUTURE)
        assertEquals(Instant.DISTANT_FUTURE, settings.unlockDestructiveActions)
    }

    // ============================================
    // Destructuring Tests
    // ============================================

    @Test
    fun adminCredentials_destructuring() {
        val creds = AdminCredentials(session = "token", userType = "Admin")
        val (session, userType) = creds
        assertEquals("token", session)
        assertEquals("Admin", userType)
    }

    @Test
    fun adminSettings_destructuring() {
        val now = Clock.System.now()
        val settings = AdminSettings(
            showHiddenFields = true,
            editAllFields = true,
            showAlternativeEditOptions = true,
            showEndpoints = true,
            unlockDestructiveActions = now,
            liveData = false
        )
        val (showHidden, editAll, showAlt, showEndpoints, unlock, live) = settings
        assertTrue(showHidden)
        assertTrue(editAll)
        assertTrue(showAlt)
        assertTrue(showEndpoints)
        assertEquals(now, unlock)
        assertFalse(live)
    }

    // ============================================
    // toString Tests
    // ============================================

    @Test
    fun adminCredentials_toString() {
        val creds = AdminCredentials(session = "token", userType = "User")
        val str = creds.toString()
        assertTrue(str.contains("AdminCredentials"))
        assertTrue(str.contains("session=token"))
        assertTrue(str.contains("userType=User"))
    }

    @Test
    fun adminSettings_toString() {
        val settings = AdminSettings(showHiddenFields = true)
        val str = settings.toString()
        assertTrue(str.contains("AdminSettings"))
        assertTrue(str.contains("showHiddenFields=true"))
    }
}
