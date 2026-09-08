package com.vinnovateit.latch.features.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinnovateit.latch.common.util.formatDate
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import com.vinnovateit.latch.features.stats.components.DailyUsageTrend
import com.vinnovateit.latch.features.stats.components.HistoryChartItem
import com.vinnovateit.latch.features.stats.components.StatsOverviewMetrics
import com.vinnovateit.latch.features.stats.components.aggregateDailyUsage
import com.vinnovateit.latch.features.stats.components.computeMetrics
import com.vinnovateit.latch.platform.LatchAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

import kotlinx.coroutines.flow.MutableStateFlow

enum class DateRangeFilter(val label: String) {
  LAST_30_DAYS("Last 30"),
  LAST_60_DAYS("Last 60"),
  LAST_90_DAYS("Last 90"),
  THIS_MONTH("This Month"),
  THIS_YEAR("This Year"),
  YTD("YTD"),
  LAST_YEAR("Last Year"),
  ALL_TIME("All Time")
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
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

  // Data now comes from the single source of truth: SessionRepository
  val liveStatus = LatchAppGraph.sessions.liveStatus
  val lastSession = LatchAppGraph.sessions.lastSession
  private val sessionHistory = LatchAppGraph.sessions.sessionSummaries

  val portalHistory: StateFlow<List<PortalSessionRecord>> = LatchAppGraph.sessions.portalHistory
  val isSyncing: StateFlow<Boolean> = LatchAppGraph.sessions.isSyncing

  val selectedFilter = MutableStateFlow(DateRangeFilter.THIS_MONTH)

  fun setFilter(filter: DateRangeFilter) {
    selectedFilter.value = filter
  }

  val nonZeroPortalHistory: StateFlow<List<PortalSessionRecord>> =
    portalHistory.map { list ->
      list.filter { it.uploadBytes > 0L || it.downloadBytes > 0L }
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val todaySessions: StateFlow<List<PortalSessionRecord>> =
    nonZeroPortalHistory.map { list ->
      val todayKey = formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
      list.filter { it.loginTime > 0 && formatDate(it.loginTime, "yyyy-MM-dd") == todayKey }
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val olderDayRecords: StateFlow<List<AggregatedDayRecord>> =
    nonZeroPortalHistory.map { list ->
      val todayKey = formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
      list
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
            dateFormatted = com.vinnovateit.latch.common.util.formatDisplayDate(first.loginTime),
            downloadBytes = dl,
            uploadBytes = ul,
            totalBytes = total,
            downloadFormatted = com.vinnovateit.latch.common.util.formatBytes(dl),
            uploadFormatted = com.vinnovateit.latch.common.util.formatBytes(ul),
            totalFormatted = com.vinnovateit.latch.common.util.formatBytes(total),
            sessionCount = daySessions.size,
            totalDurationMillis = totalDur,
            durationFormatted = com.vinnovateit.latch.common.util.formatDurationDynamic(totalDur)
          )
        }
        .sortedByDescending { it.dayTimestamp }
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val overviewMetrics: StateFlow<StatsOverviewMetrics> =
    nonZeroPortalHistory.map { sessions ->
      computeMetrics(sessions)
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeMetrics(emptyList()))

  val usageTrends: StateFlow<List<DailyUsageTrend>> =
    nonZeroPortalHistory.map { sessions ->
      aggregateDailyUsage(sessions)
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  init {
    refreshHistory()
  }

  fun refreshHistory() {
    val platform = LatchAppGraph.platform
    if (platform.credentials.exists()) {
      val userId = platform.credentials.userId()
      val password = platform.credentials.password()
      if (!userId.isNullOrBlank() && !password.isNullOrBlank()) {
        viewModelScope.launch(Dispatchers.IO) {
          LatchAppGraph.sessions.syncPortalHistory(userId, password)
        }
      }
    }
  }

  // This flow combines live and last sessions to decide what to show in the UI.
  val sessionToShow: StateFlow<SessionSummary?> =
    combine(
      liveStatus,
      lastSession,
    ) { live, last ->
      live?.let {
        // Create a temporary summary for the UI from the live data
        SessionSummary(
          startTimestamp = it.startTimeMillis,
          endTimestamp = System.currentTimeMillis(), // It's ongoing
          totalData = DataUsage(it.totalRxBytes, it.totalTxBytes),
          history = it.liveData,
          maxRxBps = it.maxRxBps,
          maxTxBps = it.maxTxBps
        )
      } ?: last // If not live, show the last completed session
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val historyToShow: StateFlow<List<SessionSummary>> =
    combine(
      sessionHistory,
      liveStatus
    ) { history, live ->
      live?.let {
        val liveSummary = SessionSummary(
          startTimestamp = it.startTimeMillis,
          endTimestamp = System.currentTimeMillis(),
          totalData = DataUsage(it.totalRxBytes, it.totalTxBytes),
          history = it.liveData,
          maxRxBps = it.maxRxBps,
          maxTxBps = it.maxTxBps
        )
        val historyWithoutLive = history.filter { it.startTimestamp != liveSummary.startTimestamp }
        mergeSessions(listOf(liveSummary) + historyWithoutLive, 60_000L)
      } ?: mergeSessions(history, 60_000L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private fun mergeSessions(sessions: List<SessionSummary>, gapMs: Long): List<SessionSummary> {
    if (sessions.isEmpty()) return emptyList()
    val sorted = sessions.sortedBy { it.startTimestamp }
    val merged = mutableListOf<SessionSummary>()
    var current = sorted[0]

    for (i in 1 until sorted.size) {
      val next = sorted[i]
      if (next.startTimestamp - current.endTimestamp <= gapMs) {
        current = current.copy(
          endTimestamp = maxOf(current.endTimestamp, next.endTimestamp),
          totalData = DataUsage(
            current.totalData.rxBytes + next.totalData.rxBytes,
            current.totalData.txBytes + next.totalData.txBytes
          ),
          history = current.history + next.history,
          maxRxBps = maxOf(current.maxRxBps, next.maxRxBps),
          maxTxBps = maxOf(current.maxTxBps, next.maxTxBps)
        )
      } else {
        merged.add(current)
        current = next
      }
    }
    merged.add(current)
    return merged.sortedByDescending { it.startTimestamp }
  }


  val chartItems: StateFlow<List<HistoryChartItem>> =
    combine(selectedFilter, nonZeroPortalHistory, liveStatus) { filter, records, live ->
      val recordsByDay = records
        .filter { it.loginTime > 0 }
        .groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }

      val groupedByDay = recordsByDay
        .mapValues { (_, list) ->
          DataUsage(
            rxBytes = list.sumOf { it.downloadBytes },
            txBytes = list.sumOf { it.uploadBytes }
          )
        }
        .toMutableMap()

      val todayKey = formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
      live?.let {
        val current = groupedByDay[todayKey] ?: DataUsage(0L, 0L)
        groupedByDay[todayKey] = DataUsage(
          rxBytes = current.rxBytes + it.totalRxBytes,
          txBytes = current.txBytes + it.totalTxBytes
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

      when (filter) {
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
          val validRecords = records.filter { it.loginTime > 0 }
          val earliest = validRecords.minOfOrNull { it.loginTime } ?: (System.currentTimeMillis() - 30L * 86400000L)
          val minAllowed = Calendar.getInstance().apply { set(2020, Calendar.JANUARY, 1) }.timeInMillis
          startCal.timeInMillis = maxOf(earliest, minAllowed)
          startCal.set(Calendar.DAY_OF_MONTH, 1)
        }
      }

      val currentYear = now.get(Calendar.YEAR)
      val items = mutableListOf<HistoryChartItem>()
      var lastMonth = -1

      val maxEnd = if (endCal.after(now)) now else endCal
      val cursor = startCal.clone() as Calendar
      while (!cursor.after(maxEnd)) {
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
        val formattedDate = com.vinnovateit.latch.common.util.formatDisplayDate(dayTimestamp)
        val dayRecords = recordsByDay[key] ?: emptyList()
        val sessionCount = dayRecords.size + (if (live != null && key == todayKey) 1 else 0)
        val durationMillis = dayRecords.sumOf { it.durationMillis }
        val durationFormatted = com.vinnovateit.latch.common.util.formatDurationDynamic(durationMillis)
        items.add(
          HistoryChartItem.BarData(
            usage = usage,
            label = label,
            timestamp = dayTimestamp,
            formattedDate = formattedDate,
            sessionCount = sessionCount,
            durationMillis = durationMillis,
            durationFormatted = durationFormatted
          )
        )
        cursor.add(Calendar.DAY_OF_YEAR, 1)
      }
      items.distinct()
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val statsInsights: StateFlow<com.vinnovateit.latch.core.stats.StatsInsights> =
    nonZeroPortalHistory.map { sessions ->
      com.vinnovateit.latch.core.stats.computeStatsInsights(sessions)
    }.flowOn(Dispatchers.Default)
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.vinnovateit.latch.core.stats.computeStatsInsights(emptyList())
      )




  fun onClearHistory() {
    LatchAppGraph.sessions.clearHistory()
  }

  override fun onCleared() {
    super.onCleared()
  }
}