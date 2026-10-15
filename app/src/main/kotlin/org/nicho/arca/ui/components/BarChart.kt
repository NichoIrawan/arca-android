package org.nicho.arca.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nicho.arca.designsystem.ArcaDesignSystem
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [BarChart]'s colours: [rest] for a bar, [active] for the selected or touched one, and a
 * second pair for bars below zero (a loss, a negative balance).
 */
@Immutable
data class BarColors(
    val rest: Color,
    val active: Color,
    val negativeRest: Color,
    val negativeActive: Color,
)

@Composable
fun neutralBarColors(): BarColors {
    val colors = ArcaDesignSystem.colors
    return BarColors(colors.inkTertiary, colors.ink, colors.negative.copy(alpha = 0.45f), colors.negative)
}

/**
 * The columns of Reports' trend and Account Detail's balance trend (boards 06, 08), drawn as
 * the prototype draws them: no track behind a bar, the selected bar in [BarColors.active] and
 * the rest quieter, every bar growing from zero, left to right (m-reveal-y: 640ms, a long soft
 * ease-out, 60ms apart, 12ms when there are more than ten). A new [replayKey] grows them again;
 * new values for the same key ease from the old heights.
 *
 * [fractions] are each bar's size against the largest (−1..1, computed in :domain); a negative
 * one hangs below a zero line. A bar never draws shorter than a 3dp stub, so an empty day still
 * has a mark to touch.
 *
 * Touch: a press highlights the bar under the finger, and dragging sideways glides a soft
 * highlight from bar to bar with a light tick ("hover", client request 2026-10-15). [onHover]
 * follows the finger (or a mouse pointer) live; letting go selects that bar through [onSelect].
 * When [clearable], tapping the selected bar again clears the selection (null). A vertical drag
 * is left to the page, so the chart never traps a scroll.
 *
 * Everything is drawn on one canvas; the animations run in the draw phase and never recompose.
 */
@Composable
fun BarChart(
    fractions: List<Float>,
    labels: List<String>,
    spoken: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    replayKey: Any?,
    modifier: Modifier = Modifier,
    onHover: (Int?) -> Unit = {},
    colors: BarColors = neutralBarColors(),
    height: Dp = 96.dp,
    maxBarWidth: Dp = 40.dp,
    clearable: Boolean = false,
) {
    val motion = ArcaDesignSystem.motion
    val highlight = ArcaDesignSystem.colors.surfaceSunken
    val hairline = ArcaDesignSystem.colors.hairline
    val labelRest = ArcaDesignSystem.colors.inkTertiary
    val labelActive = ArcaDesignSystem.colors.ink
    val labelStyle = ArcaDesignSystem.typography.footnote
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val n = fractions.size
    val step = if (n > 10) 12 else 60

    // Growth from zero, one clock for every bar.
    val revealSpan = (motion.revealMillis + step * (n - 1).coerceAtLeast(0)).toFloat()
    val reveal = remember(replayKey) { Animatable(0f) }
    LaunchedEffect(replayKey) {
        delay(80.milliseconds)
        reveal.animateTo(revealSpan, tween(revealSpan.toInt(), easing = LinearEasing))
    }

    // New values under the same key ease from where the bars stood.
    val heights = remember { BarHeights() }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(fractions, replayKey) {
        val target = fractions.toFloatArray()
        if (heights.key != replayKey || heights.to.size != target.size) {
            heights.key = replayKey
            heights.from = target
            heights.to = target
            morph.snapTo(1f)
        } else {
            heights.from = heights.current(morph.value)
            heights.to = target
            morph.snapTo(0f)
            morph.animateTo(1f, tween(motion.revealMillis, easing = motion.revealEasing))
        }
    }

    // The touched bar, and the soft highlight that glides between bars after it.
    var hovered by remember { mutableIntStateOf(-1) }
    val glide = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val currentOnHover by rememberUpdatedState(onHover)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    fun hover(index: Int) {
        if (index == hovered) return
        val wasShowing = hovered >= 0
        hovered = index
        currentOnHover(index.takeIf { it >= 0 })
        scope.launch {
            if (index < 0) {
                glow.animateTo(0f, tween(motion.stateMillis))
            } else {
                if (!wasShowing) glide.snapTo(index.toFloat()) else launch { glide.animateTo(index.toFloat(), tween(120, easing = motion.easeOut)) }
                glow.animateTo(1f, tween(120))
            }
        }
    }

    val measurer = rememberTextMeasurer(cacheSize = 0)
    val labelLayouts = remember(labels, labelStyle) {
        labels.map { if (it.isEmpty()) null else measurer.measure(it, labelStyle, maxLines = 1, softWrap = false) }
    }
    val labelGap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    val labelHeight = labelLayouts.firstNotNullOfOrNull { it }?.size?.height ?: 0

    Box(modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(height + with(androidx.compose.ui.platform.LocalDensity.current) { (labelGap + labelHeight).toDp() })
                .pointerInput(n, clearable) {
                    if (n == 0) return@pointerInput
                    fun indexAt(x: Float) = (x / size.width * n).toInt().coerceIn(0, n - 1)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var index = indexAt(down.position.x)
                        hover(index)
                        var scrubbing = false
                        var travelled = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val again = !scrubbing && clearable && index == currentSelected
                                currentOnSelect(if (again) null else index)
                                hover(-1)
                                return@awaitEachGesture
                            }
                            if (change.isConsumed) break
                            if (!scrubbing) {
                                travelled += change.positionChange()
                                if (travelled.getDistance() > viewConfiguration.touchSlop) {
                                    // Up or down is the page's scroll; sideways is a scrub.
                                    if (abs(travelled.y) > abs(travelled.x)) break
                                    scrubbing = true
                                }
                            }
                            if (scrubbing) {
                                change.consume()
                                val next = indexAt(change.position.x)
                                if (next != index) {
                                    index = next
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }
                                hover(index)
                            }
                        }
                        hover(-1)
                    }
                }
                .pointerInput(n) {
                    if (n == 0) return@pointerInput
                    // A mouse or stylus hovering without a press.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Move -> if (!change.pressed) hover((change.position.x / size.width * n).toInt().coerceIn(0, n - 1))
                                PointerEventType.Exit -> if (!change.pressed) hover(-1)
                                else -> Unit
                            }
                        }
                    }
                }
                .drawBehind {
                    if (n == 0) return@drawBehind
                    val chart = height.toPx()
                    val headroom = 8.dp.toPx()
                    val slot = size.width / n
                    val thin = n > 10
                    val barWidth = if (thin) (slot - 3.dp.toPx()).coerceIn(1.5.dp.toPx(), 10.dp.toPx()) else minOf(slot * 0.8f, maxBarWidth.toPx())
                    val radius = if (thin) barWidth / 2 else 6.dp.toPx()
                    val stub = 3.dp.toPx()

                    val shown = heights.current(morph.value)
                    var up = 0f
                    var down = 0f
                    for (f in shown) {
                        if (f > up) up = f
                        if (-f > down) down = -f
                    }
                    val reach = up + down
                    val usable = chart - headroom * (if (down > 0f) 2 else 1)
                    val baseline = if (reach == 0f) chart else headroom + usable * (up / reach)
                    if (down > 0f) drawRect(hairline, Offset(0f, baseline - 0.5.dp.toPx()), Size(size.width, 1.dp.toPx()))

                    val glowAlpha = glow.value
                    if (glowAlpha > 0f) {
                        drawRoundRect(
                            highlight.copy(alpha = highlight.alpha * glowAlpha),
                            topLeft = Offset(glide.value * slot + 1.dp.toPx(), 0f),
                            size = Size(slot - 2.dp.toPx(), chart),
                            cornerRadius = CornerRadius(minOf(slot / 2, 10.dp.toPx())),
                        )
                    }

                    val clock = reveal.value
                    val easing = motion.revealEasing
                    for (i in 0 until n) {
                        val grown = easing.transform(((clock - i * step) / motion.revealMillis).coerceIn(0f, 1f))
                        val f = shown.getOrElse(i) { 0f }
                        val near = (1f - abs(glide.value - i)).coerceIn(0f, 1f) * glowAlpha
                        val emphasis = maxOf(if (i == selected) 1f else 0f, near)
                        val negative = f < 0f
                        val color = lerp(if (negative) colors.negativeRest else colors.rest, if (negative) colors.negativeActive else colors.active, emphasis)
                        val length = maxOf(stub, if (reach == 0f) 0f else abs(f) / reach * usable) * grown
                        val width = barWidth * (1f + 0.18f * near)
                        val x = i * slot + (slot - width) / 2
                        val top = if (negative) baseline else baseline - length
                        if (length > 0f) drawRoundRect(color, Offset(x, top), Size(width, length), CornerRadius(minOf(radius, width / 2)))

                        val label = labelLayouts.getOrNull(i) ?: continue
                        val labelX = (i * slot + (slot - label.size.width) / 2).coerceIn(0f, size.width - label.size.width)
                        drawText(label, lerp(labelRest, labelActive, emphasis), topLeft = Offset(labelX, chart + labelGap))
                    }
                },
        )
        // TalkBack: one node per bar, over the drawing.
        Row(Modifier.matchParentSize()) {
            for (i in 0 until n) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics {
                            contentDescription = spoken.getOrElse(i) { "" }
                            this.selected = i == selected
                            onClick { onSelect(i); true }
                        },
                )
            }
        }
    }
}

/** The heights being drawn: easing [from] the last values [to] the new ones. */
private class BarHeights {
    var key: Any? = Any()
    var from = FloatArray(0)
    var to = FloatArray(0)

    fun current(progress: Float): FloatArray =
        if (progress >= 1f || from.size != to.size) to else FloatArray(to.size) { from[it] + (to[it] - from[it]) * progress }
}
