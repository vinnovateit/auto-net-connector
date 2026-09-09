package com.vinnovateit.latch.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.vinnovateit.latch.ui.theme.LatchTheme
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/** Default window dimensions (420dp x 700dp) for modern desktop layout. */
private const val PREFERRED_W = 420f
private const val PREFERRED_H = 700f

/** Maximum fraction of screen usable height/width. */
private const val MAX_SCREEN_FRACTION = 0.95f

/** Minimum window dimensions. */
private const val MIN_W = 360
private const val MIN_H = 600

private fun preferredWindowSize(): DpSize {
    return try {
        val gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        val scale = gc.defaultTransform.scaleY.toFloat().coerceAtLeast(1f)

        val usableW = (gc.bounds.width - insets.left - insets.right) / scale
        val usableH = (gc.bounds.height - insets.top - insets.bottom) / scale

        val fit = minOf(
            1f,
            usableW * MAX_SCREEN_FRACTION / PREFERRED_W,
            usableH * MAX_SCREEN_FRACTION / PREFERRED_H,
        )

        DpSize(
            width = (PREFERRED_W * fit).coerceAtLeast(MIN_W.toFloat()).coerceAtMost(usableW).dp,
            height = (PREFERRED_H * fit).coerceAtLeast(MIN_H.toFloat()).coerceAtMost(usableH).dp,
        )
    } catch (e: Throwable) {
        DpSize(PREFERRED_W.dp, PREFERRED_H.dp)
    }
}

/**
 * The main desktop window.
 *
 * Uses native OS outer window chrome (title bar and controls), is resizable,
 * and opens centered on screen.
 */
@Composable
internal fun LatchWindow(
    visible: Boolean,
    restoreTrigger: Int = 0,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val initialSize = remember { preferredWindowSize() }
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = initialSize,
    )

    LaunchedEffect(visible, restoreTrigger) {
        if (visible) {
            state.isMinimized = false
        }
    }

    Window(
        visible = visible,
        onCloseRequest = onCloseRequest,
        state = state,
        resizable = true,
        undecorated = false,
        transparent = false,
        title = "Latch",
        icon = remember { LatchIcon.brand() },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = java.awt.Dimension(360, 600)
        }

        LaunchedEffect(visible, restoreTrigger) {
            if (visible) {
                state.isMinimized = false
                (window as? java.awt.Frame)?.state = java.awt.Frame.NORMAL
                (window as? java.awt.Frame)?.extendedState = java.awt.Frame.NORMAL
                window.toFront()
                window.requestFocus()
            }
        }

        LatchTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}
