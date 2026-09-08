package com.vinnovateit.latch.core.settings

import com.vinnovateit.latch.core.platform.InMemoryKeyValueStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsManagerTest {

    private lateinit var store: InMemoryKeyValueStore

    @BeforeTest
    fun setup() {
        store = InMemoryKeyValueStore()
        SettingsManager.initialize(store)
    }

    @Test
    fun `haptics is enabled by default`() {
        assertTrue(SettingsManager.hapticsEnabled.value)
    }

    @Test
    fun `setHapticsEnabled updates state and store`() {
        SettingsManager.setHapticsEnabled(false)
        assertFalse(SettingsManager.hapticsEnabled.value)
        assertFalse(store.getBoolean("haptics_enabled", true))

        SettingsManager.setHapticsEnabled(true)
        assertTrue(SettingsManager.hapticsEnabled.value)
        assertTrue(store.getBoolean("haptics_enabled", false))
    }

    @Test
    fun `clearAll resets haptics to default true`() {
        SettingsManager.setHapticsEnabled(false)
        assertFalse(SettingsManager.hapticsEnabled.value)

        SettingsManager.clearAll()
        assertTrue(SettingsManager.hapticsEnabled.value)
        assertTrue(store.getBoolean("haptics_enabled", false))
    }

    @Test
    fun `initialize loads persisted haptics setting`() {
        val populatedStore = InMemoryKeyValueStore()
        populatedStore.putBoolean("haptics_enabled", false)

        SettingsManager.initialize(populatedStore)
        assertFalse(SettingsManager.hapticsEnabled.value)
    }
}
