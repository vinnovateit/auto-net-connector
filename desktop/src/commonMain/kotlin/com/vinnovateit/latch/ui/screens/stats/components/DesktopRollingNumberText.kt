package com.vinnovateit.latch.ui.screens.stats.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

/**
 * Animated rolling number text where digits roll vertically like an odometer / slot machine,
 * counting up one by one with staggered easing.
 */
@Composable
fun RollingNumberText(
    value: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val styledText = textStyle.copy(
        fontFeatureSettings = "tnum",
    )
    val resolvedColor = if (styledText.color != Color.Unspecified) {
        styledText.color
    } else {
        LocalContentColor.current
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var digitIndex = 0
        for (i in value.indices) {
            val char = value[i]
            if (char in '0'..'9') {
                val idx = digitIndex++
                key("digit_$idx") {
                    DigitRoller(
                        digit = char.digitToInt(),
                        textStyle = styledText,
                        resolvedColor = resolvedColor,
                        colIndex = idx,
                    )
                }
            } else {
                key("char_$i") {
                    Text(
                        text = char.toString(),
                        style = styledText,
                        color = resolvedColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitRoller(
    digit: Int,
    textStyle: TextStyle,
    resolvedColor: Color,
    colIndex: Int,
) {
    var hasAnimated by rememberSaveable(colIndex) { mutableStateOf(false) }
    var currentDigit by rememberSaveable(colIndex) { mutableIntStateOf(if (hasAnimated) digit else 0) }

    LaunchedEffect(digit) {
        if (hasAnimated) {
            currentDigit = digit
            return@LaunchedEffect
        }
        if (currentDigit == digit) {
            hasAnimated = true
            return@LaunchedEffect
        }
        delay(120L + colIndex * 25L)
        val diff = kotlin.math.abs(digit - currentDigit)
        if (diff == 0) {
            hasAnimated = true
            return@LaunchedEffect
        }
        val stepSize = if (diff > 5) kotlin.math.ceil(diff / 5.0).toInt() else 1
        val direction = if (digit > currentDigit) 1 else -1
        val totalSteps = (diff + stepSize - 1) / stepSize
        val stepDelay = (460L / totalSteps.coerceAtLeast(1)).coerceIn(80L, 110L)
        var d = currentDigit
        while (d != digit) {
            delay(stepDelay)
            d = if (direction > 0) {
                (d + stepSize).coerceAtMost(digit)
            } else {
                (d - stepSize).coerceAtLeast(digit)
            }
            currentDigit = d
        }
        hasAnimated = true
    }

    AnimatedContent(
        targetState = currentDigit,
        transitionSpec = {
            val isFinal = targetState == digit
            val duration = if (isFinal) 180 else 90
            (slideInVertically(
                animationSpec = tween(durationMillis = duration, easing = if (isFinal) FastOutSlowInEasing else LinearEasing),
            ) { height -> height } + fadeIn(tween(duration))).togetherWith(
                slideOutVertically(
                    animationSpec = tween(durationMillis = duration, easing = if (isFinal) FastOutSlowInEasing else LinearEasing),
                ) { height -> -height } + fadeOut(tween(duration)),
            ).using(SizeTransform(clip = false))
        },
        label = "DigitRoll_$colIndex",
    ) { d ->
        Text(
            text = "$d",
            style = textStyle,
            color = resolvedColor,
            textAlign = TextAlign.Center,
        )
    }
}
