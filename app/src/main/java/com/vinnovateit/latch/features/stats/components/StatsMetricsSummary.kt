package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.common.util.formatBytes
import com.vinnovateit.latch.common.util.formatDurationDynamic
import com.vinnovateit.latch.core.model.PortalSessionRecord

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Total Data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${totalFmt.first} ${totalFmt.second}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "↓ ${dlFmt.first} ${dlFmt.second}  ↑ ${ulFmt.first} ${ulFmt.second}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Sessions / Avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${metrics.totalSessions} sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Avg ${formatDurationDynamic(metrics.averageDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
