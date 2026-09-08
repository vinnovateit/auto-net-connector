package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.common.util.formatBytes
import com.vinnovateit.latch.common.util.formatDate
import com.vinnovateit.latch.common.util.formatDurationDynamic
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import com.vinnovateit.latch.features.settings.manager.SettingsManager
import com.vinnovateit.latch.features.stats.AggregatedDayRecord
import com.vinnovateit.latch.ui.theme.ColorGraphDownload
import com.vinnovateit.latch.ui.theme.ColorGraphUpload

fun groupedItemShape(index: Int, totalCount: Int, cornerRadius: Dp = 24.dp, innerRadius: Dp = 4.dp): Shape {
  return when {
    totalCount <= 1 -> RoundedCornerShape(cornerRadius)
    index == 0 -> RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
    index == totalCount - 1 -> RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = cornerRadius, bottomEnd = cornerRadius)
    else -> RoundedCornerShape(innerRadius)
  }
}

@Composable
fun TodaySessionListItem(
  session: PortalSessionRecord,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
  val isAmoled = usePureBlack && com.vinnovateit.latch.ui.theme.LocalIsDarkTheme.current

  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = if (isAmoled) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
  ) {
    val totalFormatted = remember(session.totalBytes, session.uploadBytes, session.downloadBytes) {
      val effectiveTotal = if (session.totalBytes > 0) session.totalBytes else (session.uploadBytes + session.downloadBytes)
      formatBytes(effectiveTotal)
    }
    val dlFormatted = remember(session.downloadBytes) { formatBytes(session.downloadBytes) }
    val ulFormatted = remember(session.uploadBytes) { formatBytes(session.uploadBytes) }
    val durationStr = session.durationFormatted.ifBlank { formatDurationDynamic(session.durationMillis) }

    ListItem(
      headlineContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = session.location.ifBlank { "Session" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (session.loginTime > 0) formatDate(session.loginTime, "hh:mm a") else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      supportingContent = {
        Row(
          modifier = Modifier.padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = ColorGraphDownload, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("${dlFormatted.first} ${dlFormatted.second}", style = MaterialTheme.typography.labelSmall)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = ColorGraphUpload, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("${ulFormatted.first} ${ulFormatted.second}", style = MaterialTheme.typography.labelSmall)
          }
          if (durationStr.isNotBlank()) {
            Text(durationStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
          }
        }
      },
      trailingContent = {
        Text(
          text = "${totalFormatted.first} ${totalFormatted.second}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
  }
}

@Composable
fun DayAggregateListItem(
  record: AggregatedDayRecord,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
  val isAmoled = usePureBlack && com.vinnovateit.latch.ui.theme.LocalIsDarkTheme.current

  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = if (isAmoled) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
  ) {
    val sessionLabel = if (record.sessionCount == 1) "1 session" else "${record.sessionCount} sessions"

    ListItem(
      headlineContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = record.dateFormatted,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
          ) {
            Text(
              text = sessionLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }
      },
      supportingContent = {
        Row(
          modifier = Modifier.padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = ColorGraphDownload, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("${record.downloadFormatted.first} ${record.downloadFormatted.second}", style = MaterialTheme.typography.labelSmall)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = ColorGraphUpload, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("${record.uploadFormatted.first} ${record.uploadFormatted.second}", style = MaterialTheme.typography.labelSmall)
          }
          if (record.durationFormatted.isNotBlank()) {
            Text(
              text = record.durationFormatted,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }
      },
      trailingContent = {
        Text(
          text = "${record.totalFormatted.first} ${record.totalFormatted.second}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
  }
}

@Composable
fun StatsItemCard(
  session: PortalSessionRecord,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  TodaySessionListItem(session = session, shape = shape)
}

@Composable
fun StatsItemCard(
  session: SessionSummary,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  val record = PortalSessionRecord(
    location = "Local Session",
    macAddress = "-",
    loginTime = session.startTimestamp,
    logoutTime = session.endTimestamp,
    durationFormatted = formatDurationDynamic(session.endTimestamp - session.startTimestamp),
    durationMillis = (session.endTimestamp - session.startTimestamp).coerceAtLeast(0L),
    uploadBytes = session.totalData.txBytes,
    downloadBytes = session.totalData.rxBytes,
    totalBytes = session.totalData.rxBytes + session.totalData.txBytes
  )
  TodaySessionListItem(session = record, shape = shape)
}
