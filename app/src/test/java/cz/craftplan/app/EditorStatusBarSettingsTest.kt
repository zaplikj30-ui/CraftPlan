package cz.craftplan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStatusBarSettingsTest {
    @Test
    fun phoneAndTabletDefaults() {
        val settings = EditorStatusBarSettings()
        assertFalse(settings.hidden(true, false))
        assertTrue(settings.hidden(false, false))
        assertFalse(settings.hidden(true, true))
        assertFalse(settings.hidden(false, true))
    }

    @Test
    fun overridesAreIndependentAndWinOverDeviceDefaults() {
        val settings = EditorStatusBarSettings().withHidden(true, true)
        assertTrue(settings.hidden(true, false))
        assertTrue(settings.hidden(true, true))
        assertTrue(settings.hidden(false, false))
        assertFalse(settings.hidden(false, true))
        val both = settings.withHidden(false, false)
        assertTrue(both.hidden(true, false))
        assertFalse(both.hidden(false, false))
        assertFalse(both.hidden(false, true))
        assertTrue(both.withHidden(false, true).hidden(false, true))
    }

    @Test
    fun overlayUsesUnionAndSubtractsExistingTopPadding() {
        assertEquals(24, editorOverlayTop(24, 0, 0))
        assertEquals(36, editorOverlayTop(24, 36, 0))
        assertEquals(16, editorOverlayTop(24, 36, 20))
        assertEquals(0, editorOverlayTop(24, 36, 40))
        assertEquals(0, editorOverlayTop(0, 0, 0))
    }
}
