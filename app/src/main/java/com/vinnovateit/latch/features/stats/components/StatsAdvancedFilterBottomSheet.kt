package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.features.stats.DateRangeFilter

data class AdvancedFilterOption(
    val filter: DateRangeFilter,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsAdvancedFilterBottomSheet(
    selectedFilter: DateRangeFilter,
    onFilterSelected: (DateRangeFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    val advancedOptions = remember {
        listOf(
            AdvancedFilterOption(
                filter = DateRangeFilter.LAST_YEAR,
                description = "Jan 1 to Dec 31 of previous year",
                icon = Icons.Rounded.History
            ),
            AdvancedFilterOption(
                filter = DateRangeFilter.ALL_TIME,
                description = "Earliest record date to now",
                icon = Icons.Rounded.AllInclusive
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Advanced Filters",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Select a historical or extended time window for usage statistics.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(16.dp))

            advancedOptions.forEach { option ->
                val isSelected = option.filter == selectedFilter
                val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onFilterSelected(option.filter)
                                onDismiss()
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.filter.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor
                        )
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
