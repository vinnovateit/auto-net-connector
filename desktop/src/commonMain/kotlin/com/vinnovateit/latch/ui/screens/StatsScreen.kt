package com.vinnovateit.latch.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.core.domain.SessionRepository
import com.vinnovateit.latch.core.model.AggregatedDayRecord
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.HistoryChartItem
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.StatsOverviewMetrics
import com.vinnovateit.latch.core.model.computeMetrics
import com.vinnovateit.latch.core.platform.PlatformServices
import com.vinnovateit.latch.core.settings.SettingsManager
import com.vinnovateit.latch.core.stats.StatsInsights
import com.vinnovateit.latch.core.stats.computeChartItems
import com.vinnovateit.latch.core.stats.computeStatsInsights
import com.vinnovateit.latch.core.stats.formatBitsPerSecond
import com.vinnovateit.latch.core.stats.formatBytes
import com.vinnovateit.latch.core.stats.formatDate
import com.vinnovateit.latch.core.stats.formatDisplayDate
import com.vinnovateit.latch.core.stats.formatDurationDynamic
import com.vinnovateit.latch.core.stats.generatePortalHtmlReport
import com.vinnovateit.latch.desktop.resources.Res
import com.vinnovateit.latch.desktop.resources.stats_empty_message
import com.vinnovateit.latch.desktop.resources.stats_title
import com.vinnovateit.latch.ui.components.DataUsageDonut
import com.vinnovateit.latch.ui.components.LatchDetailHeader
import com.vinnovateit.latch.ui.components.LatchIcons
import com.vinnovateit.latch.ui.theme.LocalIsDarkTheme
import com.vinnovateit.latch.ui.theme.StatsColorPalettes
import com.vinnovateit.latch.ui.theme.satoshiFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.io.File

fun groupedItemShape(index: Int, totalCount: Int, cornerRadius: Dp = 24.dp, innerRadius: Dp = 4.dp): Shape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(cornerRadius)
        index == 0 -> RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
        index == totalCount - 1 -> RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = cornerRadius, bottomEnd = cornerRadius)
        else -> RoundedCornerShape(innerRadius)
    }
}

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
    val chartPalette by SettingsManager.chartPalette.collectAsStateWithLifecycle()
    val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
    val isAmoled = usePureBlack && LocalIsDarkTheme.current
    val (dlColor, ulColor) = StatsColorPalettes.resolveColors(chartPalette)

    LaunchedEffect(Unit) {
        val userId = platform.credentials.userId()
        val password = platform.credentials.password()
        if (!userId.isNullOrBlank() && !password.isNullOrBlank()) {
            sessions.syncPortalHistory(userId, password)
        }
    }

    val nonZeroHistory = remember(portalHistory) {
        portalHistory.filter { it.uploadBytes > 0L || it.downloadBytes > 0L }
    }
    val metrics = remember(nonZeroHistory) { computeMetrics(nonZeroHistory) }
    val insights = remember(nonZeroHistory) { computeStatsInsights(nonZeroHistory) }
    val chartItems = remember(nonZeroHistory, liveStatus) {
        val liveRx = liveStatus?.totalRxBytes ?: 0L
        val liveTx = liveStatus?.totalTxBytes ?: 0L
        computeChartItems(nonZeroHistory, liveRxBytes = liveRx, liveTxBytes = liveTx)
    }

    val todayKey = remember { formatDate(System.currentTimeMillis(), "yyyy-MM-dd") }
    val todaySessions = remember(nonZeroHistory, todayKey) {
        nonZeroHistory.filter { it.loginTime > 0 && formatDate(it.loginTime, "yyyy-MM-dd") == todayKey }
    }
    val olderDayRecords = remember(nonZeroHistory, todayKey) {
        nonZeroHistory
            .filter { it.loginTime > 0 && formatDate(it.loginTime, "yyyy-MM-dd") != todayKey }
            .groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }
            .map { (_, daySessions) ->
                val first = daySessions.first()
                val dl = daySessions.sumOf { it.downloadBytes }
                val ul = daySessions.sumOf { it.uploadBytes }
                val total = daySessions.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
                val totalDur = daySessions.sumOf { it.durationMillis }
                AggregatedDayRecord(
                    dayTimestamp = first.loginTime,
                    dateFormatted = formatDate(first.loginTime, "EEE, dd MMM yyyy"),
                    downloadBytes = dl,
                    uploadBytes = ul,
                    totalBytes = total,
                    downloadFormatted = formatBytes(dl),
                    uploadFormatted = formatBytes(ul),
                    totalFormatted = formatBytes(total),
                    sessionCount = daySessions.size,
                    totalDurationMillis = totalDur,
                    durationFormatted = formatDurationDynamic(totalDur),
                )
            }
            .sortedByDescending { it.dayTimestamp }
    }

    var displayedOlderDaysCount by remember { mutableIntStateOf(30) }
    val visibleOlderDays = remember(olderDayRecords, displayedOlderDaysCount) {
        olderDayRecords.take(displayedOlderDaysCount)
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        LatchDetailHeader(
            title = stringResource(Res.string.stats_title),
            onBack = onBack,
            actions = {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = LatchIcons.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Resync History") },
                            onClick = {
                                menuExpanded = false
                                val userId = platform.credentials.userId()
                                val password = platform.credentials.password()
                                if (!userId.isNullOrBlank() && !password.isNullOrBlank()) {
                                    coroutineScope.launch {
                                        sessions.syncPortalHistory(userId, password, force = true)
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export HTML Report") },
                            onClick = {
                                menuExpanded = false
                                coroutineScope.launch {
                                    try {
                                        val userHome = System.getProperty("user.home") ?: "."
                                        val downloadsDir = File(userHome, "Downloads").takeIf { it.exists() && it.isDirectory }
                                            ?: File(userHome)
                                        val reportFile = File(downloadsDir, "latch-session-report-${System.currentTimeMillis()}.html")
                                        reportFile.outputStream().use { stream ->
                                            generatePortalHtmlReport(
                                                sessions = nonZeroHistory,
                                                outputStream = stream,
                                                appVersion = "Desktop",
                                                userId = platform.credentials.userId() ?: ""
                                            )
                                        }
                                        platform.systemActions.openUrl(reportFile.toURI().toString())
                                    } catch (_: Exception) {}
                                }
                            },
                        )
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        dlColor = dlColor,
                        ulColor = ulColor,
                    )
                }
            }

            if (nonZeroHistory.isNotEmpty()) {
                // Hero Total Data Used & DL/UL summary
                item {
                    StatsMetricsSummary(
                        metrics = metrics,
                        dlColor = dlColor,
                        ulColor = ulColor,
                    )
                }

                // Usage insights rows
                item {
                    UsageInsightsCards(insights = insights)
                }

                // Daily usage bar chart (All-time history with 0-gap Canvas bars & centering)
                item {
                    HistoryBarChart(
                        chartItems = chartItems,
                        dlColor = dlColor,
                        ulColor = ulColor,
                        isAmoled = isAmoled,
                    )
                }
            }

            // Portal session cards
            if (nonZeroHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.stats_empty_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                if (todaySessions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Today's Sessions",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                    itemsIndexed(todaySessions, key = { _, session -> "today_${session.loginTime}" }) { index, session ->
                        TodaySessionListItem(
                            session = session,
                            shape = groupedItemShape(index, todaySessions.size),
                            dlColor = dlColor,
                            ulColor = ulColor,
                        )
                    }
                }

                if (olderDayRecords.isNotEmpty()) {
                    item {
                        Text(
                            text = "Previous Days",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                    itemsIndexed(visibleOlderDays, key = { _, record -> "day_${record.dayTimestamp}" }) { index, record ->
                        if (index >= visibleOlderDays.size - 5 && displayedOlderDaysCount < olderDayRecords.size) {
                            LaunchedEffect(Unit) {
                                displayedOlderDaysCount = (displayedOlderDaysCount + 30).coerceAtMost(olderDayRecords.size)
                            }
                        }
                        DayAggregateListItem(
                            record = record,
                            shape = groupedItemShape(index, visibleOlderDays.size),
                            dlColor = dlColor,
                            ulColor = ulColor,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero Metrics Summary
// ---------------------------------------------------------------------------

@Composable
private fun StatsMetricsSummary(
    metrics: StatsOverviewMetrics,
    dlColor: Color,
    ulColor: Color,
    modifier: Modifier = Modifier,
) {
    val totalFmt = formatBytes(metrics.totalBytes)
    val dlFmt = formatBytes(metrics.totalDownloadBytes)
    val ulFmt = formatBytes(metrics.totalUploadBytes)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Total data used",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = satoshiFontFamily(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = totalFmt.first,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                fontFamily = satoshiFontFamily(),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = totalFmt.second,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
                fontFamily = satoshiFontFamily(),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = LatchIcons.ArrowDownward,
                    contentDescription = "Downloaded",
                    tint = dlColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${dlFmt.first} ${dlFmt.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = LatchIcons.ArrowUpward,
                    contentDescription = "Uploaded",
                    tint = ulColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${ulFmt.first} ${ulFmt.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        GameStatRow(label = "Portal logins", value = "${metrics.totalSessions}")
    }
}

@Composable
fun GameStatRow(
    label: String,
    value: String,
    unit: String = "",
    sublabel: String = "",
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = satoshiFontFamily(),
            )
            if (sublabel.isNotBlank()) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontFamily = satoshiFontFamily(),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontFamily = satoshiFontFamily(),
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 1.dp),
                    fontFamily = satoshiFontFamily(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Usage Insights
// ---------------------------------------------------------------------------

@Composable
private fun UsageInsightsCards(
    insights: StatsInsights,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            GameStatRow(
                label = "Daily average",
                value = insights.dailyAverageFormatted.first,
                unit = insights.dailyAverageFormatted.second,
            )
            GameStatRow(
                label = "Highest usage day",
                value = insights.highestUsageDayFormatted,
                sublabel = if (insights.highestUsageDayDate != "N/A") insights.highestUsageDayDate else "",
            )
            GameStatRow(
                label = "Peak usage window",
                value = insights.peakUsageTimeWindow,
            )
            GameStatRow(
                label = "Active streak",
                value = "${insights.currentStreakDays}",
                unit = if (insights.currentStreakDays == 1) "day" else "days",
            )
            GameStatRow(
                label = "Max streak",
                value = "${insights.longestStreakDays}",
                unit = if (insights.longestStreakDays == 1) "day" else "days",
            )
            GameStatRow(
                label = "Active days",
                value = "${insights.activeDaysCount}",
                unit = if (insights.activeDaysCount == 1) "day" else "days",
            )
            GameStatRow(
                label = "Longest session",
                value = insights.mostActiveSessionDurationFormatted,
            )
            if (insights.nightOwlPercentage >= 10) {
                GameStatRow(
                    label = "Night owl traffic",
                    value = insights.nightOwlFormatted.first,
                    unit = insights.nightOwlFormatted.second,
                    sublabel = "${insights.nightOwlPercentage}% after midnight (12 - 6 AM)",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Daily Usage Bar Chart (All-time history with 0-gap Canvas bars & centering)
// ---------------------------------------------------------------------------

@Immutable
data class DesktopChartDetailState(
    val usage: DataUsage,
    val label: String,
    val sessionCount: Int = 0,
    val durationFormatted: String = "",
)

@Composable
private fun HistoryBarChart(
    chartItems: List<HistoryChartItem>,
    dlColor: Color,
    ulColor: Color,
    isAmoled: Boolean,
) {
    if (chartItems.isEmpty()) return

    val todayIdx = remember(chartItems) {
        val todayKey = formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
        val exact = chartItems.indexOfLast {
            it is HistoryChartItem.BarData && formatDate(it.timestamp, "yyyy-MM-dd") == todayKey
        }
        val idx = if (exact != -1) exact else chartItems.indexOfLast { it is HistoryChartItem.BarData }
        idx.coerceAtLeast(0)
    }

    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = todayIdx)
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val initialBarItem = remember(chartItems, todayIdx) {
        chartItems.getOrNull(todayIdx) as? HistoryChartItem.BarData
    }
    var selectedIndex by remember(chartItems, todayIdx) {
        mutableIntStateOf(if (initialBarItem != null) todayIdx else -1)
    }
    var displayedData by remember(chartItems, todayIdx) {
        mutableStateOf(
            if (initialBarItem != null) {
                DesktopChartDetailState(
                    usage = initialBarItem.usage,
                    label = initialBarItem.formattedDate.ifBlank {
                        formatDisplayDate(initialBarItem.timestamp)
                    },
                    sessionCount = initialBarItem.sessionCount,
                    durationFormatted = initialBarItem.durationFormatted,
                )
            } else {
                DesktopChartDetailState(DataUsage(0, 0), "Total Data Usage")
            }
        )
    }

    var visibleMaxUsage by remember { mutableLongStateOf(1L) }
    LaunchedEffect(chartItems, lazyListState) {
        snapshotFlow {
            val visible = lazyListState.layoutInfo.visibleItemsInfo
            var maxV = 1L
            for (v in visible) {
                val item = chartItems.getOrNull(v.index)
                if (item is HistoryChartItem.BarData) {
                    val tot = item.usage.rxBytes + item.usage.txBytes
                    if (tot > maxV) maxV = tot
                }
            }
            maxV
        }.distinctUntilChanged().collect {
            visibleMaxUsage = it
        }
    }

    val animatedMaxUsage by animateFloatAsState(
        targetValue = visibleMaxUsage.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "DesktopBarMaxUsageSpring",
    )

    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val headerTitle by remember(chartItems, lazyListState) {
        derivedStateOf {
            val visible = lazyListState.layoutInfo.visibleItemsInfo
            val centerItem = if (visible.isNotEmpty()) {
                val centerOffset = (lazyListState.layoutInfo.viewportStartOffset + lazyListState.layoutInfo.viewportEndOffset) / 2
                visible.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - centerOffset) }
            } else null

            val targetItem = (centerItem?.index?.let { chartItems.getOrNull(it) })
                ?: chartItems.getOrNull(todayIdx)

            val timestamp = when (targetItem) {
                is HistoryChartItem.BarData -> targetItem.timestamp
                is HistoryChartItem.CollapsedMonth -> targetItem.timestamp
                else -> System.currentTimeMillis()
            }
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val calYear = cal.get(java.util.Calendar.YEAR)
            val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.US).format(cal.time)
            if (calYear == currentYear) monthName else "$monthName $calYear"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = satoshiFontFamily(),
            )

            Spacer(Modifier.height(16.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val barWidth = 10.dp
                val barAreaHeight = 120.dp
                val centerPadding = (maxWidth - barWidth) / 2f

                LazyRow(
                    state = lazyListState,
                    modifier = Modifier.height(130.dp),
                    contentPadding = PaddingValues(horizontal = centerPadding),
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                    horizontalArrangement = Arrangement.spacedBy(0.5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    itemsIndexed(
                        items = chartItems,
                        key = { index, item ->
                            when (item) {
                                is HistoryChartItem.BarData -> "bar_${item.timestamp}_$index"
                                is HistoryChartItem.MonthSeparator -> "month_${item.monthName}_$index"
                                is HistoryChartItem.CollapsedMonth -> "collapsed_${item.monthName}_$index"
                            }
                        },
                    ) { idx, item ->
                        when (item) {
                            is HistoryChartItem.BarData -> {
                                DesktopCanvasBar(
                                    modifier = Modifier.width(barWidth).fillMaxHeight(),
                                    usage = item.usage,
                                    maxUsage = { animatedMaxUsage },
                                    isSelected = (idx == selectedIndex),
                                    hasSelection = (selectedIndex != -1),
                                    isAmoled = isAmoled,
                                    barWidth = barWidth,
                                    barAreaHeight = barAreaHeight,
                                    dlColor = dlColor,
                                    ulColor = ulColor,
                                    onTap = {
                                        selectedIndex = idx
                                        displayedData = DesktopChartDetailState(
                                            usage = item.usage,
                                            label = formatDisplayDate(item.timestamp),
                                            sessionCount = item.sessionCount,
                                            durationFormatted = item.durationFormatted,
                                        )
                                        coroutineScope.launch {
                                            val layoutInfo = lazyListState.layoutInfo
                                            val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                                            if (targetItem != null) {
                                                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                                                val itemCenter = targetItem.offset + targetItem.size / 2
                                                val delta = (itemCenter - viewportCenter).toFloat()
                                                if (kotlin.math.abs(delta) > 1f) {
                                                    lazyListState.animateScrollBy(
                                                        value = delta,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            is HistoryChartItem.MonthSeparator -> {
                                Box(
                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = item.monthName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontFamily = satoshiFontFamily(),
                                        modifier = Modifier.rotate(-90f),
                                    )
                                }
                            }
                            is HistoryChartItem.CollapsedMonth -> {
                                Box(
                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = item.monthName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontFamily = satoshiFontFamily(),
                                        modifier = Modifier.rotate(-90f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            DesktopStatDetailRow(data = displayedData, dlColor = dlColor, ulColor = ulColor)
        }
    }
}

@Composable
private fun DesktopCanvasBar(
    modifier: Modifier = Modifier,
    usage: DataUsage,
    maxUsage: () -> Float,
    isSelected: Boolean,
    hasSelection: Boolean = false,
    isAmoled: Boolean = false,
    barWidth: Dp,
    barAreaHeight: Dp,
    dlColor: Color,
    ulColor: Color,
    onTap: () -> Unit,
) {
    val total = usage.rxBytes + usage.txBytes
    val uploadFrac = if (total > 0) usage.txBytes.toFloat() / total.toFloat() else 0f
    val emptyColor = if (isAmoled) Color(0xFF262626) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .width(barWidth)
            .height(barAreaHeight)
            .graphicsLayer {
                alpha = if (isSelected) 1f else if (hasSelection) 0.45f else 1f
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    ) {
        val maxVal = maxUsage()
        val currentFrac = if (maxVal > 0f && total > 0L) {
            (total.toFloat() / maxVal).coerceIn(0.04f, 0.96f)
        } else 0.04f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
        val strokeWidth = 1.5.dp.toPx()
        val inset = if (isAmoled) strokeWidth / 2 else 0f
        val drawWidth = (size.width - inset * 2).coerceAtLeast(0f)

        if (total > 0) {
            val rawBarHeight = (size.height * currentFrac).coerceAtLeast(6.dp.toPx())
            val drawHeight = (rawBarHeight - inset * 2).coerceAtLeast(0f)
            val startY = size.height - rawBarHeight + inset
            val topLeftOffset = Offset(inset, startY)

            val ulH = if (uploadFrac > 0f) (drawHeight * uploadFrac).coerceAtLeast(2.dp.toPx()) else 0f
            val dlH = (drawHeight - ulH).coerceAtLeast(0f)

            if (ulH > 0f) {
                drawRoundRect(
                    color = ulColor,
                    topLeft = topLeftOffset,
                    size = Size(drawWidth, ulH),
                    cornerRadius = cornerRadius,
                )
            }

            if (dlH > 0f) {
                val dlTopY = topLeftOffset.y + ulH
                drawRoundRect(
                    color = dlColor,
                    topLeft = Offset(topLeftOffset.x, dlTopY),
                    size = Size(drawWidth, dlH),
                    cornerRadius = cornerRadius,
                )
            }
        } else {
            val emptyHeight = 6.dp.toPx()
            drawRoundRect(
                color = emptyColor,
                topLeft = Offset(0f, size.height - emptyHeight),
                size = Size(size.width, emptyHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}

@Composable
private fun DesktopStatDetailRow(
    data: DesktopChartDetailState,
    dlColor: Color,
    ulColor: Color,
) {
    val (currentUsage, label, sessionCount, durationFormatted) = data
    val (totalFmt, dlFmt, ulFmt) = remember(currentUsage) {
        Triple(
            formatBytes(currentUsage.rxBytes + currentUsage.txBytes),
            formatBytes(currentUsage.rxBytes),
            formatBytes(currentUsage.txBytes),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "${totalFmt.first} ${totalFmt.second}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = satoshiFontFamily(),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = satoshiFontFamily(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LatchIcons.ArrowDownward, null, tint = dlColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${dlFmt.first} ${dlFmt.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = satoshiFontFamily(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LatchIcons.ArrowUpward, null, tint = ulColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${ulFmt.first} ${ulFmt.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = satoshiFontFamily(),
                )
            }
        }
        if (sessionCount > 0 || (durationFormatted.isNotBlank() && durationFormatted != "0s")) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sessionCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = "$sessionCount ${if (sessionCount == 1) "session" else "sessions"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                }
                if (durationFormatted.isNotBlank() && durationFormatted != "0s") {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "⏱ $durationFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Grouped List Items
// ---------------------------------------------------------------------------

@Composable
fun TodaySessionListItem(
    session: PortalSessionRecord,
    shape: Shape = RoundedCornerShape(16.dp),
    dlColor: Color = MaterialTheme.colorScheme.primary,
    ulColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
    val isAmoled = usePureBlack && LocalIsDarkTheme.current

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (isAmoled) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        val totalFormatted = remember(session.totalBytes, session.uploadBytes, session.downloadBytes) {
            val effectiveTotal = if (session.totalBytes > 0) session.totalBytes else (session.uploadBytes + session.downloadBytes)
            formatBytes(effectiveTotal)
        }
        val dlFormatted = remember(session.downloadBytes) { formatBytes(session.downloadBytes) }
        val ulFormatted = remember(session.uploadBytes) { formatBytes(session.uploadBytes) }
        val durationStr = session.durationFormatted.ifBlank { formatDurationDynamic(session.durationMillis) }

        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.location.ifBlank { "Session" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = satoshiFontFamily(),
                    )
                    if (session.isManual) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = "Manual",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (session.loginTime > 0) formatDate(session.loginTime, "hh:mm a") else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = satoshiFontFamily(),
                    )
                }
            },
            supportingContent = {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowDownward, null, tint = dlColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${dlFormatted.first} ${dlFormatted.second}", style = MaterialTheme.typography.labelSmall, fontFamily = satoshiFontFamily())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowUpward, null, tint = ulColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${ulFormatted.first} ${ulFormatted.second}", style = MaterialTheme.typography.labelSmall, fontFamily = satoshiFontFamily())
                    }
                    if (durationStr.isNotBlank()) {
                        Text(durationStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontFamily = satoshiFontFamily())
                    }
                }
            },
            trailingContent = {
                Text(
                    text = "${totalFormatted.first} ${totalFormatted.second}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
fun DayAggregateListItem(
    record: AggregatedDayRecord,
    shape: Shape = RoundedCornerShape(16.dp),
    dlColor: Color = MaterialTheme.colorScheme.primary,
    ulColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
    val isAmoled = usePureBlack && LocalIsDarkTheme.current

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (isAmoled) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        val sessionLabel = if (record.sessionCount == 1) "1 session" else "${record.sessionCount} sessions"

        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.dateFormatted,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = satoshiFontFamily(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = sessionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                }
            },
            supportingContent = {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowDownward, null, tint = dlColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${record.downloadFormatted.first} ${record.downloadFormatted.second}", style = MaterialTheme.typography.labelSmall, fontFamily = satoshiFontFamily())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowUpward, null, tint = ulColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${record.uploadFormatted.first} ${record.uploadFormatted.second}", style = MaterialTheme.typography.labelSmall, fontFamily = satoshiFontFamily())
                    }
                    if (record.durationFormatted.isNotBlank()) {
                        Text(
                            text = record.durationFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    text = "${record.totalFormatted.first} ${record.totalFormatted.second}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// ---------------------------------------------------------------------------
// Live session card
// ---------------------------------------------------------------------------

@Composable
private fun LiveSessionCard(
    startTimeMillis: Long,
    usage: DataUsage,
    latestRxBps: Long,
    latestTxBps: Long,
    speedUnit: String,
    dlColor: Color,
    ulColor: Color,
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
                DataUsageDonut(data = usage, modifier = Modifier.size(96.dp), isAmoled = isAmoled, dlColor = dlColor, ulColor = ulColor)
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val (rxValue, rxUnit) = formatBitsPerSecond(latestRxBps, speedUnit)
                    val (txValue, txUnit) = formatBitsPerSecond(latestTxBps, speedUnit)
                    RateChip(LatchIcons.ArrowUpward, "$txValue $txUnit", ulColor)
                    RateChip(LatchIcons.ArrowDownward, "$rxValue $rxUnit", dlColor)
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
            fontFamily = satoshiFontFamily(),
        )
    }
}
