package com.vinnovateit.latch.features.stats.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

/**
 * Animated rolling number text where digits roll vertically like an odometer / slot machine,
 * counting up one by one with staggered easing.
 */
@Composable
fun RollingNumberText(
    value: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val styledText = textStyle.copy(
        fontFeatureSettings = "tnum"
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textLayoutResult = remember(textMeasurer, styledText, density) {
        textMeasurer.measure("0", styledText)
    }
    val digitWidth = with(density) { textLayoutResult.size.width.toDp() }
    val digitHeight = with(density) { textLayoutResult.size.height.toDp() }
    val digitHeightPx = textLayoutResult.size.height.toFloat()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var digitIndex = 0
        for (i in value.indices) {
            val char = value[i]
            if (char in '0'..'9') {
                DigitRoller(
                    digit = char.digitToInt(),
                    textStyle = styledText,
                    digitWidth = digitWidth,
                    digitHeight = digitHeight,
                    digitHeightPx = digitHeightPx,
                    colIndex = digitIndex++
                )
            } else {
                Text(
                    text = char.toString(),
                    style = styledText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DigitRoller(
    digit: Int,
    textStyle: TextStyle,
    digitWidth: Dp,
    digitHeight: Dp,
    digitHeightPx: Float,
    colIndex: Int
) {
    val animatable = remember { Animatable(0f) }

    val resolvedColor = if (textStyle.color != Color.Unspecified) {
        textStyle.color
    } else {
        LocalContentColor.current
    }

    LaunchedEffect(digit) {
        // Target is in the second cycle (indices 10..19) so initial roll from 0 completes 1 full spin + lands
        val target = (10 + digit).toFloat()
        animatable.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = 900 + (colIndex * 90).coerceAtMost(500),
                delayMillis = colIndex * 70,
                easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
            )
        )
    }

    Box(
        modifier = Modifier
            .width(digitWidth)
            .height(digitHeight)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationY = -animatable.value * digitHeightPx
            }
        ) {
            // 3 cycles of digits 0..9 (30 items total)
            for (cycle in 0..2) {
                for (d in 0..9) {
                    Box(
                        modifier = Modifier
                            .width(digitWidth)
                            .height(digitHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$d",
                            style = textStyle,
                            color = resolvedColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
