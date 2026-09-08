package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.common.util.formatDate
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.ui.theme.ColorGraphDownload
import com.vinnovateit.latch.ui.theme.ColorGraphUpload

data class DailyUsageTrend(
    val dateLabel: String,
    val timestamp: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long
)

fun aggregateDailyUsage(sessions: List<PortalSessionRecord>, daysLimit: Int = 14): List<DailyUsageTrend> {
    if (sessions.isEmpty()) return emptyList()

    return sessions
        .filter { it.loginTime > 0 }
        .groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }
        .map { (_, list) ->
            val firstTimestamp = list.minOf { it.loginTime }
            val ul = list.sumOf { it.uploadBytes }
            val dl = list.sumOf { it.downloadBytes }
            val label = formatDate(firstTimestamp, "dd MMM")
            DailyUsageTrend(
                dateLabel = label,
                timestamp = firstTimestamp,
                uploadBytes = ul,
                downloadBytes = dl,
                totalBytes = ul + dl
            )
        }
        .sortedBy { it.timestamp }
        .takeLast(daysLimit)
}

@Composable
fun PortalUsageTrends(
    trends: List<DailyUsageTrend>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    val maxDaily = trends.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Daily Trends",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            trends.forEach { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val barHeight = 120.dp
                    val frac = (item.totalBytes.toFloat() / maxDaily).coerceIn(0.1f, 1f)

                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(barHeight),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight * frac)
                        ) {
                            val ulFrac = if (item.totalBytes > 0) item.uploadBytes.toFloat() / item.totalBytes else 0f
                            val dlFrac = 1f - ulFrac

                            val dlHeight = size.height * dlFrac
                            val ulHeight = size.height * ulFrac

                            // Download portion (bottom)
                            drawRoundRect(
                                color = ColorGraphDownload,
                                topLeft = Offset(0f, size.height - dlHeight),
                                size = Size(size.width, dlHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            // Upload portion (top)
                            if (ulHeight > 0f) {
                                drawRoundRect(
                                    color = ColorGraphUpload,
                                    topLeft = Offset(0f, size.height - dlHeight - ulHeight),
                                    size = Size(size.width, ulHeight),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
