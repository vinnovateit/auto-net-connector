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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.features.settings.manager.SettingsManager

@Immutable
sealed class HistoryChartItem {
    data class BarData(
        val usage: DataUsage,
        val label: String,
        val timestamp: Long,
        val formattedDate: String = ""
    ) : HistoryChartItem()
    data class MonthSeparator(val monthName: String) : HistoryChartItem()
}

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
            horizontalArrangement = Arrangement.SpaceBetween,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(quickFilterScrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickFilters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterSelected(filter) },
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
    val totalUsageLabel = "Total Data Usage"
    var displayedData by remember { mutableStateOf(totalUsageData to totalUsageLabel) }
    var revertJob by remember { mutableStateOf<Job?>(null) }

    val maxDailyUsage = remember(chartItems) {
        chartItems.filterIsInstance<HistoryChartItem.BarData>()
            .maxOfOrNull { it.usage.rxBytes + it.usage.txBytes }
            ?.coerceAtLeast(1L) ?: 1L
    }

    // Reset selection and scroll position safely when chartItems change
    LaunchedEffect(chartItems) {
        selectedIndex = -1
        revertJob?.cancel()
        displayedData = totalUsageData to totalUsageLabel
        if (todayIdx in chartItems.indices) {
            lazyListState.scrollToItem(todayIdx)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidth = 24.dp
            val rowHeight = 200.dp
            val barAreaHeight = 150.dp
            val horizontalPadding = 16.dp

            LazyRow(
                state = lazyListState,
                modifier = Modifier.height(rowHeight),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                                    .width(containerWidth)
                                    .fillMaxHeight(),
                                usage = item.usage,
                                maxUsage = maxDailyUsage,
                                dayLabel = item.label,
                                isSelected = (idx == selectedIndex),
                                isAmoled = isAmoled,
                                barAreaHeight = barAreaHeight,
                                onTap = {
                                    val clickedItem = chartItems.getOrNull(idx) as? HistoryChartItem.BarData ?: return@Bar
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    if (selectedIndex == idx) {
                                        selectedIndex = -1
                                        revertJob?.cancel()
                                        displayedData = totalUsageData to totalUsageLabel
                                    } else {
                                        selectedIndex = idx
                                        val formattedDate = clickedItem.formattedDate.ifBlank { dateFormatter.format(Date(clickedItem.timestamp)) }
                                        displayedData = clickedItem.usage to formattedDate
                                        revertJob?.cancel()
                                        revertJob = coroutineScope.launch {
                                            delay(7000)
                                            selectedIndex = -1
                                            displayedData = totalUsageData to totalUsageLabel
                                        }
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(idx)
                                        }
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

        StatDetailRow(data = displayedData)
    }
}

@Composable
private fun MonthSeparator(monthName: String) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = monthName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(-90f)
        )
    }
}

@Composable
private fun Bar(
    modifier: Modifier = Modifier,
    usage: DataUsage,
    maxUsage: Long,
    dayLabel: String,
    isSelected: Boolean,
    isAmoled: Boolean = false,
    barAreaHeight: Dp,
    onTap: () -> Unit
) {
    val total = usage.rxBytes + usage.txBytes
    val totalFrac = (total.toFloat() / maxUsage.toFloat()).coerceIn(0.06f, 1f)

    val uploadFrac = if (total > 0) usage.txBytes.toFloat() / total.toFloat() else 0f
    val downloadFrac = 1f - uploadFrac
    val density = LocalDensity.current
    val barHeightInDp = with(density) { (barAreaHeight.toPx() * totalFrac).toDp() }

    Column(
        modifier = modifier
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
                .width(24.dp)
                .height(barAreaHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            val dlColor = ColorGraphDownload
            val ulColor = ColorGraphUpload

            Canvas(
                modifier = Modifier
                    .width(10.dp)
                    .height(barHeightInDp)
            ) {
                val strokeWidth = 2.dp.toPx()
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2, size.width / 2)
                val inset = if (isAmoled) strokeWidth / 2 else 0f
                val drawSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeftOffset = Offset(inset, inset)

                if (total > 0) {
                    val gapPx = if (downloadFrac > 0f && uploadFrac > 0f) 2.dp.toPx() else 0f
                    val availableHeight = (drawSize.height - gapPx).coerceAtLeast(0f)
                    val ulH = availableHeight * uploadFrac
                    val dlH = availableHeight * downloadFrac

                    if (ulH > 0f) {
                        if (isAmoled) {
                            drawRoundRect(
                                color = ulColor,
                                topLeft = topLeftOffset,
                                size = Size(drawSize.width, ulH),
                                cornerRadius = cornerRadius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                        } else {
                            drawRoundRect(
                                color = ulColor,
                                topLeft = topLeftOffset,
                                size = Size(drawSize.width, ulH),
                                cornerRadius = cornerRadius
                            )
                        }
                    }

                    if (dlH > 0f) {
                        val dlTopY = topLeftOffset.y + if (ulH > 0f) ulH + gapPx else 0f
                        if (isAmoled) {
                            drawRoundRect(
                                color = dlColor,
                                topLeft = Offset(topLeftOffset.x, dlTopY),
                                size = Size(drawSize.width, dlH),
                                cornerRadius = cornerRadius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                        } else {
                            drawRoundRect(
                                color = dlColor,
                                topLeft = Offset(topLeftOffset.x, dlTopY),
                                size = Size(drawSize.width, dlH),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                } else {
                    val emptyColor = if (isAmoled) Color.DarkGray else Color.Gray.copy(alpha = 0.25f)
                    if (isAmoled) {
                        drawRoundRect(
                            color = emptyColor,
                            topLeft = topLeftOffset,
                            size = drawSize,
                            cornerRadius = cornerRadius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    } else {
                        drawRoundRect(
                            color = emptyColor,
                            topLeft = topLeftOffset,
                            size = drawSize,
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(backgroundColor)
        ) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatDetailRow(data: Pair<DataUsage, String>) {
    val (currentUsage, label) = data

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
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedContent(dlFmt, label = "DLStat", transitionSpec = { fadeIn() togetherWith fadeOut() }) { (value, unit) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowDownward, null, tint = ColorGraphDownload, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$value $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedContent(ulFmt, label = "ULStat", transitionSpec = { fadeIn() togetherWith fadeOut() }) { (value, unit) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowUpward, null, tint = ColorGraphUpload, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$value $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}