package org.nicho.arca.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch
import org.nicho.arca.designsystem.ArcaMotionTokens
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon

/**
 * The prototype's press feedback: a small scale-down and fade while pressed, no ripple
 * (DSG-ARC-001 §8: native, not choreographed).
 *
 * Built as an [IndicationNodeFactory], so a pressable row costs no composition work of its own:
 * the interaction source is made on first touch and the effect is drawn, never recomposed.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    disabledAlpha: Float = 0.4f,
): Modifier = this
    .then(if (!enabled && disabledAlpha < 1f) Modifier.alpha(disabledAlpha) else Modifier)
    .clickable(
        interactionSource = null,
        indication = PressIndication.of(pressedScale),
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )

private class PressIndication private constructor(private val pressedScale: Float) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource, pressedScale)
    override fun equals(other: Any?): Boolean = other is PressIndication && other.pressedScale == pressedScale
    override fun hashCode(): Int = pressedScale.hashCode()

    companion object {
        private val cache = HashMap<Float, PressIndication>()
        fun of(pressedScale: Float): PressIndication = cache.getOrPut(pressedScale) { PressIndication(pressedScale) }
    }
}

private class PressNode(private val source: InteractionSource, private val pressedScale: Float) : Modifier.Node(), DrawModifierNode {
    private val progress = Animatable(0f)
    private val paint = Paint()

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val target = if (presses > 0) 1f else 0f
                launch { progress.animateTo(target, tween(ArcaMotionTokens.pressMillis, easing = ArcaMotionTokens.easeOut)) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = progress.value
        if (p == 0f) {
            drawContent()
            return
        }
        val s = 1f - (1f - pressedScale) * p
        paint.alpha = 1f - 0.4f * p
        val content = this
        scale(s) {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                content.drawContent()
                canvas.restore()
            }
        }
    }
}

/** A screen's title row: optional overline, the title, and trailing icon actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = ArcaDesignSystem.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.screenMargin, end = spacing.screenMargin - 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(overline, style = ArcaDesignSystem.typography.footnote, color = ArcaDesignSystem.colors.inkTertiary)
            }
            Text(
                title,
                style = ArcaDesignSystem.typography.title,
                color = ArcaDesignSystem.colors.ink,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = ArcaDesignSystem.typography.footnote,
                    color = ArcaDesignSystem.colors.inkTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), content = actions)
    }
}

/**
 * A 36dp icon button (back, settings, filter…), as the prototype draws it. Compose extends a
 * small target's touch area to the 48dp minimum by itself (SDD-ARC-002 §9.10), so the visual size
 * can stay 36dp.
 */
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ArcaDesignSystem.colors.inkSecondary,
    enabled: Boolean = true,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(onClick, enabled = enabled, pressedScale = 0.86f, disabledAlpha = 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        ArcaIcon(icon, tint = tint, contentDescription = contentDescription)
    }
}

/** Section label: small, tracked, uppercase (DSG-ARC-001 §4.2), with an optional trailing text. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = ArcaDesignSystem.typography.label,
            color = ArcaDesignSystem.colors.inkSecondary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            val trailingModifier = if (onTrailingClick != null) Modifier.pressable(onTrailingClick) else Modifier
            Text(trailing, style = ArcaDesignSystem.typography.footnote, color = ArcaDesignSystem.colors.inkTertiary, modifier = trailingModifier)
        }
    }
}

/** The 40dp icon tile of every row (DSG-ARC-001 §7.2): neutral fill, the glyph carries the colour. */
@Composable
fun IconTile(
    @DrawableRes icon: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = ArcaDesignSystem.colors.surfaceSunken,
) {
    Box(
        modifier
            .size(size)
            .clip(ArcaDesignSystem.shapes.small)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        ArcaIcon(icon, tint = tint, size = if (size >= 40.dp) 19.dp else 17.dp)
    }
}

/**
 * A 6dp pill bar: the envelope drain bar, category share bars. [fraction] is 0..1 geometry. It
 * grows from zero after [delayMillis] the first time it's shown (m-reveal-x); pass the data's
 * period as [replayKey] so a month change grows it again.
 */
@Composable
fun ProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = ArcaDesignSystem.colors.surfaceSunken,
    delayMillis: Int = 80,
    replayKey: Any? = Unit,
) {
    val shown = rememberReveal(fraction, delayMillis, replayKey)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val width = size.width * shown.value
                if (width > 0f) drawRoundRect(color, size = Size(width, size.height), cornerRadius = radius)
            },
    )
}

/** A 1dp rule, inset under the text column of a row list (DSG-ARC-001 §7.2). */
@Composable
fun Hairline(modifier: Modifier = Modifier, startInset: Dp = 0.dp) {
    Box(
        modifier
            .padding(start = startInset)
            .fillMaxWidth()
            .height(1.dp)
            .background(ArcaDesignSystem.colors.hairline),
    )
}

/** The one primary action of a screen or sheet: an accent pill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(ArcaDesignSystem.shapes.pill)
            .background(ArcaDesignSystem.colors.accent)
            .pressable(onClick, enabled = enabled)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ArcaDesignSystem.typography.headline, color = ArcaDesignSystem.colors.accentOn)
    }
}

/** A quiet full-width action on a sunken fill ("Archive account", "Delete transfer"). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ArcaDesignSystem.colors.ink,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(ArcaDesignSystem.shapes.medium)
            .background(ArcaDesignSystem.colors.surfaceSunken)
            .pressable(onClick, pressedScale = 0.98f)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ArcaDesignSystem.typography.headline, color = color)
    }
}

/** A labelled row in a form: "Date ………… Today, Sep 30 ›". */
@Composable
fun FormRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ArcaDesignSystem.colors.ink,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.pressable(onClick, pressedScale = 1f) else Modifier)
                .padding(vertical = ArcaDesignSystem.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = ArcaDesignSystem.typography.body, color = ArcaDesignSystem.colors.inkSecondary)
            Text(
                value,
                style = ArcaDesignSystem.typography.body,
                color = valueColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ArcaDesignSystem.spacing.space4),
            )
            if (onClick != null) {
                ArcaIcon(
                    org.nicho.arca.designsystem.ArcaIcons.chevronRight,
                    tint = ArcaDesignSystem.colors.inkTertiary,
                    size = 16.dp,
                    modifier = Modifier.padding(start = ArcaDesignSystem.spacing.space2),
                )
            }
        }
        Hairline()
    }
}

/**
 * A segmented control: one accent thumb slides between options and the labels only recolour, as
 * the prototype has it ("same control, different value"). [compact] is the small variant beside a
 * section label (Daily / Weekly).
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = ArcaDesignSystem.colors
    val motion = ArcaDesignSystem.motion
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    // Drawn, so the thumb's slide never recomposes the row.
    val thumb = animateFloatAsState(index.toFloat(), tween(motion.moveMillis, easing = motion.easeInOut), label = "thumb")
    val accent = colors.accent
    val count = options.size
    Row(
        modifier
            .clip(ArcaDesignSystem.shapes.pill)
            .background(colors.surfaceSunken)
            .padding(3.dp)
            .drawBehind {
                val gap = 2.dp.toPx()
                val slot = (size.width - gap * (count - 1)) / count
                drawRoundRect(
                    accent,
                    topLeft = Offset((slot + gap) * thumb.value, 0f),
                    size = Size(slot, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val labelColor = animateColorAsState(if (isSelected) colors.accentOn else colors.inkSecondary, tween(motion.stateMillis), label = "segLabel")
            Box(
                Modifier
                    .weight(1f)
                    .clip(ArcaDesignSystem.shapes.pill)
                    .semantics { this.selected = isSelected }
                    .pressable({ onSelect(value) }, pressedScale = 1f, role = Role.Tab)
                    .padding(vertical = if (compact) 6.dp else 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label,
                    style = ArcaDesignSystem.typography.footnote.copy(fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal),
                    color = { labelColor.value },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A text chip (filters, periods): 7 × 12dp padding, accent when on, ink-secondary on a sunken
 * fill when off. [large] is the Filter sheet's bigger chip; [dashed] its "See all" and "Range…".
 */
@Composable
fun TextChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    dashed: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = ArcaDesignSystem.colors
    val motion = ArcaDesignSystem.motion
    val fill by animateColorAsState(
        when {
            selected -> colors.accent
            dashed -> Color.Transparent
            else -> colors.surfaceSunken
        },
        tween(motion.stateMillis),
        label = "chipFill",
    )
    val ink by animateColorAsState(if (selected) colors.accentOn else colors.inkSecondary, tween(motion.stateMillis), label = "chipInk")
    Row(
        modifier
            .clip(ArcaDesignSystem.shapes.pill)
            .background(fill)
            .then(if (dashed && !selected) Modifier.dashedBorder(colors.hairline, ArcaDesignSystem.shapes.pill) else Modifier)
            .semantics { this.selected = selected }
            .pressable(onClick, pressedScale = 0.94f)
            .padding(horizontal = if (large) 14.dp else 12.dp, vertical = if (large) 9.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, style = ArcaDesignSystem.typography.footnote, color = ink, maxLines = 1)
        trailing?.invoke()
    }
}

/** A 1.5dp dashed outline: the prototype's "See all" and "Range…" chips, the capture form's "All". */
fun Modifier.dashedBorder(color: Color, shape: Shape, width: Dp = 1.5.dp): Modifier =
    drawBehind {
        val stroke = width.toPx()
        val inset = Size(size.width - stroke, size.height - stroke)
        val outline = shape.createOutline(inset, layoutDirection, this)
        translate(stroke / 2, stroke / 2) {
            drawOutline(
                outline,
                color,
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
    }

/**
 * The capture form's round chip (board 04): a 48dp well (44 for categories) with the icon, the
 * label under it, 58dp wide. The chosen one fills with the accent; [dashed] is "All".
 */
@Composable
fun WellChip(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wellSize: Dp = 48.dp,
    dashed: Boolean = false,
) {
    val colors = ArcaDesignSystem.colors
    val motion = ArcaDesignSystem.motion
    val fill by animateColorAsState(
        when {
            selected -> colors.accent
            dashed -> Color.Transparent
            else -> colors.surfaceSunken
        },
        tween(motion.stateMillis),
        label = "well",
    )
    val glyph by animateColorAsState(if (selected) colors.accentOn else colors.inkSecondary, tween(motion.stateMillis), label = "wellIcon")
    Column(
        modifier
            .width(58.dp)
            .semantics { this.selected = selected }
            .pressable(onClick, pressedScale = 0.94f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(wellSize)
                .background(fill, ArcaDesignSystem.shapes.pill)
                .then(if (dashed) Modifier.dashedBorder(colors.hairline, ArcaDesignSystem.shapes.pill) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            ArcaIcon(icon, tint = glyph)
        }
        Text(
            label,
            style = ArcaDesignSystem.typography.footnote,
            color = if (selected) colors.ink else colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
