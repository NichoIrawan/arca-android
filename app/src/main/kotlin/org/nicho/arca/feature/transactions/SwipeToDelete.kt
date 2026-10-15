package org.nicho.arca.feature.transactions

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.feature.reports.ArcaSheet
import org.nicho.arca.ui.components.TransactionRow
import org.nicho.arca.ui.components.pressable

/**
 * Swipe a row left to delete it (client request, 2026-10-15). The Delete panel behind the row
 * grows with the swipe and turns from the quiet sunken fill to red as it nears the line, with a
 * tick when it gets there. Let go once it's fully red and the confirmation sheet opens; let go
 * before and the row slides back as if nothing happened. Either way the row returns to rest.
 *
 * TalkBack gets the same action as a custom "Delete" on the row.
 */
@Composable
fun SwipeToDelete(onRequestDelete: () -> Unit, content: @Composable () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val motion = ArcaDesignSystem.motion
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Set synchronously while dragging and only animated by the settle, so no queued update can
    // land after the finger lifts and stop the row from returning.
    val offset = remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    var width by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableFloatStateOf(0f) } // the finger's own travel, before resistance
    var armed by remember { mutableIntStateOf(0) }       // 1 once the panel is fully red
    val request by rememberUpdatedState(onRequestDelete)
    val threshold = maxOf(width * 0.4f, with(density) { 120.dp.toPx() })
    val calm = colors.surfaceSunken
    val alarm = colors.negative
    val deleteLabel = stringResource(R.string.action_delete)

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .semantics {
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { request(); true })
            },
    ) {
        // Behind the row: the revealed strip, its color following the swipe.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val revealed = -offset.floatValue
                    if (revealed <= 0f) return@drawBehind
                    val p = (revealed / threshold).coerceIn(0f, 1f)
                    drawRect(lerp(calm, alarm, p), Offset(size.width - revealed, 0f), Size(revealed, size.height))
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(88.dp)
                    .graphicsLayer {
                        val revealed = -offset.floatValue
                        val p = (revealed / threshold).coerceIn(0f, 1f)
                        alpha = (revealed / 48.dp.toPx()).coerceIn(0f, 1f)
                        val grow = 0.85f + 0.15f * p + if (armed == 1) 0.08f else 0f
                        scaleX = grow
                        scaleY = grow
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Two copies, quiet and alarmed, crossfaded in the draw phase.
                DeleteGlyph(colors.inkSecondary, Modifier.graphicsLayer { alpha = 1f - (-offset.floatValue / threshold).coerceIn(0f, 1f) })
                DeleteGlyph(Color.White, Modifier.graphicsLayer { alpha = (-offset.floatValue / threshold).coerceIn(0f, 1f) })
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .background(colors.surface)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragged = (dragged + delta).coerceAtMost(0f)
                        val travel = -dragged
                        // Past the line the row follows the finger at a third of the pace.
                        val shown = if (travel <= threshold) travel else threshold + (travel - threshold) * 0.33f
                        val nowArmed = if (travel >= threshold) 1 else 0
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.performHapticFeedback(if (nowArmed == 1) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick)
                        }
                        offset.floatValue = -shown
                    },
                    onDragStarted = {
                        settle?.cancel()
                        dragged = offset.floatValue
                    },
                    onDragStopped = {
                        val confirm = armed == 1
                        armed = 0
                        dragged = 0f
                        if (confirm) request()
                        settle = scope.launch {
                            animate(offset.floatValue, 0f, animationSpec = tween(motion.moveMillis, easing = motion.easeOut)) { value, _ ->
                                offset.floatValue = value
                            }
                        }
                    },
                ),
        ) { content() }
    }
}

@Composable
private fun DeleteGlyph(tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ArcaIcon(ArcaIcons.delete, tint = tint, size = 20.dp)
        Text(stringResource(R.string.action_delete), style = ArcaDesignSystem.typography.footnote, color = tint)
    }
}

@Composable
fun ConfirmDeleteSheet(item: TransactionListItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val transfer = item.transaction.transferGroupId != null
    ArcaSheet(onDismiss) {
        Column(Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space5, bottom = spacing.space5)) {
            Text(
                stringResource(if (transfer) R.string.delete_confirm_transfer_title else R.string.delete_confirm_title),
                style = ArcaDesignSystem.typography.title,
                color = colors.ink,
            )
            Box(
                Modifier
                    .padding(top = spacing.space4)
                    .fillMaxWidth()
                    .background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
                    .padding(horizontal = spacing.space4),
            ) {
                TransactionRow(item, onClick = {})
            }
            Text(
                stringResource(if (transfer) R.string.delete_confirm_transfer_body else R.string.delete_confirm_body),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = spacing.space3),
            )
            Box(
                Modifier
                    .padding(top = spacing.space5)
                    .fillMaxWidth()
                    .background(colors.negative, ArcaDesignSystem.shapes.pill)
                    .pressable(onConfirm)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.action_delete), style = ArcaDesignSystem.typography.headline, color = Color.White)
            }
            Box(
                Modifier
                    .padding(top = spacing.space3)
                    .fillMaxWidth()
                    .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
                    .pressable(onDismiss)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.action_cancel), style = ArcaDesignSystem.typography.headline, color = colors.ink)
            }
        }
    }
}
