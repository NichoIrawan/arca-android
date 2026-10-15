package org.nicho.arca.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.nicho.arca.designsystem.ArcaDesignSystem
import kotlin.time.Duration.Companion.milliseconds

/*
 * The prototype's motion (motion.css), in Compose. Principles it states and this keeps:
 * frequency decides whether something animates; only transform, opacity and size move; nothing
 * counts up, bounces or loops for decoration on a screen full of money.
 */

/**
 * Tab screens: the header and tab bar stay put; the content arrives block by block, each a short
 * rise and fade a step after the one before, and the stagger stops after the 7th so a long list
 * never makes the page wait (m-page, tightened 2026-10-15 so a screen settles in about 200ms).
 *
 * One clock drives every block, so a block composed late (scrolled into view after the arrival)
 * simply appears.
 */
@Stable
class PageArrival internal constructor(private val clock: Animatable<Float, *>, private val stepMillis: Int, private val durationMillis: Int) {
    fun progress(index: Int): Float {
        val start = index.coerceAtMost(6) * stepMillis
        val t = ((clock.value - start) / durationMillis).coerceIn(0f, 1f)
        return EaseOutCurve.transform(t)
    }
}

private val EaseOutCurve = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
fun rememberPageArrival(): PageArrival {
    val motion = ArcaDesignSystem.motion
    val total = (6 * motion.pageStepMillis + motion.pageInMillis).toFloat()
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(total, tween(total.toInt(), easing = LinearEasing)) }
    return remember(clock) { PageArrival(clock, motion.pageStepMillis, motion.pageInMillis) }
}

/** Block [index] of a page arriving through [arrival]. */
fun Modifier.arriving(arrival: PageArrival, index: Int): Modifier = graphicsLayer {
    val p = arrival.progress(index)
    alpha = p
    translationY = (1f - p) * 8.dp.toPx()
}

/**
 * Pushed screens: the content arrives from the right, the direction of "deeper" (m-push), in under
 * 100ms (client request, 2026-10-15: every transition feels instant). The header isn't animated,
 * so Back is there the instant the screen is.
 */
@Composable
fun Modifier.pushArrival(): Modifier {
    val motion = ArcaDesignSystem.motion
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(motion.pushInMillis, easing = motion.easeDrawer)) }
    return graphicsLayer {
        alpha = p.value
        translationX = (1f - p.value) * 16.dp.toPx()
    }
}

/**
 * A 0..1 value that grows from zero the first time it's shown, after [delayMillis], then follows
 * [target] (m-reveal-x / m-reveal-y: 640ms, a long soft ease-out). Changing [replayKey] (the month,
 * the range) grows it from zero again, as the prototype's charts replay.
 *
 * Read [State.value] in a draw or layout lambda, not in composition, and the animation never
 * recomposes anything.
 */
@Composable
fun rememberReveal(target: Float, delayMillis: Int = 80, replayKey: Any? = Unit): State<Float> {
    val motion = ArcaDesignSystem.motion
    val value = remember(replayKey) { Animatable(0f) }
    val first = remember(replayKey) { booleanArrayOf(true) }
    LaunchedEffect(target, replayKey) {
        if (first[0]) {
            first[0] = false
            kotlinx.coroutines.delay(delayMillis.toLong().milliseconds)
        }
        value.animateTo(target.coerceIn(0f, 1f), tween(motion.revealMillis, easing = motion.revealEasing))
    }
    return value.asState()
}

/** [rememberReveal], read in composition: for a value that sizes a layout. */
@Composable
fun revealFraction(target: Float, delayMillis: Int = 80, replayKey: Any? = Unit): Float =
    rememberReveal(target, delayMillis, replayKey).value

/**
 * A figure whose digits roll to a new value, column by column, when it changes in place (the
 * prototype's NumberFlow). Shown the first time without motion.
 *
 * Positions are matched from the right, so the units digit always rolls into the units digit.
 * Each digit is one drawn cell: the ten glyphs are measured once per style, and the roll moves
 * them in the draw phase, so a rolling figure never recomposes.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val motion = ArcaDesignSystem.motion
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val glyphs = remember(style, measurer) { Array(10) { measurer.measure(it.toString(), style, maxLines = 1, softWrap = false) } }
    val cellWidth = remember(glyphs) { glyphs.maxOf { it.size.width } }
    val lineHeight = glyphs[0].size.height
    val density = LocalDensity.current
    val cellSize = with(density) { Modifier.width(cellWidth.toDp()).height(lineHeight.toDp()) }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        val chars = text.toList()
        chars.forEachIndexed { i, ch ->
            val fromRight = chars.size - 1 - i
            key(fromRight) {
                if (ch.isDigit()) {
                    val shown = animateFloatAsState(
                        targetValue = (ch - '0').toFloat(),
                        animationSpec = tween(motion.rollingNumberMillis, easing = motion.rollingNumberEasing),
                        label = "digit",
                    )
                    Spacer(
                        cellSize
                            .clipToBounds()
                            .drawBehind {
                                val v = shown.value
                                val low = v.toInt().coerceIn(0, 9)
                                val f = v - low
                                val y = -f * lineHeight
                                val a = glyphs[low]
                                drawText(a, color, topLeft = Offset((size.width - a.size.width) / 2f, y))
                                if (f > 0f && low < 9) {
                                    val b = glyphs[low + 1]
                                    drawText(b, color, topLeft = Offset((size.width - b.size.width) / 2f, y + lineHeight))
                                }
                            },
                    )
                } else {
                    Text(ch.toString(), style = style, color = color, maxLines = 1)
                }
            }
        }
    }
}
