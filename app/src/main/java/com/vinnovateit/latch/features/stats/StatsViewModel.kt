package com.vinnovateit.latch.features.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinnovateit.latch.core.stats.formatDate
import com.vinnovateit.latch.core.model.AggregatedDayRecord
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.DateRangeFilter
import com.vinnovateit.latch.core.model.HistoryChartItem
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import com.vinnovateit.latch.core.model.StatsOverviewMetrics
import com.vinnovateit.latch.core.stats.StatsInsights
import com.vinnovateit.latch.core.stats.computeChartItems
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

  val allDayRecords: StateFlow<List<AggregatedDayRecord>> = LatchAppGraph.sessions.aggregatedDayRecords
  val overviewMetrics: StateFlow<StatsOverviewMetrics> = LatchAppGraph.sessions.overviewMetrics
  val statsInsights: StateFlow<StatsInsights> = LatchAppGraph.sessions.statsInsights

  fun refreshHistory(force: Boolean = false) {
    val platform = LatchAppGraph.platform
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



  val chartItems: StateFlow<List<HistoryChartItem>> =
    combine(nonZeroPortalHistory, liveStatus) { records, live ->
      val liveRx = live?.totalRxBytes ?: 0L
      val liveTx = live?.totalTxBytes ?: 0L
      computeChartItems(records, liveRxBytes = liveRx, liveTxBytes = liveTx)
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun onClearHistory() {
    LatchAppGraph.sessions.clearHistory()
  }

  override fun onCleared() {
    super.onCleared()
  }
}