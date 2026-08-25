package com.miearn.app.ui

import android.content.Context
import android.provider.Settings

internal object StreakMotionPolicy {
    fun shouldAnimate(screenStarted: Boolean, systemAnimatorScale: Float): Boolean =
        screenStarted && systemAnimatorScale > 0f

    fun readSystemAnimatorScale(context: Context): Float {
        val resolver = context.contentResolver
        return listOf(
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE,
        ).minOf { key ->
            runCatching { Settings.Global.getFloat(resolver, key) }
                .getOrDefault(1f)
        }
    }
}
