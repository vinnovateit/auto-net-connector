package com.vinnovateit.latch.features.settings.manager

import com.vinnovateit.latch.core.platform.InMemoryKeyValueStore
import com.vinnovateit.latch.core.settings.SettingsManager as CoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsManagerTest {

    @Before
    fun setup() {
        CoreSettings.initialize(InMemoryKeyValueStore())
    }

    @Test
    fun hapticsEnabled_delegatesToCoreSettings() {
        assertTrue(SettingsManager.hapticsEnabled.value)

        SettingsManager.setHapticsEnabled(false)
        assertFalse(SettingsManager.hapticsEnabled.value)
        assertFalse(CoreSettings.hapticsEnabled.value)

        SettingsManager.setHapticsEnabled(true)
        assertTrue(SettingsManager.hapticsEnabled.value)
        assertTrue(CoreSettings.hapticsEnabled.value)
    }

    @Test
    fun paletteStyle_delegatesToCoreSettings() {
        assertEquals("TonalSpot", SettingsManager.paletteStyle.value)

        SettingsManager.setPaletteStyle("Vibrant")
        assertEquals("Vibrant", SettingsManager.paletteStyle.value)
        assertEquals("Vibrant", CoreSettings.paletteStyle.value)

        CoreSettings.clearAll()
        assertEquals("TonalSpot", CoreSettings.paletteStyle.value)
    }

    @Test
    fun accentColor_customHexSupported() {
        assertEquals("Red", SettingsManager.accentColor.value)

        SettingsManager.setAccentColor("#FF5722")
        assertEquals("#FF5722", SettingsManager.accentColor.value)
        assertEquals("#FF5722", CoreSettings.accentColor.value)
    }
}
