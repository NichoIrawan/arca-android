package org.nicho.arca.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Motion tokens from the Android prototype (DSG-ARC-001 §8). Native, not choreographed: short
 * durations, and no motion that celebrates or scolds.
 */
@Immutable
data class ArcaMotion(
    val pressMillis: Int,
    val stateMillis: Int,
    val moveMillis: Int,
    val sheetMillis: Int,
    val easeOut: Easing,      // entering, responding
    val easeInOut: Easing,    // moving across the screen
    val easeDrawer: Easing,   // sheets and pushes
    /** Rolling numbers (§8.3): a figure that changes counts to its new value. */
    val rollingNumberMillis: Int,
    val rollingNumberEasing: Easing,
)

/**
 * The prototype's rolling-number curve, a CSS `linear()` with 25 evenly spaced stops: a fast
 * start that settles gently. Interpolated linearly between the stops.
 */
private object RollingNumberEasing : Easing {
    private val stops = floatArrayOf(
        0f, 0.0575f, 0.1802f, 0.3206f, 0.4549f, 0.5727f, 0.6709f, 0.7499f, 0.812f, 0.86f, 0.8965f,
        0.9239f, 0.9444f, 0.9596f, 0.9707f, 0.9789f, 0.9848f, 0.9891f, 0.9922f, 0.9944f, 0.996f,
        0.9972f, 0.998f, 0.9986f, 1f,
    )

    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val position = fraction * (stops.size - 1)
        val i = position.toInt()
        val t = position - i
        return stops[i] + (stops[i + 1] - stops[i]) * t
    }
}

val ArcaMotionTokens = ArcaMotion(
    pressMillis = 140,
    stateMillis = 180,
    moveMillis = 260,
    sheetMillis = 420,
    easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
    easeInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f),
    easeDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f),
    rollingNumberMillis = 900,
    rollingNumberEasing = RollingNumberEasing,
)
