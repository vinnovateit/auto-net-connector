package com.vinnovateit.latch.features.settings.manager

import com.vinnovateit.latch.core.platform.InMemoryKeyValueStore
import com.vinnovateit.latch.core.settings.SettingsManager as CoreSettings
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
}
