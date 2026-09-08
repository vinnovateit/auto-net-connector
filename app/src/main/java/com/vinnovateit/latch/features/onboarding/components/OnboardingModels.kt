package com.vinnovateit.latch.features.onboarding.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
data class SlideContent(
    val title: String,
    val description: AnnotatedString,
    val icon: @Composable () -> Unit,
    val icons: List<Int> = emptyList()
)