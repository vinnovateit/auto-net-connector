package com.vinnovateit.latch.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.core.domain.SessionRepository
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.platform.PlatformServices
import com.vinnovateit.latch.core.settings.SettingsManager
import com.vinnovateit.latch.core.stats.formatBitsPerSecond
import com.vinnovateit.latch.core.stats.formatBytes
import com.vinnovateit.latch.core.stats.formatDate
import com.vinnovateit.latch.core.stats.formatDurationDynamic
import com.vinnovateit.latch.desktop.resources.Res
import com.vinnovateit.latch.desktop.resources.stats_empty_message
import com.vinnovateit.latch.desktop.resources.stats_title
import com.vinnovateit.latch.ui.components.DataUsageDonut
import com.vinnovateit.latch.ui.components.LatchDetailHeader
import com.vinnovateit.latch.ui.components.LatchIcons
import com.vinnovateit.latch.ui.theme.ColorGraphDownload
import com.vinnovateit.latch.ui.theme.ColorGraphUpload
import com.vinnovateit.latch.ui.theme.LocalIsDarkTheme
import com.vinnovateit.latch.ui.theme.satoshiFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Session history driven by portal history from the Pronto Networks captive portal.
 *
 * Shows live connection speed while connected, KPI summary tiles, daily bar chart,
 * and portal session cards. A sync button re-fetches using saved credentials.
 */
@Composable
fun StatsScreen(
    sessions: SessionRepository,
    platform: PlatformServices,
    onBack: (() -> Unit)?,
    onClearHistory: () -> Unit,
) {
    val liveStatus by sessions.liveStatus.collectAsStateWithLifecycle()
    val portalHistory by sessions.portalHistory.collectAsStateWithLifecycle()
    val isSyncing by sessions.isSyncing.collectAsStateWithLifecycle()
    val speedUnit by SettingsManager.speedUnits.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row with title + sync button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LatchDetailHeader(
                title = stringResource(Res.string.stats_title),
                onBack = onBack,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val userId = platform.credentials.userId()
                    val password = platform.credentials.password()
                    if (!userId.isNullOrBlank() && !password.isNullOrBlank()) {
                        scope.launch { sessions.syncPortalHistory(userId, password) }
                    }
                },
                enabled = !isSyncing,
                modifier = Modifier.size(40.dp),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = LatchIcons.Refresh,
                        contentDescription = "Sync from Portal",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Live session card (when connected)
            liveStatus?.let { live ->
                item {
                    val usage = DataUsage(live.totalRxBytes, live.totalTxBytes)
                    LiveSessionCard(
                        startTimeMillis = live.startTimeMillis,
                        usage = usage,
                        latestRxBps = live.liveData.lastOrNull()?.usage?.rxBps ?: 0L,
                        latestTxBps = live.liveData.lastOrNull()?.usage?.txBps ?: 0L,
                        speedUnit = speedUnit,
                    )
                }
            }

            // KPI summary tiles from portal history
            item {
                PortalTotalsRow(history = portalHistory)
            }

            // Daily usage bar chart from portal history
            if (portalHistory.isNotEmpty()) {
                item {
                    PortalDailyBarChart(history = portalHistory)
                }
            }

            // Portal session cards
            if (portalHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isSyncing) "Syncing…" else stringResource(Res.string.stats_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(portalHistory) { record ->
                    PortalSessionRow(record = record)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// KPI tiles
// ---------------------------------------------------------------------------

@Composable
private fun PortalTotalsRow(history: List<PortalSessionRecord>) {
    val totalBytes = history.sumOf { it.totalBytes.coerceAtLeast(it.uploadBytes + it.downloadBytes) }
    val (totalValue, totalUnit) = formatBytes(totalBytes)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TotalTile(
            label = "Total Data",
            value = "$totalValue $totalUnit",
            modifier = Modifier.weight(1f),
        )
        TotalTile(
            label = "Sessions",
            value = history.size.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TotalTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Daily bar chart (portal history)
// ---------------------------------------------------------------------------

@Composable
private fun PortalDailyBarChart(history: List<PortalSessionRecord>) {
    data class Bar(val label: String, val rxBytes: Long, val txBytes: Long)

    val bars = remember(history) {
        val dayKeyFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val labelFmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        history
            .filter { it.loginTime > 0 }
            .groupBy { dayKeyFmt.format(java.util.Date(it.loginTime)) }
            .map { (_, group) ->
                Bar(
                    label = labelFmt.format(java.util.Date(group.first().loginTime)),
                    rxBytes = group.sumOf { it.downloadBytes },
                    txBytes = group.sumOf { it.uploadBytes },
                )
            }.takeLast(7)
    }
    if (bars.isEmpty()) return

    val maxBytes = remember(bars) { bars.maxOf { it.rxBytes + it.txBytes }.coerceAtLeast(1L) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Daily Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = satoshiFontFamily(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    val rxFrac = (bar.rxBytes.toFloat() / maxBytes).coerceIn(0.05f, 1f)
                    val txFrac = (bar.txBytes.toFloat() / maxBytes).coerceIn(0.02f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .fillMaxHeight(txFrac)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(ColorGraphUpload),
                                )
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .fillMaxHeight(rxFrac)
                                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                        .background(ColorGraphDownload),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Portal session card
// ---------------------------------------------------------------------------

@Composable
private fun PortalSessionRow(record: PortalSessionRecord) {
    val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
    val isAmoled = usePureBlack && LocalIsDarkTheme.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (isAmoled) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Location chip + duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LatchIcons.WifiOutlined,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = record.location.ifBlank { "Unknown" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                val durStr = record.durationFormatted.ifBlank { formatDurationDynamic(record.durationMillis) }
                Text(
                    text = durStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = satoshiFontFamily(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Login time
            if (record.loginTime > 0) {
                Text(
                    text = formatDate(record.loginTime, "dd MMM, HH:mm"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = satoshiFontFamily(),
                )
                Spacer(Modifier.height(6.dp))
            }

            // Data row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val (total, totalUnit) = formatBytes(
                    record.totalBytes.coerceAtLeast(record.uploadBytes + record.downloadBytes)
                )
                Text(
                    text = "$total $totalUnit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )
                Row {
                    Icon(LatchIcons.ArrowDownward, null, tint = ColorGraphDownload, modifier = Modifier.size(14.dp))
                    val (dl, dlU) = formatBytes(record.downloadBytes)
                    Text("$dl $dlU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = satoshiFontFamily())
                    Spacer(Modifier.width(10.dp))
                    Icon(LatchIcons.ArrowUpward, null, tint = ColorGraphUpload, modifier = Modifier.size(14.dp))
                    val (ul, ulU) = formatBytes(record.uploadBytes)
                    Text("$ul $ulU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = satoshiFontFamily())
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live session card (unchanged from before)
// ---------------------------------------------------------------------------

@Composable
private fun LiveSessionCard(
    startTimeMillis: Long,
    usage: DataUsage,
    latestRxBps: Long,
    latestTxBps: Long,
    speedUnit: String,
) {
    val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
    val isAmoled = usePureBlack && LocalIsDarkTheme.current

    var duration by remember(startTimeMillis) {
        mutableLongStateOf(System.currentTimeMillis() - startTimeMillis)
    }
    LaunchedEffect(startTimeMillis) {
        while (true) {
            duration = System.currentTimeMillis() - startTimeMillis
            delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        border = if (isAmoled) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )
                Text(
                    text = formatDurationDynamic(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = satoshiFontFamily(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DataUsageDonut(data = usage, modifier = Modifier.size(96.dp), isAmoled = isAmoled)
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val (rxValue, rxUnit) = formatBitsPerSecond(latestRxBps, speedUnit)
                    val (txValue, txUnit) = formatBitsPerSecond(latestTxBps, speedUnit)
                    RateChip(LatchIcons.ArrowUpward, "$txValue $txUnit", ColorGraphUpload)
                    RateChip(LatchIcons.ArrowDownward, "$rxValue $rxUnit", ColorGraphDownload)
                }
            }
        }
    }
}

@Composable
private fun RateChip(icon: ImageVector, text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
