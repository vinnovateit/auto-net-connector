package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class StatsInsights(
    val peakUsageTimeWindow: String,
    val highestUsageDayFormatted: String,
    val highestUsageDayDate: String,
    val highestUsageDayBytes: Long,
    val dailyAverageBytes: Long,
    val dailyAverageFormatted: Pair<String, String>,
    val weeklyAverageBytes: Long,
    val weeklyAverageFormatted: Pair<String, String>,
    val mostActiveSessionDurationFormatted: String,
    val mostActiveSessionBytes: Long,
    val mostActiveSessionFormatted: Pair<String, String>,
    val activeDaysCount: Int,
)

fun formatInsightDate(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val recordYear = cal.get(Calendar.YEAR)
    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val currentYear = nowCal.get(Calendar.YEAR)

    val pattern = if (recordYear == currentYear) "dd MMM" else "dd MMM yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(cal.time)
}

fun computeStatsInsights(
    sessions: List<PortalSessionRecord>,
    nowMillis: Long = System.currentTimeMillis()
): StatsInsights {
    val nonZero = sessions.filter { it.loginTime > 0 && (it.uploadBytes > 0 || it.downloadBytes > 0) }
    if (nonZero.isEmpty()) {
        val zeroPair = formatBytes(0L)
        return StatsInsights(
            peakUsageTimeWindow = "N/A",
            highestUsageDayFormatted = "0 B",
            highestUsageDayDate = "N/A",
            highestUsageDayBytes = 0L,
            dailyAverageBytes = 0L,
            dailyAverageFormatted = zeroPair,
            weeklyAverageBytes = 0L,
            weeklyAverageFormatted = zeroPair,
            mostActiveSessionDurationFormatted = "0m",
            mostActiveSessionBytes = 0L,
            mostActiveSessionFormatted = zeroPair,
            activeDaysCount = 0,
        )
    }

    // 1. Peak usage 3-hour window
    val windowBytes = LongArray(8)
    val windowCal = Calendar.getInstance()
    for (s in nonZero) {
        windowCal.timeInMillis = s.loginTime
        val hour = windowCal.get(Calendar.HOUR_OF_DAY)
        val windowIdx = (hour / 3).coerceIn(0, 7)
        val bytes = s.totalBytes.coerceAtLeast(s.downloadBytes + s.uploadBytes)
        windowBytes[windowIdx] += bytes
    }
    var bestWindowIdx = 0
    var maxWindowBytes = -1L
    for (i in 0 until 8) {
        if (windowBytes[i] > maxWindowBytes) {
            maxWindowBytes = windowBytes[i]
            bestWindowIdx = i
        }
    }
    val startHour = bestWindowIdx * 3
    val endHour = startHour + 3
    fun formatHour(h: Int): String {
        val ampm = if (h < 12 || h == 24) "AM" else "PM"
        val h12 = when (val mod = h % 12) {
            0 -> 12
            else -> mod
        }
        return String.format(Locale.US, "%02d:00 %s", h12, ampm)
    }
    val peakUsageTimeWindow = "${formatHour(startHour)} – ${formatHour(endHour)}"

    // 2. Highest Usage Day
    val dayGroups = nonZero.groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }
    var highestDayBytes = 0L
    var highestDayTimestamp = 0L
    for ((_, daySessions) in dayGroups) {
        val dayTotal = daySessions.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
        if (dayTotal > highestDayBytes) {
            highestDayBytes = dayTotal
            highestDayTimestamp = daySessions.first().loginTime
        }
    }
    val highestDayDate = if (highestDayTimestamp > 0) formatInsightDate(highestDayTimestamp, nowMillis) else "N/A"
    val highestUsageFormatted = formatBytes(highestDayBytes)

    // 3. Daily & Weekly Averages
    val totalBytesAll = nonZero.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
    val activeDays = dayGroups.size.coerceAtLeast(1)
    val dailyAverageBytes = totalBytesAll / activeDays
    val weeklyAverageBytes = dailyAverageBytes * 7L

    // 4. Most active session
    val topSession = nonZero.maxByOrNull { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
        ?: nonZero.first()
    val topSessionBytes = topSession.totalBytes.coerceAtLeast(topSession.downloadBytes + topSession.uploadBytes)

    return StatsInsights(
        peakUsageTimeWindow = peakUsageTimeWindow,
        highestUsageDayFormatted = "${highestUsageFormatted.first} ${highestUsageFormatted.second}",
        highestUsageDayDate = highestDayDate,
        highestUsageDayBytes = highestDayBytes,
        dailyAverageBytes = dailyAverageBytes,
        dailyAverageFormatted = formatBytes(dailyAverageBytes),
        weeklyAverageBytes = weeklyAverageBytes,
        weeklyAverageFormatted = formatBytes(weeklyAverageBytes),
        mostActiveSessionDurationFormatted = formatDurationDynamic(topSession.durationMillis),
        mostActiveSessionBytes = topSessionBytes,
        mostActiveSessionFormatted = formatBytes(topSessionBytes),
        activeDaysCount = activeDays,
    )
}
