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
import androidx.compose.runtime.key
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

    val (maxDigitWidthPx, maxDigitHeightPx) = remember(textMeasurer, styledText) {
        var maxW = 0
        var maxH = 0
        for (d in 0..9) {
            val result = textMeasurer.measure(d.toString(), styledText)
            if (result.size.width > maxW) maxW = result.size.width
            if (result.size.height > maxH) maxH = result.size.height
        }
        maxW to maxH
    }
    val digitWidth = with(density) { (maxDigitWidthPx + 2).toDp() }
    val digitHeight = with(density) { maxDigitHeightPx.toDp() }
    val digitHeightPx = maxDigitHeightPx.toFloat()

    val resolvedColor = if (styledText.color != Color.Unspecified) {
        styledText.color
    } else {
        LocalContentColor.current
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var digitIndex = 0
        for (i in value.indices) {
            val char = value[i]
            if (char in '0'..'9') {
                val idx = digitIndex++
                key("digit_${idx}_$char") {
                    DigitRoller(
                        digit = char.digitToInt(),
                        textStyle = styledText,
                        resolvedColor = resolvedColor,
                        digitWidth = digitWidth,
                        digitHeight = digitHeight,
                        digitHeightPx = digitHeightPx,
                        colIndex = idx
                    )
                }
            } else {
                key("char_$i") {
                    Box(
                        modifier = Modifier.height(digitHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char.toString(),
                            style = styledText,
                            color = resolvedColor,
                            textAlign = TextAlign.Center
                        )
                    }
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
    digitWidth: Dp,
    digitHeight: Dp,
    digitHeightPx: Float,
    colIndex: Int
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(digit) {
        val target = (10 + digit).toFloat()
        animatable.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = 800 + (colIndex * 100).coerceAtMost(600),
                delayMillis = colIndex * 60,
                easing = CubicBezierEasing(0.12f, 0.98f, 0.32f, 1.0f)
            )
        )
    }

    Box(
        modifier = Modifier
            .width(digitWidth)
            .height(digitHeight)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationY = -animatable.value * digitHeightPx
            }
        ) {
            // 3 cycles of digits 0..9 (30 items total).
            // Cycle 0: indices 0..9. Cycle 1: indices 10..19. Cycle 2: indices 20..29.
            // Target is in cycle 1 (10 + digit). TopCenter alignment anchors y=0 to item 0.
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
                            textAlign = TextAlign.Center,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
