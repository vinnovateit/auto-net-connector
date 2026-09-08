package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinnovateit.latch.core.stats.StatsInsights
import com.vinnovateit.latch.features.settings.manager.SettingsManager
import com.vinnovateit.latch.ui.theme.LocalIsDarkTheme

@Composable
fun UsageInsightsCards(
    insights: StatsInsights,
    modifier: Modifier = Modifier,
) {
    val usePureBlack by SettingsManager.usePureBlack.collectAsStateWithLifecycle()
    val isAmoled = usePureBlack && LocalIsDarkTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Usage Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Staggered Masonry Layout (Two complementary columns)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Column 1
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Top Usage Day (Hero tile with prominent number)
                MasonryInsightTile(
                    label = "TOP DAY",
                    icon = Icons.Rounded.EmojiEvents,
                    iconTint = MaterialTheme.colorScheme.primary,
                    primaryNumber = insights.highestUsageDayFormatted,
                    secondaryUnit = "",
                    subtitle = if (insights.highestUsageDayDate != "N/A") "on ${insights.highestUsageDayDate}" else "No session logged",
                    isAmoled = isAmoled,
                    extraContent = {
                        if (insights.highestUsageDayDate != "N/A") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "Peak Record",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                )

                // 2. Daily Average
                MasonryInsightTile(
                    label = "DAILY AVERAGE",
                    icon = Icons.Rounded.TrendingUp,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    primaryNumber = insights.dailyAverageFormatted.first,
                    secondaryUnit = insights.dailyAverageFormatted.second,
                    subtitle = "across ${insights.activeDaysCount} active days",
                    isAmoled = isAmoled
                )

                // 3. Active Days
                MasonryInsightTile(
                    label = "ACTIVE DAYS",
                    icon = Icons.Rounded.CalendarMonth,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    primaryNumber = "${insights.activeDaysCount}",
                    secondaryUnit = "days",
                    subtitle = "with portal connections",
                    isAmoled = isAmoled
                )
            }

            // Column 2
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Peak Window
                MasonryInsightTile(
                    label = "PEAK WINDOW",
                    icon = Icons.Rounded.Schedule,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    primaryNumber = insights.peakUsageTimeWindow,
                    secondaryUnit = "",
                    subtitle = "busiest traffic window",
                    isAmoled = isAmoled,
                    extraContent = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "High Traffic",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                )

                // 2. Weekly Projected Pace
                MasonryInsightTile(
                    label = "WEEKLY PACE",
                    icon = Icons.Rounded.DateRange,
                    iconTint = MaterialTheme.colorScheme.primary,
                    primaryNumber = insights.weeklyAverageFormatted.first,
                    secondaryUnit = insights.weeklyAverageFormatted.second,
                    subtitle = "projected 7-day volume",
                    isAmoled = isAmoled
                )

                // 3. Longest Session
                MasonryInsightTile(
                    label = "LONGEST SESSION",
                    icon = Icons.Rounded.Timer,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    primaryNumber = insights.mostActiveSessionDurationFormatted,
                    secondaryUnit = "",
                    subtitle = "active continuous session",
                    isAmoled = isAmoled
                )
            }
        }
    }
}

@Composable
private fun MasonryInsightTile(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    primaryNumber: String,
    secondaryUnit: String,
    subtitle: String,
    isAmoled: Boolean,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = primaryNumber,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (secondaryUnit.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = secondaryUnit,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = iconTint,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            extraContent?.invoke()
        }
    }
}
