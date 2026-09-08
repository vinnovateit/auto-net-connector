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

import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row

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
  onNavigateToHistory: () -> Unit = {},
  statsViewModel: StatsViewModel
) {
  val overviewMetrics by statsViewModel.overviewMetrics.collectAsStateWithLifecycle()
  val statsInsights by statsViewModel.statsInsights.collectAsStateWithLifecycle()
  val chartItems by statsViewModel.chartItems.collectAsStateWithLifecycle()
  val selectedFilter by statsViewModel.selectedFilter.collectAsStateWithLifecycle()
  val todaySessions by statsViewModel.todaySessions.collectAsStateWithLifecycle()
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

    item {
      UsageInsightsCards(insights = statsInsights)
      Spacer(modifier = Modifier.height(15.dp))
    }

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

    if (todaySessions.isNotEmpty()) {
      itemsIndexed(todaySessions, key = { index, session -> "today_${session.loginTime}_$index" }) { index, session ->
        TodaySessionListItem(
          session = session,
          shape = groupedItemShape(index, todaySessions.size)
        )
      }
    } else {
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
          Text(
            text = "No active portal sessions recorded today.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
          )
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(14.dp))
      Surface(
        onClick = onNavigateToHistory,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.DateRange,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Full Session History",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Browse day-wise data usage and past session logs",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = "Open session history",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}