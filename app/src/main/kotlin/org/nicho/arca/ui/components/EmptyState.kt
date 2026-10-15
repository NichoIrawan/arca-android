package org.nicho.arca.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.Mascot
import org.nicho.arca.designsystem.MascotMood
import kotlin.time.Duration.Companion.milliseconds

/**
 * A calm first-run empty state (FR-901, BEH-P2), as the prototype draws it: the mascot waving
 * once, a title, one line of what will appear here, and the one action that gets it there. A
 * first-time user and someone back after months see the same neutral screen: no streaks, no
 * guilt. Each part rises in 60ms after the last (m-rise).
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    topPadding: Dp = 175.dp,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val spacing = ArcaDesignSystem.spacing
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, top = topPadding, bottom = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(MascotMood.CALM, size = 56.dp, greet = true, modifier = Modifier.rise(0))
        Text(
            title,
            style = ArcaDesignSystem.typography.title,
            color = ArcaDesignSystem.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = spacing.space6, bottom = spacing.space3)
                .rise(1),
        )
        Text(
            body,
            style = ArcaDesignSystem.typography.body,
            color = ArcaDesignSystem.colors.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.rise(2),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = ArcaDesignSystem.typography.headline,
                color = ArcaDesignSystem.colors.accentOn,
                modifier = Modifier
                    .padding(top = spacing.space7)
                    .rise(3)
                    .background(ArcaDesignSystem.colors.accent, ArcaDesignSystem.shapes.pill)
                    .pressable(onAction)
                    .padding(horizontal = 28.dp, vertical = 15.dp),
            )
        }
        if (extra != null) {
            Column(Modifier.padding(top = spacing.space7).rise(4), horizontalAlignment = Alignment.CenterHorizontally, content = extra)
        }
    }
}

/** First-run stagger: a 10dp rise and fade, 360ms, each step 60ms after the last (m-rise). */
@Composable
fun Modifier.rise(index: Int): Modifier {
    val motion = ArcaDesignSystem.motion
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 60L).milliseconds)
        p.animateTo(1f, tween(360, easing = motion.easeOut))
    }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 10.dp.toPx()
    }
}
