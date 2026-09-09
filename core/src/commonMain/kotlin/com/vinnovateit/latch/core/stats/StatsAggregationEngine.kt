package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.AggregatedDayRecord
import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayKeyFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
private val displayDateFormat = ThreadLocal.withInitial { SimpleDateFormat("EEE, dd MMM yyyy", Locale.US) }

/**
 * Aggregates individual [PortalSessionRecord] items into daily summary buckets [AggregatedDayRecord].
 * Thread-safe using ThreadLocal formatters.
 */
fun aggregateDays(
    sessions: List<PortalSessionRecord>,
    nowMillis: Long = System.currentTimeMillis(),
): List<AggregatedDayRecord> {
    val dayKeyFmt = dayKeyFormat.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayFmt = displayDateFormat.get() ?: SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
    val todayKey = dayKeyFmt.format(Date(nowMillis))

    return sessions
        .filter { it.loginTime > 0 }
        .groupBy { dayKeyFmt.format(Date(it.loginTime)) }
        .map { (dateKey, daySessions) ->
            val first = daySessions.first()
            val dl = daySessions.sumOf { it.downloadBytes }
            val ul = daySessions.sumOf { it.uploadBytes }
            val total = daySessions.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
            val totalDur = daySessions.sumOf { it.durationMillis }
            val isToday = dateKey == todayKey
            AggregatedDayRecord(
                dayTimestamp = first.loginTime,
                dateFormatted = if (isToday) "Today" else displayFmt.format(Date(first.loginTime)),
                downloadBytes = dl,
                uploadBytes = ul,
                totalBytes = total,
                downloadFormatted = formatBytes(dl),
                uploadFormatted = formatBytes(ul),
                totalFormatted = formatBytes(total),
                sessionCount = daySessions.size,
                totalDurationMillis = totalDur,
                durationFormatted = formatDurationDynamic(totalDur),
                isToday = isToday,
            )
        }
        .sortedByDescending { it.dayTimestamp }
}
