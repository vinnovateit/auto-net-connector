package com.vinnovateit.latch.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.core.domain.SessionRepository
import com.vinnovateit.latch.core.model.AggregatedDayRecord
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.DateRangeFilter
import com.vinnovateit.latch.core.model.HistoryChartItem
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.platform.PlatformServices
import com.vinnovateit.latch.core.settings.SettingsManager
import com.vinnovateit.latch.core.stats.formatBitsPerSecond
import com.vinnovateit.latch.core.stats.formatBytes
import com.vinnovateit.latch.core.stats.formatDate
import com.vinnovateit.latch.core.stats.formatDisplayDate
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
import org.jetbrains.compose.resources.stringResource
import java.util.Calendar

fun groupedItemShape(index: Int, totalCount: Int, cornerRadius: Dp = 24.dp, innerRadius: Dp = 4.dp): Shape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(cornerRadius)
        index == 0 -> RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
        index == totalCount - 1 -> RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = cornerRadius, bottomEnd = cornerRadius)
        else -> RoundedCornerShape(innerRadius)
    }
}

/**
 * Session history driven by portal history from the Pronto Networks captive portal.
 *
 * Shows live connection speed while connected, KPI summary tiles, daily bar chart with
 * date range filtering and month grouping, and Material 3 grouped list items for today's
 * sessions and aggregated older days.
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

    LaunchedEffect(Unit) {
        val userId = platform.credentials.userId()
        val password = platform.credentials.password()
        if (!userId.isNullOrBlank() && !password.isNullOrBlank()) {
            sessions.syncPortalHistory(userId, password)
        }
    }

    // Filter out 0B entries
    val nonZeroHistory = remember(portalHistory) {
        portalHistory.filter { it.uploadBytes > 0L || it.downloadBytes > 0L }
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
                    }
                }
            },
        )

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
                PortalTotalsRow(history = nonZeroHistory)
            }

            // Daily usage bar chart from portal history
            if (nonZeroHistory.isNotEmpty()) {
                item {
                    PortalDailyBarChart(history = nonZeroHistory)
                }
            }

            // Portal session cards
            if (nonZeroHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
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
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            fontFamily = satoshiFontFamily(),
                        )
                    }
                    itemsIndexed(todaySessions, key = { _, session -> "today_${session.loginTime}" }) { index, session ->
                        TodaySessionListItem(
                            session = session,
                            shape = groupedItemShape(index, todaySessions.size),
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
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
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
                        )
                    }
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
    var selectedFilter by remember { mutableStateOf(DateRangeFilter.THIS_MONTH) }
    var selectedTimestamp by remember { mutableStateOf<Long?>(null) }

    val chartItems = remember(history, selectedFilter) {
        val groupedByDay = history
            .filter { it.loginTime > 0 }
            .groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }
            .mapValues { (_, list) ->
                DataUsage(
                    rxBytes = list.sumOf { it.downloadBytes },
                    txBytes = list.sumOf { it.uploadBytes },
                )
            }

        val now = Calendar.getInstance()
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        when (selectedFilter) {
            DateRangeFilter.LAST_30_DAYS -> {
                startCal.add(Calendar.DAY_OF_YEAR, -29)
            }
            DateRangeFilter.LAST_60_DAYS -> {
                startCal.add(Calendar.DAY_OF_YEAR, -59)
            }
            DateRangeFilter.LAST_90_DAYS -> {
                startCal.add(Calendar.DAY_OF_YEAR, -89)
            }
            DateRangeFilter.THIS_MONTH -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
            }
            DateRangeFilter.THIS_YEAR -> {
                startCal.set(Calendar.DAY_OF_YEAR, 1)
                endCal.set(Calendar.MONTH, Calendar.DECEMBER)
                endCal.set(Calendar.DAY_OF_MONTH, 31)
            }
            DateRangeFilter.YTD -> {
                startCal.set(Calendar.DAY_OF_YEAR, 1)
            }
            DateRangeFilter.LAST_YEAR -> {
                startCal.add(Calendar.YEAR, -1)
                startCal.set(Calendar.DAY_OF_YEAR, 1)
                endCal.add(Calendar.YEAR, -1)
                endCal.set(Calendar.MONTH, Calendar.DECEMBER)
                endCal.set(Calendar.DAY_OF_MONTH, 31)
            }
            DateRangeFilter.ALL_TIME -> {
                val earliest = history.minOfOrNull { it.loginTime } ?: (System.currentTimeMillis() - 30L * 86400000L)
                startCal.timeInMillis = earliest
                startCal.set(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val currentYear = now.get(Calendar.YEAR)
        val items = mutableListOf<HistoryChartItem>()
        var lastMonth = -1

        val cursor = startCal.clone() as Calendar
        while (!cursor.after(endCal)) {
            val dayTimestamp = cursor.timeInMillis
            val currentMonth = cursor.get(Calendar.MONTH)
            val itemYear = cursor.get(Calendar.YEAR)
            if (lastMonth != -1 && currentMonth != lastMonth) {
                val monthPattern = if (itemYear == currentYear) "MMM" else "MMM yyyy"
                items.add(HistoryChartItem.MonthSeparator(formatDate(dayTimestamp, monthPattern)))
            }
            lastMonth = currentMonth

            val key = formatDate(dayTimestamp, "yyyy-MM-dd")
            val usage = groupedByDay[key] ?: DataUsage(0, 0)
            val label = formatDate(dayTimestamp, "dd")
            items.add(HistoryChartItem.BarData(usage, label, dayTimestamp))
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        items.distinct()
    }

    if (chartItems.isEmpty()) return

    val maxBytes = remember(chartItems) {
        chartItems.filterIsInstance<HistoryChartItem.BarData>()
            .maxOfOrNull { it.usage.rxBytes + it.usage.txBytes }
            ?.coerceAtLeast(1L) ?: 1L
    }

    val lazyListState = rememberLazyListState()
    LaunchedEffect(chartItems) {
        if (chartItems.isNotEmpty()) {
            lazyListState.scrollToItem(chartItems.size - 1)
        }
        selectedTimestamp = null
    }

    val totalUsageData = remember(chartItems) {
        val totalRx = chartItems.filterIsInstance<HistoryChartItem.BarData>().sumOf { it.usage.rxBytes }
        val totalTx = chartItems.filterIsInstance<HistoryChartItem.BarData>().sumOf { it.usage.txBytes }
        DataUsage(totalRx, totalTx)
    }

    val selectedBar = chartItems.filterIsInstance<HistoryChartItem.BarData>()
        .find { it.timestamp == selectedTimestamp }

    val displayedUsage = selectedBar?.usage ?: totalUsageData
    val displayedLabel = if (selectedBar != null) {
        formatDisplayDate(selectedBar.timestamp)
    } else {
        "Total Usage (${selectedFilter.label})"
    }

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val headerTitle = remember(chartItems, selectedTimestamp, selectedFilter) {
        val lastTimestamp = selectedTimestamp
            ?: chartItems.filterIsInstance<HistoryChartItem.BarData>().lastOrNull()?.timestamp
            ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = lastTimestamp }
        if (cal.get(Calendar.YEAR) == currentYear) {
            java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(cal.time)
        } else {
            java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )

                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { menuExpanded = true },
                        label = { Text(selectedFilter.label, style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            Icon(
                                imageVector = LatchIcons.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DateRangeFilter.values().forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label) },
                                onClick = {
                                    selectedFilter = filter
                                    selectedTimestamp = null
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyRow(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                itemsIndexed(chartItems) { _, item ->
                    when (item) {
                        is HistoryChartItem.BarData -> {
                            val total = item.usage.rxBytes + item.usage.txBytes
                            val rxFrac = (item.usage.rxBytes.toFloat() / maxBytes).coerceIn(if (total > 0) 0.05f else 0.02f, 1f)
                            val txFrac = (item.usage.txBytes.toFloat() / maxBytes).coerceIn(if (total > 0) 0.05f else 0.02f, 1f)
                            val isSelected = (item.timestamp == selectedTimestamp)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(36.dp)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        selectedTimestamp = if (isSelected) null else item.timestamp
                                    },
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    if (total > 0) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(18.dp)
                                                    .fillMaxHeight(txFrac)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(ColorGraphUpload),
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(18.dp)
                                                    .fillMaxHeight(rxFrac)
                                                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                                    .background(ColorGraphDownload),
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                val labelBg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                val labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(labelBg),
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = labelColor,
                                        fontFamily = satoshiFontFamily(),
                                    )
                                }
                            }
                        }
                        is HistoryChartItem.MonthSeparator -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item.monthName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = satoshiFontFamily(),
                                    modifier = Modifier.rotate(-90f),
                                )
                            }
                        }
                        is HistoryChartItem.CollapsedMonth -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item.monthName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = satoshiFontFamily(),
                                    modifier = Modifier.rotate(-90f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Detailed stats row underneath chart
            val (totalVal, totalUnit) = formatBytes(displayedUsage.rxBytes + displayedUsage.txBytes)
            val (dlVal, dlUnit) = formatBytes(displayedUsage.rxBytes)
            val (ulVal, ulUnit) = formatBytes(displayedUsage.txBytes)

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$totalVal $totalUnit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = satoshiFontFamily(),
                )
                Text(
                    text = displayedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = satoshiFontFamily(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowDownward, contentDescription = null, tint = ColorGraphDownload, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$dlVal $dlUnit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = satoshiFontFamily())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowUpward, contentDescription = null, tint = ColorGraphUpload, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$ulVal $ulUnit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = satoshiFontFamily())
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
                        Icon(LatchIcons.ArrowDownward, null, tint = ColorGraphDownload, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${dlFormatted.first} ${dlFormatted.second}", style = MaterialTheme.typography.labelSmall, fontFamily = satoshiFontFamily())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowUpward, null, tint = ColorGraphUpload, modifier = Modifier.size(14.dp))
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
                        Icon(LatchIcons.ArrowDownward, null, tint = ColorGraphDownload, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${record.downloadFormatted.first} ${record.downloadFormatted.second}", style = MaterialTheme.typography.labelSmall, fontFamily = satoshiFontFamily())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LatchIcons.ArrowUpward, null, tint = ColorGraphUpload, modifier = Modifier.size(14.dp))
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
