package com.vinnovateit.latch.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.desktop.VinnovateItLogo
import com.vinnovateit.latch.desktop.resources.Res
import com.vinnovateit.latch.desktop.resources.ic_hand_left
import com.vinnovateit.latch.desktop.resources.ic_hand_right
import com.vinnovateit.latch.ui.components.LatchIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
fun HandsConnectAnimation(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 140.dp,
    durationMs: Int = 600,
) {
    val leftOffsetX = remember { Animatable(-180f) }
    val leftOffsetY = remember { Animatable(-180f) }
    val rightOffsetX = remember { Animatable(180f) }
    val rightOffsetY = remember { Animatable(180f) }

    LaunchedEffect(Unit) {
        val spec = tween<Float>(durationMillis = durationMs, easing = FastOutSlowInEasing)
        launch { leftOffsetX.animateTo(0f, spec) }
        launch { leftOffsetY.animateTo(0f, spec) }
        launch { rightOffsetX.animateTo(0f, spec) }
        launch { rightOffsetY.animateTo(0f, spec) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_hand_right),
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp)
                .offset {
                    IntOffset(
                        rightOffsetX.value.roundToInt(),
                        rightOffsetY.value.roundToInt(),
                    )
                },
            contentScale = ContentScale.Fit,
        )

        Image(
            painter = painterResource(Res.drawable.ic_hand_left),
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp)
                .offset {
                    IntOffset(
                        leftOffsetX.value.roundToInt(),
                        leftOffsetY.value.roundToInt(),
                    )
                },
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * 1:1 match of Android LatchSetupBottomBar: morphing shape FAB, rotation animation,
 * VinnovateIT branding logo, and page indicator dots.
 */
@Composable
fun DesktopOnboardingBottomBar(
    pagerState: PagerState,
    isFinishButtonEnabled: Boolean,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
    val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)

    val targetShapeValues = when (pagerState.currentPage % 3) {
        0 -> listOf(28f, 28f, 28f, 28f) // Circle
        1 -> listOf(14f, 14f, 14f, 14f) // Rounded square
        else -> listOf(8f, 28f, 8f, 28f) // Leaf shape
    }

    val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
    val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
    val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
    val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")

    val animatedRotation by animateFloatAsState(
        targetValue = pagerState.currentPage * 360f,
        animationSpec = rotationAnimationSpec,
        label = "Rotation",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        imageVector = VinnovateItLogo,
                        contentDescription = "VinnovateIT",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(width = 110.dp, height = 36.dp),
                    )
                }

                val isLastPage = pagerState.currentPage == pagerState.pageCount - 1
                val fabShape = RoundedCornerShape(
                    topStart = animatedTopStart.dp,
                    topEnd = animatedTopEnd.dp,
                    bottomEnd = animatedBottomEnd.dp,
                    bottomStart = animatedBottomStart.dp,
                )

                FloatingActionButton(
                    onClick = {
                        if (isLastPage) onFinishClicked() else onNextClicked()
                    },
                    shape = fabShape,
                    containerColor = if (isLastPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isLastPage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.rotate(animatedRotation),
                    ) {
                        AnimatedContent(
                            targetState = isLastPage,
                            transitionSpec = {
                                (slideInVertically { it } + fadeIn()).togetherWith(
                                    slideOutVertically { -it } + fadeOut(),
                                )
                            },
                            label = "FabIcon",
                        ) { last ->
                            if (last) {
                                Icon(
                                    imageVector = LatchIcons.Check,
                                    contentDescription = "Finish",
                                    modifier = Modifier.size(24.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = LatchIcons.ArrowForwardIos,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Page Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pagerState.pageCount) { page ->
                    val isCurrent = page == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isCurrent) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }
        }
    }
}
