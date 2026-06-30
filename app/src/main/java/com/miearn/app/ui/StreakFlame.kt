package com.miearn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun StreakFlame(
    days: Int,
    modifier: Modifier = Modifier,
) {
    val level = StreakFlameLevel.fromDays(days)
    if (level == StreakFlameLevel.NONE) return

    val outerColor = when (level) {
        StreakFlameLevel.SMALL -> Color(0xFFFFB23E)
        StreakFlameLevel.MEDIUM -> Color(0xFFFF8A24)
        StreakFlameLevel.LARGE -> Color(0xFFF05A24)
        StreakFlameLevel.NONE -> Color.Transparent
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "\u8fde\u7eed\u5b66\u4e60\u706b\u82d7"
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
            drawCircle(
                color = Color(0xFFFFD65A),
                radius = minOf(scaleX, scaleY) * 0.17f,
                center = Offset(scaleX * 0.49f, scaleY * 0.72f),
            )
        }
    }
}
