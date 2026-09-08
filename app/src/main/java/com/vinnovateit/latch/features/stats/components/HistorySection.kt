package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.vinnovateit.latch.common.util.StatsColorPalettes
import com.vinnovateit.latch.features.settings.manager.SettingsManager
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    history: List<HistoryChartItem>
) {
    if (history.isNotEmpty()) {
        HistoryBarChartContent(chartItems = history)
    } else {
        NoDataCard("No stats available. Connect to Wi-Fi to start tracking your usage.")
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

    val visibleMaxUsage by remember(chartItems) {
        derivedStateOf {
            val visibleInfo = lazyListState.layoutInfo.visibleItemsInfo
            if (visibleInfo.isEmpty()) {
                chartItems.filterIsInstance<HistoryChartItem.BarData>()
                    .maxOfOrNull { it.usage.rxBytes + it.usage.txBytes }
                    ?.coerceAtLeast(1L) ?: 1L
            } else {
                val maxVisible = visibleInfo.mapNotNull { itemInfo ->
                    (chartItems.getOrNull(itemInfo.index) as? HistoryChartItem.BarData)?.let {
                        it.usage.rxBytes + it.usage.txBytes
                    }
                }.maxOrNull()?.coerceAtLeast(1L)
                maxVisible ?: 1L
            }
        }
    }

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
        }.distinctUntilChanged().collect { centerIdx ->
            if (centerIdx != -1 && centerIdx != lastCenteredIndex) {
                if (lazyListState.isScrollInProgress) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val animScale = remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        } catch (e: Exception) {
            1.0f
        }
    }

    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val headerTitle = remember(chartItems, selectedIndex) {
        val selectedItem = chartItems.getOrNull(selectedIndex) as? HistoryChartItem.BarData
        val ts = selectedItem?.timestamp
            ?: chartItems.filterIsInstance<HistoryChartItem.BarData>().lastOrNull()?.timestamp
            ?: System.currentTimeMillis()
        val year = formatDate(ts, "yyyy").toIntOrNull() ?: currentYear
        val pattern = if (year == currentYear) "MMMM" else "MMMM yyyy"
        formatDate(ts, pattern)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barWidth = 14.dp
            val rowHeight = 160.dp
            val barAreaHeight = 160.dp
            val centerPadding = ((maxWidth - barWidth) / 2).coerceAtLeast(16.dp)

            LazyRow(
                state = lazyListState,
                modifier = Modifier.height(rowHeight),
                contentPadding = PaddingValues(horizontal = centerPadding),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                horizontalArrangement = Arrangement.spacedBy(0.2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                itemsIndexed(
                    items = chartItems,
                    key = { index, item ->
                        when (item) {
                            is HistoryChartItem.BarData -> "bar_${item.timestamp}_$index"
                            is HistoryChartItem.MonthSeparator -> "month_${item.monthName}_$index"
                        }
                    },
                    contentType = { _, item ->
                        when (item) {
                            is HistoryChartItem.BarData -> "bar"
                            is HistoryChartItem.MonthSeparator -> "month"
                        }
                    }
                ) { idx, item ->
                    when (item) {
                        is HistoryChartItem.BarData -> {
                            Bar(
                                modifier = Modifier
                                    .width(barWidth)
                                    .fillMaxHeight(),
                                usage = item.usage,
                                maxUsage = visibleMaxUsage,
                                isSelected = (idx == selectedIndex),
                                hasSelection = (selectedIndex != -1),
                                isAmoled = isAmoled,
                                barWidth = barWidth,
                                barAreaHeight = barAreaHeight,
                                dlColor = dlColor,
                                ulColor = ulColor,
                                index = idx,
                                animScale = animScale,
                                isScrollInProgress = lazyListState.isScrollInProgress,
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
    maxUsage: Long,
    isSelected: Boolean,
    hasSelection: Boolean = false,
    isAmoled: Boolean = false,
    barWidth: Dp,
    barAreaHeight: Dp,
    dlColor: Color,
    ulColor: Color,
    index: Int = 0,
    animScale: Float = 1.0f,
    isScrollInProgress: Boolean = false,
    onTap: () -> Unit
) {
    val total = usage.rxBytes + usage.txBytes
    val targetFrac = if (maxUsage > 0L && total > 0L) {
        (total.toFloat() / maxUsage.toFloat()).coerceIn(0.04f, 0.96f)
    } else 0.04f

    val animatedFrac by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFrac,
        animationSpec = if (animScale <= 0f || isScrollInProgress) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.tween(
                durationMillis = (280 * animScale).toInt().coerceAtLeast(1),
                delayMillis = ((index % 12) * 12 * animScale).toInt(),
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        },
        label = "BarFrac_$index"
    )

    val uploadFrac = if (total > 0) usage.txBytes.toFloat() / total.toFloat() else 0f
    val downloadFrac = 1f - uploadFrac

    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = if (isSelected) 1f else if (hasSelection) 0.45f else 1f
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
                modifier = Modifier.fillMaxSize()
            ) {
                val currentFrac = animatedFrac
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                val strokeWidth = 1.5.dp.toPx()
                val inset = if (isAmoled) strokeWidth / 2 else 0f
                val drawWidth = (size.width - inset * 2).coerceAtLeast(0f)

                if (total > 0) {
                    val rawBarHeight = (size.height * currentFrac).coerceAtLeast(6.dp.toPx())
                    val drawHeight = (rawBarHeight - inset * 2).coerceAtLeast(0f)
                    val startY = size.height - rawBarHeight + inset
                    val topLeftOffset = Offset(inset, startY)

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
                    val rawBarHeight = 4.dp.toPx()
                    val drawHeight = (rawBarHeight - inset * 2).coerceAtLeast(0f)
                    val startY = size.height - rawBarHeight + inset
                    val topLeftOffset = Offset(inset, startY)
                    drawRoundRect(
                        color = emptyColor,
                        topLeft = topLeftOffset,
                        size = Size(drawWidth, drawHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }
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
        Text(
            "${totalFmt.first} ${totalFmt.second}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ArrowDownward, null, tint = dlColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${dlFmt.first} ${dlFmt.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = ulColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${ulFmt.first} ${ulFmt.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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