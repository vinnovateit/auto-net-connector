package com.vinnovateit.latch.core.model

enum class DateRangeFilter(val label: String) {
    LAST_30_DAYS("Last 30"),
    LAST_60_DAYS("Last 60"),
    LAST_90_DAYS("Last 90"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    YTD("YTD"),
    LAST_YEAR("Last Year"),
    ALL_TIME("All Time"),
}

data class AggregatedDayRecord(
    val dayTimestamp: Long,
    val dateFormatted: String,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val totalBytes: Long,
    val downloadFormatted: Pair<String, String>,
    val uploadFormatted: Pair<String, String>,
    val totalFormatted: Pair<String, String>,
    val sessionCount: Int,
    val totalDurationMillis: Long,
    val durationFormatted: String,
    val isToday: Boolean = false,
)

data class StatsOverviewMetrics(
    val totalBytes: Long,
    val totalUploadBytes: Long,
    val totalDownloadBytes: Long,
    val totalSessions: Int,
)

fun computeMetrics(sessions: List<PortalSessionRecord>): StatsOverviewMetrics {
    if (sessions.isEmpty()) {
        return StatsOverviewMetrics(0L, 0L, 0L, 0)
    }
    val total = sessions.sumOf { it.totalBytes }
    val ul = sessions.sumOf { it.uploadBytes }
    val dl = sessions.sumOf { it.downloadBytes }

    return StatsOverviewMetrics(
        totalBytes = total,
        totalUploadBytes = ul,
        totalDownloadBytes = dl,
        totalSessions = sessions.size,
    )
}

sealed class HistoryChartItem {
    data class BarData(
        val usage: DataUsage,
        val label: String,
        val timestamp: Long,
        val formattedDate: String = "",
        val sessionCount: Int = 0,
        val durationMillis: Long = 0L,
        val durationFormatted: String = "",
    ) : HistoryChartItem()

    data class MonthSeparator(val monthName: String) : HistoryChartItem()

    data class CollapsedMonth(val monthName: String, val timestamp: Long) : HistoryChartItem()
}
