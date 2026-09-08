package com.vinnovateit.latch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class ChartPalette(
    val name: String,
    val description: String,
    val previewDownload: Color,
    val previewUpload: Color
)

object StatsColorPalettes {
    val PALETTES = listOf(
        ChartPalette(
            name = "Material Dynamic",
            description = "Adaptive primary & tertiary based on your theme",
            previewDownload = Color(0xFFC01221),
            previewUpload = Color(0xFF7D00B8)
        ),
        ChartPalette(
            name = "Emerald & Ocean",
            description = "Classic green download & blue upload",
            previewDownload = Color(0xFF10B981),
            previewUpload = Color(0xFF3B82F6)
        ),
        ChartPalette(
            name = "Sunset & Violet",
            description = "Warm amber download & deep violet upload",
            previewDownload = Color(0xFFF59E0B),
            previewUpload = Color(0xFF8B5CF6)
        ),
        ChartPalette(
            name = "Neon Teal & Coral",
            description = "Vibrant teal download & energetic coral upload",
            previewDownload = Color(0xFF14B8A6),
            previewUpload = Color(0xFFF43F5E)
        ),
        ChartPalette(
            name = "Neon Lime & Magenta",
            description = "",
            previewDownload = Color(0xFF10E88A),
            previewUpload = Color(0xFFFF007F)
        )
    )

    @Composable
    fun resolveColors(paletteName: String): Pair<Color, Color> {
        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary
        return when (paletteName) {
            "Emerald & Ocean" -> Color(0xFF10B981) to Color(0xFF3B82F6)
            "Sunset & Violet" -> Color(0xFFF59E0B) to Color(0xFF8B5CF6)
            "Neon Teal & Coral" -> Color(0xFF14B8A6) to Color(0xFFF43F5E)
            "Neon Lime & Magenta" -> Color(0xFF10E88A) to Color(0xFFFF007F)
            else -> primary to tertiary
        }
    }
}
