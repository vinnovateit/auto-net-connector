package com.vinnovateit.latch.core.wifi

import com.vinnovateit.latch.core.settings.SettingsManager

/**
 * True unless the SSID is readable and readably *not* a campus network.
 *
 * Shared by the engine's login gate and by the read-only probe a CLI one-shot
 * uses when it owns the runtime, so both decide "campus network" identically.
 */
fun isVitCampusSsid(ssid: String?): Boolean {
    val clean = ssid?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() } ?: return true
    return clean.contains("VIT", ignoreCase = true) ||
        SettingsManager.allowedSsids.value.any { clean.contains(it, ignoreCase = true) }
}
