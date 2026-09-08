package com.vinnovateit.latch.features.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinnovateit.latch.common.util.formatDate
import com.vinnovateit.latch.core.model.AggregatedDayRecord
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.DateRangeFilter
import com.vinnovateit.latch.core.model.HistoryChartItem
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import com.vinnovateit.latch.core.model.StatsOverviewMetrics
import com.vinnovateit.latch.core.model.computeMetrics
import com.vinnovateit.latch.platform.LatchAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class StatsViewModel(application: Application) : AndroidViewModel(application) {

  // Data now comes from the single source of truth: SessionRepository
  val liveStatus = LatchAppGraph.sessions.liveStatus
  val lastSession = LatchAppGraph.sessions.lastSession
  private val sessionHistory = LatchAppGraph.sessions.sessionSummaries

  val portalHistory: StateFlow<List<PortalSessionRecord>> = LatchAppGraph.sessions.portalHistory
  val isHistoryLoaded: StateFlow<Boolean> = LatchAppGraph.sessions.isHistoryLoaded
  val isSyncing: StateFlow<Boolean> = LatchAppGraph.sessions.isSyncing

  val nonZeroPortalHistory: StateFlow<List<PortalSessionRecord>> =
    portalHistory.map { list ->
      list.filter { it.uploadBytes > 0L || it.downloadBytes > 0L }
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val todaySessions: StateFlow<List<PortalSessionRecord>> =
    nonZeroPortalHistory.map { list ->
      val todayKey = formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
      list.filter { it.loginTime > 0 && formatDate(it.loginTime, "yyyy-MM-dd") == todayKey }
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val allDayRecords: StateFlow<List<AggregatedDayRecord>> =
    nonZeroPortalHistory.map { list ->
      val todayKey = formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
      list
        .filter { it.loginTime > 0 }
        .groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }
        .map { (key, daySessions) ->
          val first = daySessions.first()
          val dl = daySessions.sumOf { it.downloadBytes }
          val ul = daySessions.sumOf { it.uploadBytes }
          val total = daySessions.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
          val totalDur = daySessions.sumOf { it.durationMillis }
          val isToday = (key == todayKey)
          AggregatedDayRecord(
            dayTimestamp = first.loginTime,
            dateFormatted = if (isToday) "Today, ${com.vinnovateit.latch.common.util.formatDisplayDate(first.loginTime)}"
            else com.vinnovateit.latch.common.util.formatDisplayDate(first.loginTime),
            downloadBytes = dl,
            uploadBytes = ul,
            totalBytes = total,
            downloadFormatted = com.vinnovateit.latch.common.util.formatBytes(dl),
            uploadFormatted = com.vinnovateit.latch.common.util.formatBytes(ul),
            totalFormatted = com.vinnovateit.latch.common.util.formatBytes(total),
            sessionCount = daySessions.size,
            totalDurationMillis = totalDur,
            durationFormatted = com.vinnovateit.latch.common.util.formatDurationDynamic(totalDur),
            isToday = isToday
          )
        }
        .sortedByDescending { it.dayTimestamp }
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val overviewMetrics: StateFlow<StatsOverviewMetrics> =
    nonZeroPortalHistory.map { sessions ->
      computeMetrics(sessions)
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, computeMetrics(emptyList()))

  init {
    refreshHistory()
  }

  fun refreshHistory(force: Boolean = false) {
    val platform = LatchAppGraph.platform
    if (!platform.wifi.isConnectedToWifi()) return
    if (platform.credentials.exists()) {
      val userId = platform.credentials.userId()
      val password = platform.credentials.password()
      if (!userId.isNullOrBlank() && !password.isNullOrBlank()) {
        viewModelScope.launch(Dispatchers.IO) {
          LatchAppGraph.sessions.syncPortalHistory(userId, password, force = force)
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
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

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
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
    combine(nonZeroPortalHistory, liveStatus) { records, live ->
      val liveRx = live?.totalRxBytes ?: 0L
      val liveTx = live?.totalTxBytes ?: 0L
      com.vinnovateit.latch.core.stats.computeChartItems(records, liveRxBytes = liveRx, liveTxBytes = liveTx)
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val statsInsights: StateFlow<com.vinnovateit.latch.core.stats.StatsInsights> =
    nonZeroPortalHistory.map { sessions ->
      com.vinnovateit.latch.core.stats.computeStatsInsights(sessions)
    }.flowOn(Dispatchers.Default)
      .stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        com.vinnovateit.latch.core.stats.computeStatsInsights(emptyList())
      )




  fun onClearHistory() {
    LatchAppGraph.sessions.clearHistory()
  }

  override fun onCleared() {
    super.onCleared()
  }
}