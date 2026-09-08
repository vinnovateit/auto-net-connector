package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.vinnovateit.latch.core.stats.StatsInsights

@Composable
fun UsageInsightsCards(
    insights: StatsInsights,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        GameStatRow(
            label = "Network badge",
            value = insights.primaryBadge,
            sublabel = insights.badgeDescription,
            valueColor = MaterialTheme.colorScheme.primary
        )
        GameStatRow(
            label = "Active streak",
            value = "${insights.currentStreakDays}",
            unit = if (insights.currentStreakDays == 1) "day" else "days",
            sublabel = if (insights.longestStreakDays > insights.currentStreakDays) "Best: ${insights.longestStreakDays} days" else ""
        )
        GameStatRow(
            label = "Night owl traffic",
            value = insights.nightOwlFormatted.first,
            unit = insights.nightOwlFormatted.second,
            sublabel = if (insights.nightOwlPercentage > 0) "${insights.nightOwlPercentage}% after midnight (12–6 AM)" else "12 AM – 6 AM"
        )
        GameStatRow(
            label = "DL : UL ratio",
            value = insights.downloadUploadRatioFormatted,
            sublabel = "Download to upload ratio"
        )
        GameStatRow(
            label = "Daily average",
            value = insights.dailyAverageFormatted.first,
            unit = insights.dailyAverageFormatted.second
        )
        GameStatRow(
            label = "Highest usage day",
            value = insights.highestUsageDayFormatted,
            sublabel = if (insights.highestUsageDayDate != "N/A") insights.highestUsageDayDate else ""
        )
        GameStatRow(
            label = "Peak usage window",
            value = insights.peakUsageTimeWindow
        )
        GameStatRow(
            label = "Weekly projected pace",
            value = insights.weeklyAverageFormatted.first,
            unit = insights.weeklyAverageFormatted.second
        )
        GameStatRow(
            label = "Active days",
            value = "${insights.activeDaysCount}",
            unit = "days"
        )
        GameStatRow(
            label = "Longest session",
            value = insights.mostActiveSessionDurationFormatted
        )
    }
}
