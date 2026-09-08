package com.vinnovateit.latch.features.stats.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import com.vinnovateit.latch.common.util.StatsColorPalettes
import com.vinnovateit.latch.features.settings.manager.SettingsManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.common.util.NoDataCard
import com.vinnovateit.latch.common.util.formatBytes
import com.vinnovateit.latch.common.util.formatDate
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.ui.theme.ColorGraphDownload
import com.vinnovateit.latch.ui.theme.ColorGraphUpload
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Immutable
sealed class HistoryChartItem {
    data class BarData(
        val usage: DataUsage,
        val label: String,
        val timestamp: Long,
        val formattedDate: String = "",
        val sessionCount: Int = 0,
        val durationMillis: Long = 0L,
        val durationFormatted: String = ""
    ) : HistoryChartItem()
    data class MonthSeparator(val monthName: String) : HistoryChartItem()
}

@Immutable
data class ChartDetailState(
    val usage: DataUsage,
    val label: String,
    val sessionCount: Int = 0,
    val durationFormatted: String = ""
)

@Composable
fun HistoryBarChart(
    history: List<HistoryChartItem>,
    selectedFilter: com.vinnovateit.latch.features.stats.DateRangeFilter = com.vinnovateit.latch.features.stats.DateRangeFilter.THIS_MONTH,
    onFilterSelected: ((com.vinnovateit.latch.features.stats.DateRangeFilter) -> Unit)? = null
) {
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val headerTitle = remember(history, selectedFilter) {
        val lastTimestamp = history.filterIsInstance<HistoryChartItem.BarData>().lastOrNull()?.timestamp
            ?: System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastTimestamp }
        if (cal.get(java.util.Calendar.YEAR) == currentYear) {
            SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
        } else {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        }
    }

    var showAdvancedSheet by remember { mutableStateOf(false) }
    val quickFilters = remember {
        listOf(
            com.vinnovateit.latch.features.stats.DateRangeFilter.LAST_30_DAYS,
            com.vinnovateit.latch.features.stats.DateRangeFilter.LAST_60_DAYS,
            com.vinnovateit.latch.features.stats.DateRangeFilter.LAST_90_DAYS,
            com.vinnovateit.latch.features.stats.DateRangeFilter.THIS_MONTH,
            com.vinnovateit.latch.features.stats.DateRangeFilter.THIS_YEAR,
            com.vinnovateit.latch.features.stats.DateRangeFilter.YTD
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (onFilterSelected != null) {
            val quickFilterScrollState = rememberScrollState()
            val totalChips = quickFilters.size + 1
            fun chipShape(index: Int) = when (index) {
                0 -> RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                totalChips - 1 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(4.dp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(quickFilterScrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickFilters.forEachIndexed { index, filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterSelected(filter) },
                        shape = chipShape(index),
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }

                val isAdvancedSelected = selectedFilter !in quickFilters
                FilterChip(
                    selected = isAdvancedSelected,
                    onClick = { showAdvancedSheet = true },
                    shape = chipShape(totalChips - 1),
                    label = {
                        Text(
                            text = if (isAdvancedSelected) selectedFilter.label else "Advanced...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isAdvancedSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Advanced filters",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isAdvancedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showAdvancedSheet && onFilterSelected != null) {
            StatsAdvancedFilterBottomSheet(
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
                onDismiss = { showAdvancedSheet = false }
            )
        }

        if (history.isNotEmpty()) {
            HistoryBarChartContent(chartItems = history)
        } else {
            NoDataCard("No stats available. Connect to Wi-Fi to start tracking your usage.")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryBarChartContent(chartItems: List<HistoryChartItem>) {

    if (chartItems.filterIsInstance<HistoryChartItem.BarData>().all { it.usage.rxBytes + it.usage.txBytes == 0L }) {
        NoDataCard("No stats available. Connect to Wi-Fi to start tracking your usage.")
        return
    }

    val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
    val isAmoled = usePureBlack && com.vinnovateit.latch.ui.theme.LocalIsDarkTheme.current

    val todayIdx = remember(chartItems) {
        val todayKey = formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
        val exact = chartItems.indexOfLast {
            it is HistoryChartItem.BarData && formatDate(it.timestamp, "yyyy-MM-dd") == todayKey
        }
        if (exact != -1) exact else chartItems.indexOfLast { it is HistoryChartItem.BarData }
    }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    val totalUsageData = remember(chartItems) {
        val totalRx = chartItems.filterIsInstance<HistoryChartItem.BarData>().sumOf { it.usage.rxBytes }
        val totalTx = chartItems.filterIsInstance<HistoryChartItem.BarData>().sumOf { it.usage.txBytes }
        DataUsage(totalRx, totalTx)
    }
    val totalSessions = remember(chartItems) {
        chartItems.filterIsInstance<HistoryChartItem.BarData>().sumOf { it.sessionCount }
    }
    val totalDurationFormatted = remember(chartItems) {
        val ms = chartItems.filterIsInstance<HistoryChartItem.BarData>().sumOf { it.durationMillis }
        com.vinnovateit.latch.common.util.formatDurationDynamic(ms)
    }
    val totalUsageDetail = remember(totalUsageData, totalSessions, totalDurationFormatted) {
        ChartDetailState(
            usage = totalUsageData,
            label = "Total Data Usage",
            sessionCount = totalSessions,
            durationFormatted = totalDurationFormatted
        )
    }
    var displayedData by remember { mutableStateOf(totalUsageDetail) }

    val globalMax = remember(chartItems) {
        chartItems.filterIsInstance<HistoryChartItem.BarData>()
            .maxOfOrNull { it.usage.rxBytes + it.usage.txBytes }
            ?.coerceAtLeast(1L) ?: 1L
    }

    val visibleMaxDailyUsage by remember(chartItems) {
        derivedStateOf {
            val visible = lazyListState.layoutInfo.visibleItemsInfo
            var maxBytes = 0L
            for (itemInfo in visible) {
                val item = chartItems.getOrNull(itemInfo.index)
                if (item is HistoryChartItem.BarData) {
                    val sum = item.usage.rxBytes + item.usage.txBytes
                    if (sum > maxBytes) {
                        maxBytes = sum
                    }
                }
            }
            if (maxBytes > 0L) maxBytes else globalMax
        }
    }

    val animatedMaxUsage by animateFloatAsState(
        targetValue = (visibleMaxDailyUsage.toFloat() * 1.15f).coerceAtLeast(1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "BarChartMaxUsageSpring"
    )

    // Center today's bar and select it initially when chartItems change
    LaunchedEffect(chartItems) {
        if (todayIdx in chartItems.indices) {
            lazyListState.scrollToItem(todayIdx)
            val todayItem = chartItems.getOrNull(todayIdx) as? HistoryChartItem.BarData
            if (todayItem != null) {
                selectedIndex = todayIdx
                displayedData = ChartDetailState(
                    usage = todayItem.usage,
                    label = todayItem.formattedDate.ifBlank {
                        com.vinnovateit.latch.common.util.formatDisplayDate(todayItem.timestamp)
                    },
                    sessionCount = todayItem.sessionCount,
                    durationFormatted = todayItem.durationFormatted
                )
            } else {
                selectedIndex = -1
                displayedData = totalUsageDetail
            }
        } else {
            selectedIndex = -1
            displayedData = totalUsageDetail
        }
    }

    var lastCenteredIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(chartItems) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@snapshotFlow -1
            visibleItems.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2
                kotlin.math.abs(itemCenter - viewportCenter)
            }?.index ?: -1
        }.collect { centerIdx ->
            if (centerIdx != -1 && centerIdx != lastCenteredIndex) {
                if (lazyListState.isScrollInProgress) {
                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
                lastCenteredIndex = centerIdx
                val item = chartItems.getOrNull(centerIdx)
                if (item is HistoryChartItem.BarData) {
                    selectedIndex = centerIdx
                    val formattedDate = item.formattedDate.ifBlank {
                        com.vinnovateit.latch.common.util.formatDisplayDate(item.timestamp)
                    }
                    displayedData = ChartDetailState(
                        usage = item.usage,
                        label = formattedDate,
                        sessionCount = item.sessionCount,
                        durationFormatted = item.durationFormatted
                    )
                }
            }
        }
    }

    val chartPalette by SettingsManager.chartPalette.collectAsStateWithLifecycle()
    val (dlColor, ulColor) = StatsColorPalettes.resolveColors(chartPalette)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barWidth = 16.dp
            val rowHeight = 172.dp
            val barAreaHeight = 160.dp
            val centerPadding = ((maxWidth - barWidth) / 2).coerceAtLeast(16.dp)

            LazyRow(
                state = lazyListState,
                modifier = Modifier.height(rowHeight),
                contentPadding = PaddingValues(horizontal = centerPadding),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                itemsIndexed(chartItems, key = { index, item ->
                    when (item) {
                        is HistoryChartItem.BarData -> "bar_${item.timestamp}_$index"
                        is HistoryChartItem.MonthSeparator -> "month_${item.monthName}_$index"
                    }
                }) { idx, item ->
                    when (item) {
                        is HistoryChartItem.BarData -> {
                            Bar(
                                modifier = Modifier
                                    .width(barWidth)
                                    .fillMaxHeight(),
                                usage = item.usage,
                                maxUsage = animatedMaxUsage,
                                isSelected = (idx == selectedIndex),
                                isAmoled = isAmoled,
                                barWidth = barWidth,
                                barAreaHeight = barAreaHeight,
                                dlColor = dlColor,
                                ulColor = ulColor,
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(idx)
                                    }
                                }
                            )
                        }
                        is HistoryChartItem.MonthSeparator -> {
                            MonthSeparator(monthName = item.monthName)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        StatDetailRow(data = displayedData, dlColor = dlColor, ulColor = ulColor)
    }
}

@Composable
private fun MonthSeparator(monthName: String) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = monthName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(-90f)
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun Bar(
    modifier: Modifier = Modifier,
    usage: DataUsage,
    maxUsage: Float,
    isSelected: Boolean,
    isAmoled: Boolean = false,
    barWidth: Dp,
    barAreaHeight: Dp,
    dlColor: Color,
    ulColor: Color,
    onTap: () -> Unit
) {
    val total = usage.rxBytes + usage.txBytes
    val effectiveMax = maxOf(maxUsage, total.toFloat() * 1.15f)
    val totalFrac = (total.toFloat() / effectiveMax).coerceIn(0.04f, 0.88f)

    val uploadFrac = if (total > 0) usage.txBytes.toFloat() / total.toFloat() else 0f
    val downloadFrac = 1f - uploadFrac
    val density = LocalDensity.current
    val barHeightInDp = with(density) {
        if (total > 0) (barAreaHeight.toPx() * totalFrac).toDp().coerceAtLeast(6.dp) else 4.dp
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "BarScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barAreaHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            val emptyColor = if (isAmoled) Color(0xFF262626) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            Canvas(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeightInDp)
            ) {
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                val strokeWidth = 1.5.dp.toPx()
                val inset = if (isAmoled) strokeWidth / 2 else 0f
                val drawWidth = (size.width - inset * 2).coerceAtLeast(0f)
                val drawHeight = (size.height - inset * 2).coerceAtLeast(0f)
                val topLeftOffset = Offset(inset, inset)

                if (total > 0) {
                    val gapPx = if (downloadFrac > 0.05f && uploadFrac > 0.05f) 2.dp.toPx() else 0f
                    val availableHeight = (drawHeight - gapPx).coerceAtLeast(0f)
                    val ulH = if (uploadFrac > 0f) (availableHeight * uploadFrac).coerceAtLeast(2.dp.toPx()) else 0f
                    val dlH = (availableHeight - ulH).coerceAtLeast(0f)

                    // Upload on top
                    if (ulH > 0f) {
                        if (isAmoled) {
                            drawRoundRect(
                                color = ulColor,
                                topLeft = topLeftOffset,
                                size = Size(drawWidth, ulH),
                                cornerRadius = cornerRadius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                        } else {
                            drawRoundRect(
                                color = ulColor,
                                topLeft = topLeftOffset,
                                size = Size(drawWidth, ulH),
                                cornerRadius = cornerRadius
                            )
                        }
                    }

                    // Download below upload
                    if (dlH > 0f) {
                        val dlTopY = topLeftOffset.y + if (ulH > 0f) ulH + gapPx else 0f
                        if (isAmoled) {
                            drawRoundRect(
                                color = dlColor,
                                topLeft = Offset(topLeftOffset.x, dlTopY),
                                size = Size(drawWidth, dlH),
                                cornerRadius = cornerRadius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                        } else {
                            drawRoundRect(
                                color = dlColor,
                                topLeft = Offset(topLeftOffset.x, dlTopY),
                                size = Size(drawWidth, dlH),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                } else {
                    drawRoundRect(
                        color = emptyColor,
                        topLeft = topLeftOffset,
                        size = Size(drawWidth, drawHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun StatDetailRow(
    data: ChartDetailState,
    dlColor: Color,
    ulColor: Color
) {
    val (currentUsage, label, sessionCount, durationFormatted) = data

    val (totalFmt, dlFmt, ulFmt) = remember(currentUsage) {
        Triple(
            formatBytes(currentUsage.rxBytes + currentUsage.txBytes),
            formatBytes(currentUsage.rxBytes),
            formatBytes(currentUsage.txBytes)
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = totalFmt,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith
                        (slideOutVertically { -it } + fadeOut())
            },
            label = "TotalUsageSwitch"
        ) { (v, u) ->
            Text(
                "$v $u",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(dlFmt, label = "DLStat", transitionSpec = { fadeIn() togetherWith fadeOut() }) { (value, unit) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowDownward, null, tint = dlColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$value $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedContent(ulFmt, label = "ULStat", transitionSpec = { fadeIn() togetherWith fadeOut() }) { (value, unit) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowUpward, null, tint = ulColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$value $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (sessionCount > 0 || (durationFormatted.isNotBlank() && durationFormatted != "0s")) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sessionCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "$sessionCount ${if (sessionCount == 1) "session" else "sessions"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (durationFormatted.isNotBlank() && durationFormatted != "0s") {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "⏱ $durationFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}