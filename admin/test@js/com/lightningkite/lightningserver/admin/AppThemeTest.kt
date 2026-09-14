package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test suite for appTheme.kt
 *
 * Tests the Lightning Kite branded theme configuration used throughout the admin panel.
 * The theme defines colors, fonts, semantic overrides, and visual styling.
 */
class AppThemeTest {

    /**
     * Tests that lk() function creates a valid Theme with all required properties.
     */
    @Test
    fun testLkThemeCreation() {
        val theme = lk()

        assertNotNull(theme, "Theme should not be null")
        assertEquals("lk", theme.id, "Theme ID should be 'lk'")
    }

    /**
     * Tests that the theme has the expected color scheme.
     */
    @Test
    fun testLkThemeColors() {
        val theme = lk()

        // Background should be dark blue-gray
        val expectedBackground = Color.fromHex(0x08181D)
        assertEquals(expectedBackground, theme.background, "Background color should match")

        // Foreground should be white
        assertEquals(Color.white, theme.foreground, "Foreground should be white")

        // Card color for contrast
        val expectedCard = Color.fromHex(0x133C4A)
        // We can't directly test this as it's a local variable, but we verify semantics below

        // Title/accent color
        val expectedTitleColor = Color.fromHex(0xF4B61B)
        assertEquals(expectedTitleColor, theme.iconOverride, "Icon override should match title color")
    }

    /**
     * Tests that the theme uses the expected fonts.
     */
    @Test
    fun testLkThemeFonts() {
        val theme = lk()

        // Body font should be Lato
        assertEquals(Resources.lato, theme.font.font, "Body font should be Lato")

        // Verify font doesn't have all caps styling on body
        assertEquals(false, theme.font.allCaps, "Body font should not be all caps")
    }

    /**
     * Tests that the theme has proper spacing configuration.
     */
    @Test
    fun testLkThemeSpacing() {
        val theme = lk()

        assertEquals(0.75.rem.value, theme.gap.value, "Gap should be 0.75rem")
        assertEquals(0.dp.value, theme.elevation.value, "Elevation should be 0dp")
        assertEquals(0.px.value, theme.outlineWidth.value, "Default outline width should be 0px")
    }

    /**
     * Tests that the theme has proper corner radii.
     */
    @Test
    fun testLkThemeCornerRadii() {
        val theme = lk()

        assertTrue(theme.cornerRadii is CornerRadii.AdaptiveToSpacing, "Corner radii should be AdaptiveToSpacing (Constant)")
        val adaptive = theme.cornerRadii as CornerRadii.AdaptiveToSpacing
        assertEquals(0.5.rem.value, adaptive.value.value, "Corner radius should be 0.5rem")
    }

    /**
     * Tests that semantic overrides are properly configured.
     * We test this by verifying that applying semantics produces non-null results.
     */
    @Test
    fun testLkThemeSemanticOverrides() {
        val theme = lk()

        assertNotNull(theme.semanticOverrides, "Semantic overrides should not be null")

        // Verify that key semantics can be applied without crashing
        assertNotNull(theme[BarSemantic], "BarSemantic should be applicable")
        assertNotNull(theme[NavSemantic], "NavSemantic should be applicable")
        assertNotNull(theme[OuterSemantic], "OuterSemantic should be applicable")
        assertNotNull(theme[MainContentSemantic], "MainContentSemantic should be applicable")
        assertNotNull(theme[HeaderSemantic], "HeaderSemantic should be applicable")
        assertNotNull(theme[CardSemantic], "CardSemantic should be applicable")
        assertNotNull(theme[ImportantSemantic], "ImportantSemantic should be applicable")
        assertNotNull(theme[CriticalSemantic], "CriticalSemantic should be applicable")
        assertNotNull(theme[DialogSemantic], "DialogSemantic should be applicable")
        assertNotNull(theme[ListSemantic], "ListSemantic should be applicable")
    }

    /**
     * Tests HeaderSemantic styling.
     */
    @Test
    fun testHeaderSemantic() {
        val theme = lk()
        val headerTheme = theme[HeaderSemantic].theme

        // Header should use Barlow font with all caps
        assertEquals(Resources.barlow, headerTheme.font.font, "Header should use Barlow font")
        assertEquals(true, headerTheme.font.allCaps, "Header should be all caps")

        // Header should use title color
        val expectedTitleColor = Color.fromHex(0xF4B61B)
        assertEquals(expectedTitleColor, headerTheme.foreground, "Header foreground should be title color")
    }

    /**
     * Tests CardSemantic styling.
     */
    @Test
    fun testCardSemantic() {
        val theme = lk()
        val background = Color.fromHex(0x08181D)
        val card = Color.fromHex(0x133C4A)

        // Apply CardSemantic to base theme
        val cardTheme = theme[CardSemantic].theme

        // Card should have different background from base
        assertEquals(card, cardTheme.background, "Card should have card background color")
        assertEquals(Color.white, cardTheme.foreground, "Card foreground should be white")
    }

    /**
     * Tests ImportantSemantic styling.
     */
    @Test
    fun testImportantSemantic() {
        val theme = lk()
        val background = Color.fromHex(0x08181D)
        val card = Color.fromHex(0x133C4A)

        // Apply ImportantSemantic to base theme
        val importantTheme = theme[ImportantSemantic].theme

        // Important should use card background on base theme
        assertEquals(card, importantTheme.background, "Important should have card background")
        assertEquals(Color.white, importantTheme.foreground, "Important foreground should be white")
    }

    /**
     * Tests CriticalSemantic styling uses title color.
     */
    @Test
    fun testCriticalSemantic() {
        val theme = lk()
        val titleColor = Color.fromHex(0xF4B61B)

        val criticalTheme = theme[CriticalSemantic].theme

        // Critical should use title color as background
        assertEquals(titleColor, criticalTheme.background, "Critical should use title color background")

        // Critical foreground should be highlighted version of title color
        assertEquals(titleColor.highlight(1f), criticalTheme.foreground, "Critical foreground should be highlighted")
    }

    /**
     * Tests DialogSemantic configuration.
     */
    @Test
    fun testDialogSemantic() {
        val theme = lk()
        val dialogTheme = theme[DialogSemantic].theme

        // Dialog should have outline
        assertEquals(1.dp.value, dialogTheme.outlineWidth.value, "Dialog should have 1dp outline")

        // Dialog should have larger gap
        assertEquals(2.rem.value, dialogTheme.gap.value, "Dialog should have 2rem gap")
    }

    /**
     * Tests ListSemantic configuration.
     */
    @Test
    fun testListSemantic() {
        val theme = lk()
        val listTheme = theme[ListSemantic].theme

        // List should have minimal gap
        assertEquals(2.dp.value, listTheme.gap.value, "List should have 2dp gap")
    }

    /**
     * Tests OuterSemantic configuration for app layout.
     */
    @Test
    fun testOuterSemantic() {
        val theme = lk()
        val outerTheme = theme[OuterSemantic].theme

        // Outer should have minimal gap
        assertEquals(1.px.value, outerTheme.gap.value, "Outer should have 1px gap")

        // Outer should have no padding
        assertEquals(Edges.ZERO, outerTheme.padding, "Outer should have zero padding")

        // Outer should have gray background
        assertEquals(Color.gray(0.3f), outerTheme.background, "Outer should have gray background")
    }

    /**
     * Tests that appTheme constant is properly initialized.
     */
    @Test
    fun testAppThemeConstant() {
        assertNotNull(appTheme, "appTheme should not be null")
        val themeValue = appTheme.value
        assertNotNull(themeValue, "appTheme should have a value")
        assertEquals("lk", themeValue.id, "appTheme should use lk theme")
    }

    /**
     * Tests that nested semantic applications work correctly.
     * This tests the composability of theme semantics.
     */
    @Test
    fun testNestedSemantics() {
        val theme = lk()

        // Apply Card then Important
        val cardImportantTheme = theme[CardSemantic][ImportantSemantic].theme
        assertNotNull(cardImportantTheme, "Nested semantics should work")

        // The background should be lightened since card is not the base background
        val card = Color.fromHex(0x133C4A)
        val expected = card.lighten(0.05f)
        assertEquals(expected, cardImportantTheme.background, "Nested semantic should lighten card background")
    }

    /**
     * Tests that Theme equality works for color values.
     * This is important for conditional logic in semantic overrides.
     */
    @Test
    fun testColorEquality() {
        val background = Color.fromHex(0x08181D)
        val background2 = Color.fromHex(0x08181D)

        assertEquals(background, background2, "Same hex colors should be equal")
    }

    /**
     * Tests that color manipulation functions work as expected.
     */
    @Test
    fun testColorManipulation() {
        val titleColor = Color.fromHex(0xF4B61B)
        val highlighted = titleColor.highlight(1f)

        assertNotNull(highlighted, "Highlight should produce a color")

        val card = Color.fromHex(0x133C4A)
        val lightened = card.lighten(0.05f)

        assertNotNull(lightened, "Lighten should produce a color")
    }
}
