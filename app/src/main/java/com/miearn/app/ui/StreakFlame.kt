package com.miearn.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun StreakFlame(
    days: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    isVisible: Boolean = true,
) {
    val level = StreakFlameLevel.fromDays(days)
    if (level == StreakFlameLevel.NONE) return

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val shouldAnimate = animated &&
        isVisible &&
        StreakMotionPolicy.shouldAnimate(
            screenStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED),
            systemAnimatorScale = StreakMotionPolicy.readSystemAnimatorScale(LocalContext.current),
        )
    val infiniteTransition = rememberInfiniteTransition(label = "streak-flame")
    val outerMotion = if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2_200,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "outer-flame-motion",
        ).value
    } else {
        0.45f
    }
    val innerMotion = if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1_600,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "inner-flame-motion",
        ).value
    } else {
        0.55f
    }

    val outerColor = when (level) {
        StreakFlameLevel.SMALL -> Color(0xFFFFB23E)
        StreakFlameLevel.MEDIUM -> Color(0xFFFF8A24)
        StreakFlameLevel.LARGE -> Color(0xFFF05A24)
        StreakFlameLevel.NONE -> Color.Transparent
    }
    val outerScale = 0.97f + outerMotion * 0.065f
    val innerScale = 0.88f + innerMotion * 0.18f
    val innerAlpha = 0.78f + innerMotion * 0.22f

    Canvas(
        modifier = modifier
            .graphicsLayer {
                scaleX = outerScale
                scaleY = outerScale
                translationY = -3f * outerMotion
                rotationZ = -1f + outerMotion * 2f
            }
            .semantics {
                contentDescription = "连续学习火苗，$days 天"
            },
    ) {
        val scaleX = size.width
        val scaleY = size.height
        val outer = Path().apply {
            moveTo(scaleX * 0.52f, scaleY * 0.04f)
            cubicTo(
                scaleX * 0.62f,
                scaleY * 0.24f,
                scaleX * 0.88f,
                scaleY * 0.38f,
                scaleX * 0.84f,
                scaleY * 0.68f,
            )
            cubicTo(
                scaleX * 0.80f,
                scaleY * 0.92f,
                scaleX * 0.60f,
                scaleY,
                scaleX * 0.43f,
                scaleY * 0.96f,
            )
            cubicTo(
                scaleX * 0.16f,
                scaleY * 0.90f,
                scaleX * 0.08f,
                scaleY * 0.68f,
                scaleX * 0.18f,
                scaleY * 0.45f,
            )
            cubicTo(
                scaleX * 0.24f,
                scaleY * 0.31f,
                scaleX * 0.39f,
                scaleY * 0.20f,
                scaleX * 0.52f,
                scaleY * 0.04f,
            )
            close()
        }
        drawPath(outer, outerColor)

        if (level != StreakFlameLevel.SMALL) {
            val inner = Path().apply {
                moveTo(scaleX * 0.50f, scaleY * 0.48f)
                cubicTo(
                    scaleX * 0.64f,
                    scaleY * 0.61f,
                    scaleX * 0.65f,
                    scaleY * 0.82f,
                    scaleX * 0.51f,
                    scaleY * 0.91f,
                )
                cubicTo(
                    scaleX * 0.38f,
                    scaleY * 0.88f,
                    scaleX * 0.34f,
                    scaleY * 0.72f,
                    scaleX * 0.40f,
                    scaleY * 0.61f,
                )
                cubicTo(
                    scaleX * 0.43f,
                    scaleY * 0.55f,
                    scaleX * 0.47f,
                    scaleY * 0.51f,
                    scaleX * 0.50f,
                    scaleY * 0.48f,
                )
                close()
            }
            withTransform(
                {
                    scale(
                        scaleX = innerScale,
                        scaleY = innerScale,
                        pivot = Offset(scaleX * 0.50f, scaleY * 0.78f),
                    )
                },
            ) {
                drawPath(
                    path = inner,
                    color = Color(0xFFFFD65A).copy(alpha = innerAlpha),
                )
            }
        }
    }
}
