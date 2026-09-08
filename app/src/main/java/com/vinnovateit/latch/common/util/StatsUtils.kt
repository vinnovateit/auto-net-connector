package com.vinnovateit.latch.common.util

import androidx.compose.ui.graphics.Path
import com.vinnovateit.latch.core.model.LiveDataPoint
typealias DisplayMode = com.vinnovateit.latch.core.stats.DisplayMode

data class GraphData(
    val totalPath: Path,
    val lineTotalPath: Path
)

inline fun formatBytes(bytes: Long, unit: String = "B/s"): Pair<String, String> =
    com.vinnovateit.latch.core.stats.formatBytes(bytes, unit)

inline fun formatBitsPerSecond(bytesPerSecond: Long, unit: String = "bps"): Pair<String, String> =
    com.vinnovateit.latch.core.stats.formatBitsPerSecond(bytesPerSecond, unit)

inline fun formatDurationDynamic(ms: Long): String =
    com.vinnovateit.latch.core.stats.formatDurationDynamic(ms)

inline fun formatDate(millis: Long, pattern: String): String =
    com.vinnovateit.latch.core.stats.formatDate(millis, pattern)

inline fun formatDisplayDate(millis: Long, nowMillis: Long = System.currentTimeMillis()): String =
    com.vinnovateit.latch.core.stats.formatDisplayDate(millis, nowMillis)

fun createGraphPaths(
    history: List<LiveDataPoint>,
    width: Float,
    height: Float,
    maxRate: Float,
    graphHeightScale: Float = 0.6f
): GraphData {
    if (history.size < 2) {
        return GraphData(Path(), Path())
    }

    val effectiveMaxRate = maxRate.coerceAtLeast(1f)
    val startTime = history.first().timestamp
    val duration = (history.last().timestamp - startTime).coerceAtLeast(1)
    fun x(t: Long): Float = ((t - startTime).toFloat() / duration) * width
    fun y(bytes: Long): Float {
        val usageFraction = (bytes.toFloat() / effectiveMaxRate).coerceIn(0f, 1f)
        return height - (usageFraction * height * graphHeightScale)
    }

    val fillTotal = Path().apply { moveTo(0f, height) }
    val lineTotal = Path()
    for (i in history.indices) {
        val p = history[i]
        val xp = x(p.timestamp)
        val yTotal = y(p.usage.rxBps + p.usage.txBps)

        if (i == 0) {
            lineTotal.moveTo(xp, yTotal)
            fillTotal.lineTo(xp, yTotal)
        } else {
            val prevPoint = history[i - 1]
            val prevX = x(prevPoint.timestamp)
            val prevYTotal = y(prevPoint.usage.rxBps + prevPoint.usage.txBps)
            val cx = (prevX + xp) / 2f

            lineTotal.cubicTo(cx, prevYTotal, cx, yTotal, xp, yTotal)
            fillTotal.cubicTo(cx, prevYTotal, cx, yTotal, xp, yTotal)
        }
    }

    val lastX = x(history.last().timestamp)
    fillTotal.lineTo(lastX, height)
    fillTotal.close()

    return GraphData(fillTotal, lineTotal)
}