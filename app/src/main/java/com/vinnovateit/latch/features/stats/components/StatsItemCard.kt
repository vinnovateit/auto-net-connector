package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.features.settings.manager.SettingsManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.common.util.formatBytes
import com.vinnovateit.latch.common.util.formatDate
import com.vinnovateit.latch.common.util.formatDurationDynamic
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import com.vinnovateit.latch.ui.theme.ColorGraphDownload
import com.vinnovateit.latch.ui.theme.ColorGraphUpload

/**
 * A Composable that displays a summary of a single captive portal session record in a card,
 * with a customizable shape for grouping.
 */
@Composable
fun StatsItemCard(
  session: PortalSessionRecord,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
  val isAmoled = usePureBlack && com.vinnovateit.latch.ui.theme.LocalIsDarkTheme.current

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = shape,
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    border = if (isAmoled) androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      // Top header: Location chip + Duration pill badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Rounded.Wifi,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = session.location.ifBlank { "Unknown" },
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }

        val durationStr = session.durationFormatted.ifBlank { formatDurationDynamic(session.durationMillis) }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        ) {
          Text(
            text = durationStr,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Main row: Login date/time on the left, Data transfer on the right
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          val timeString = if (session.loginTime > 0) {
            formatDate(session.loginTime, "dd MMM • hh:mm a")
          } else {
            "-"
          }
          Text(
            text = timeString,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (session.logoutTime > 0 && session.logoutTime != session.loginTime) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Until ${formatDate(session.logoutTime, "hh:mm a")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Column(horizontalAlignment = Alignment.End) {
          val effectiveTotal = if (session.totalBytes > 0) session.totalBytes else (session.uploadBytes + session.downloadBytes)
          val total = remember(effectiveTotal) { formatBytes(effectiveTotal) }
          Text(
            text = "${total.first} ${total.second}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(2.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            val dl = remember(session.downloadBytes) { formatBytes(session.downloadBytes) }
            Icon(
              Icons.Rounded.ArrowDownward,
              contentDescription = "Download data",
              tint = ColorGraphDownload,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "${dl.first} ${dl.second}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            val ul = remember(session.uploadBytes) { formatBytes(session.uploadBytes) }
            Icon(
              Icons.Rounded.ArrowUpward,
              contentDescription = "Upload data",
              tint = ColorGraphUpload,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "${ul.first} ${ul.second}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

/**
 * Backward-compatible overload accepting SessionSummary.
 */
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
  StatsItemCard(session = record, shape = shape)
}
