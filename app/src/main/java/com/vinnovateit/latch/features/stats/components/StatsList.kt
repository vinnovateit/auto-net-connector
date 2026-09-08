package com.vinnovateit.latch.features.stats.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.R
import com.vinnovateit.latch.core.model.LiveConnectionStatus
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import com.vinnovateit.latch.features.stats.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsList(
  modifier: Modifier = Modifier,
  isLive: Boolean,
  showSessionCard: Boolean = true,
  sessionToShow: SessionSummary?,
  portalHistory: List<PortalSessionRecord> = emptyList(),
  historyToShow: List<SessionSummary> = emptyList(),
  liveStatus: LiveConnectionStatus? = null,
  speedUnits: String,
  showAllSessions: Boolean,
  onToggleShowAll: () -> Unit,
  addSpacer: Boolean = false,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  statsViewModel: StatsViewModel
) {
  val overviewMetrics by statsViewModel.overviewMetrics.collectAsStateWithLifecycle()
  val chartItems by statsViewModel.chartItems.collectAsStateWithLifecycle()
  val selectedFilter by statsViewModel.selectedFilter.collectAsStateWithLifecycle()
  val todaySessions by statsViewModel.todaySessions.collectAsStateWithLifecycle()
  val olderDayRecords by statsViewModel.olderDayRecords.collectAsStateWithLifecycle()
  var displayedOlderDaysCount by remember { mutableIntStateOf(30) }
  val visibleOlderDays = remember(olderDayRecords, displayedOlderDaysCount) {
    olderDayRecords.take(displayedOlderDaysCount)
  }
  val layoutDirection = LocalLayoutDirection.current

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(
      top = contentPadding.calculateTopPadding(),
      bottom = contentPadding.calculateBottomPadding() + 100.dp,
      start = contentPadding.calculateStartPadding(layoutDirection),
      end = contentPadding.calculateEndPadding(layoutDirection)
    ),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (addSpacer) {
      item {
        Spacer(modifier = Modifier.height(20.dp))
      }
    }

    if (isLive && sessionToShow != null && showSessionCard) {
      item {
        Box(Modifier.height(250.dp)) {
          SessionCard(
            session = sessionToShow,
            speedUnit = speedUnits
          )
        }
        Spacer(modifier = Modifier.height(15.dp))
      }
    }

    item {
      StatsMetricsSummary(metrics = overviewMetrics)
      Spacer(modifier = Modifier.height(15.dp))
    }

    if (chartItems.isNotEmpty()) {
      item {
        HistoryBarChart(
          history = chartItems,
          selectedFilter = selectedFilter,
          onFilterSelected = { statsViewModel.setFilter(it) }
        )
        Spacer(modifier = Modifier.height(15.dp))
      }
    }

    if (todaySessions.isNotEmpty()) {
      item {
        Text(
          text = "Today's Sessions",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Left,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        )
      }

      itemsIndexed(todaySessions, key = { index, session -> "today_${session.loginTime}_$index" }) { index, session ->
        TodaySessionListItem(
          session = session,
          shape = groupedItemShape(index, todaySessions.size)
        )
      }
    }

    if (olderDayRecords.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Previous Days",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Left,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        )
      }

      itemsIndexed(visibleOlderDays, key = { _, record -> "day_${record.dayTimestamp}" }) { index, record ->
        if (index >= visibleOlderDays.size - 5 && displayedOlderDaysCount < olderDayRecords.size) {
          LaunchedEffect(Unit) {
            displayedOlderDaysCount = (displayedOlderDaysCount + 30).coerceAtMost(olderDayRecords.size)
          }
        }
        DayAggregateListItem(
          record = record,
          shape = groupedItemShape(index, visibleOlderDays.size)
        )
      }
    }
  }
}