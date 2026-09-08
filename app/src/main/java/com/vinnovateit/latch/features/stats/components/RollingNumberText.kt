package com.vinnovateit.latch.features.stats.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.remember
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
    modifier: Modifier = Modifier
) {
    val styledText = textStyle.copy(
        fontFeatureSettings = "tnum"
    )

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
                        colIndex = idx
                    )
                }
            } else {
                key("char_$i") {
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

@Composable
private fun DigitRoller(
    digit: Int,
    textStyle: TextStyle,
    resolvedColor: Color,
    colIndex: Int
) {
    var currentDigit by remember { mutableIntStateOf(0) }

    LaunchedEffect(digit) {
        if (digit == 0) {
            currentDigit = 0
            return@LaunchedEffect
        }
        delay(colIndex * 40L)
        val stepDelay = (500L / digit).coerceIn(40L, 100L)
        for (d in 1..digit) {
            delay(stepDelay)
            currentDigit = d
        }
    }

    AnimatedContent(
        targetState = currentDigit,
        transitionSpec = {
            (slideInVertically(
                animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)
            ) { height -> height } + fadeIn(tween(150))).togetherWith(
                slideOutVertically(
                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
                ) { height -> -height } + fadeOut(tween(150))
            )
        },
        label = "DigitRoll_$colIndex"
    ) { d ->
        Text(
            text = "$d",
            style = textStyle,
            color = resolvedColor,
            textAlign = TextAlign.Center
        )
    }
}
