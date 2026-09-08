package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
