package org.nicho.arca.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nicho.arca.domain.model.BudgetStatus
import kotlin.time.Duration.Companion.milliseconds

/**
 * The mascot (DSG-ARC-001 §13, DSG-08): a small boxy figure drawn on a 40 × 40 grid, exactly as
 * the prototype's Mascot board. Three moods follow FR-953's thresholds: calm below 90% spent,
 * tense from 90% to 100%, concerned above 100%, the one mood drawn in `warning`.
 *
 * Its only idle motion is a blink every 5.2 seconds, "small enough to live on the home screen
 * without pulling the eye". Decorative: TalkBack skips it.
 */
enum class MascotMood {
    CALM, TENSE, CONCERNED;

    companion object {
        fun of(status: BudgetStatus?): MascotMood = when (status) {
            BudgetStatus.OVER_BUDGET -> CONCERNED
            BudgetStatus.NEAR_LIMIT -> TENSE
            else -> CALM
        }
    }
}

/**
 * [greet]: on a first-run empty state the right hand gives one wave after the mascot lands
 * (520ms in, 900ms long), then stays still.
 */
@Composable
fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 34.dp, greet: Boolean = false) {
    val colors = ArcaDesignSystem.colors
    val ink = if (mood == MascotMood.CONCERNED) colors.warning else colors.ink
    // A blink every 5.2s: closed at 93-96% of the cycle, as the prototype's keyframes. Idle between
    // blinks, so a mascot on screen doesn't keep the frame clock running.
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4836.milliseconds)
            blink.animateTo(0.12f, tween(156, easing = LinearEasing))
            blink.animateTo(1f, tween(208, easing = LinearEasing))
        }
    }
    val wave = remember { Animatable(0f) }
    if (greet) {
        val motion = ArcaDesignSystem.motion
        LaunchedEffect(Unit) {
            delay(520.milliseconds)
            wave.animateTo(1f, tween(900, easing = motion.easeInOut))
        }
    }
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        scale(this.size.width / 40f, this.size.height / 40f, pivot = Offset.Zero) {
            drawFigure(mood, ink, blink.value, wave.value)
        }
    }
}

/** The prototype's m-wave keyframes: rotation (degrees) and lift at 0, 25, 55, 80 and 100%. */
private fun waveAt(t: Float): Pair<Float, Float> {
    val stops = floatArrayOf(0f, 0.25f, 0.55f, 0.8f, 1f)
    val angles = floatArrayOf(0f, -14f, 10f, -6f, 0f)
    val lifts = floatArrayOf(0f, -4f, -3f, -1f, 0f)
    if (t <= 0f || t >= 1f) return 0f to 0f
    val i = (1 until stops.size).first { t <= stops[it] }
    val f = (t - stops[i - 1]) / (stops[i] - stops[i - 1])
    return (angles[i - 1] + (angles[i] - angles[i - 1]) * f) to (lifts[i - 1] + (lifts[i] - lifts[i - 1]) * f)
}

private fun DrawScope.drawFigure(mood: MascotMood, ink: androidx.compose.ui.graphics.Color, blink: Float, wave: Float) {
    val line = Stroke(width = 1.6f, cap = StrokeCap.Round)
    // feet
    drawRoundRect(ink, Offset(12.5f, 29.5f), Size(6f, 4.5f), CornerRadius(2.25f))
    drawRoundRect(ink, Offset(21.5f, 29.5f), Size(6f, 4.5f), CornerRadius(2.25f))
    // hands: raised a little when tense, more when concerned
    val handY = when (mood) {
        MascotMood.CONCERNED -> 11f
        MascotMood.TENSE -> 12.5f
        MascotMood.CALM -> 14f
    }
    drawRoundRect(ink, Offset(2f, handY), Size(4.5f, 6f), CornerRadius(2.25f))
    val (angle, lift) = waveAt(wave)
    translate(0f, lift) {
        rotate(angle, pivot = Offset(35.75f, handY + 6f)) {
            drawRoundRect(ink, Offset(33.5f, handY), Size(4.5f, 6f), CornerRadius(2.25f))
        }
    }
    // body
    drawRoundRect(ink, Offset(8f, 6f), Size(24f, 22f), CornerRadius(7f), style = Stroke(width = 1.6f))
    // brows
    when (mood) {
        MascotMood.CONCERNED -> {
            drawLine(ink, Offset(13f, 12.8f), Offset(16.4f, 11.6f), 1.6f, StrokeCap.Round)
            drawLine(ink, Offset(27f, 12.8f), Offset(23.6f, 11.6f), 1.6f, StrokeCap.Round)
        }
        MascotMood.TENSE -> {
            drawLine(ink, Offset(13.2f, 11.6f), Offset(16.4f, 11.6f), 1.6f, StrokeCap.Round)
            drawLine(ink, Offset(23.6f, 11.6f), Offset(26.8f, 11.6f), 1.6f, StrokeCap.Round)
        }
        MascotMood.CALM -> Unit
    }
    // eyes, blinking about their own centres
    val eyeY = if (mood == MascotMood.CONCERNED) 15.5f else 15f
    for (x in floatArrayOf(15f, 25f)) {
        scale(1f, blink, pivot = Offset(x, eyeY)) { drawCircle(ink, 1.6f, Offset(x, eyeY)) }
    }
    // mouth
    val mouth = Path().apply {
        when (mood) {
            MascotMood.CONCERNED -> { moveTo(16f, 21.8f); cubicTo(17.3f, 20.3f, 22.7f, 20.3f, 24f, 21.8f) }
            MascotMood.TENSE -> { moveTo(16.5f, 21.2f); lineTo(23.5f, 21.2f) }
            MascotMood.CALM -> { moveTo(16f, 20.6f); cubicTo(17.3f, 22.7f, 22.7f, 22.7f, 24f, 20.6f) }
        }
    }
    drawPath(mouth, ink, style = line)
}
