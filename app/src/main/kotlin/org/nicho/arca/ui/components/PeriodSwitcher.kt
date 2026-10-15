package org.nicho.arca.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.ui.format.monthYearLabel

/**
 * ‹ September 2026 › (FR-304), as the prototype draws it: 44dp arrows pulled 12dp into the
 * margin, the month in the middle. An arrow past [earliest] or [latest] fades to 30% and does
 * nothing. The label softens in on a change (m-swap: a 6dp rise and fade, 220ms).
 */
@Composable
fun PeriodSwitcher(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    latest: YearMonth = YearMonth.now(),
    earliest: YearMonth? = null,
    label: @Composable (YearMonth) -> String = { monthYearLabel(it) },
) {
    val canGoBack = earliest == null || month > earliest
    val canGoForward = month < latest
    val motion = ArcaDesignSystem.motion
    val rise = with(LocalDensity.current) { 6.dp.roundToPx() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconAction(
            ArcaIcons.chevronLeft,
            stringResource(R.string.action_previous_month),
            { onChange(month.minusMonths(1)) },
            size = 44.dp,
            enabled = canGoBack,
            modifier = Modifier.offset(x = (-12).dp),
        )
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                (fadeIn(tween(220, easing = motion.easeOut)) + slideInVertically(tween(220, easing = motion.easeOut)) { rise })
                    .togetherWith(fadeOut(tween(90)))
            },
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            label = "month",
        ) { shown ->
            Text(
                label(shown),
                style = ArcaDesignSystem.typography.headline,
                color = ArcaDesignSystem.colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconAction(
            ArcaIcons.chevronRight,
            stringResource(R.string.action_next_month),
            { onChange(month.plusMonths(1)) },
            size = 44.dp,
            enabled = canGoForward,
            modifier = Modifier.offset(x = 12.dp),
        )
    }
}
