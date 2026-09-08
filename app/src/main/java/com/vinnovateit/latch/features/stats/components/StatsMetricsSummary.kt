package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.common.util.StatsColorPalettes
import com.vinnovateit.latch.common.util.formatBytes
import com.vinnovateit.latch.common.util.formatDurationDynamic
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.features.settings.manager.SettingsManager

data class StatsOverviewMetrics(
    val totalBytes: Long,
    val totalUploadBytes: Long,
    val totalDownloadBytes: Long,
    val totalSessions: Int,
    val averageDurationMs: Long,
    val topLocation: String
)

fun computeMetrics(sessions: List<PortalSessionRecord>): StatsOverviewMetrics {
    if (sessions.isEmpty()) {
        return StatsOverviewMetrics(0L, 0L, 0L, 0, 0L, "None")
    }
    val total = sessions.sumOf { it.totalBytes }
    val ul = sessions.sumOf { it.uploadBytes }
    val dl = sessions.sumOf { it.downloadBytes }
    val avgDur = (sessions.map { it.durationMillis }.average()).toLong()
    val topLoc = sessions.groupBy { it.location }
        .maxByOrNull { it.value.size }?.key ?: "Unknown"

    return StatsOverviewMetrics(
        totalBytes = total,
        totalUploadBytes = ul,
        totalDownloadBytes = dl,
        totalSessions = sessions.size,
        averageDurationMs = avgDur,
        topLocation = topLoc
    )
}

@Composable
fun StatsMetricsSummary(
    metrics: StatsOverviewMetrics,
    modifier: Modifier = Modifier
) {
    val totalFmt = formatBytes(metrics.totalBytes)
    val ulFmt = formatBytes(metrics.totalUploadBytes)
    val dlFmt = formatBytes(metrics.totalDownloadBytes)
    val chartPalette by SettingsManager.chartPalette.collectAsStateWithLifecycle()
    val (dlColor, ulColor) = StatsColorPalettes.resolveColors(chartPalette)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Total data used",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = totalFmt.first,
                style = MaterialTheme.typography.displayMedium,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = totalFmt.second,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        GameStatRow(label = "Downloaded", value = dlFmt.first, unit = dlFmt.second, valueColor = dlColor)
        GameStatRow(label = "Uploaded", value = ulFmt.first, unit = ulFmt.second, valueColor = ulColor)
        GameStatRow(label = "Portal logins", value = "${metrics.totalSessions}")
    }
}

@Composable
fun GameStatRow(
    label: String,
    value: String,
    unit: String = "",
    sublabel: String = "",
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sublabel.isNotBlank()) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}
