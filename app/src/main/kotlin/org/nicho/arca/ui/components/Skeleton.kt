package org.nicho.arca.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import kotlin.time.Duration.Companion.milliseconds

/*
 * Skeletons (client request, 2026-10-15): a screen whose figures aren't ready within about
 * 100ms shows its shape instead of a blank page. The shape fades in only after [SKELETON_DELAY]
 * so a fast load never flashes it, then breathes softly until the data arrives.
 */

private const val SKELETON_DELAY = 90L

/** The breathing container every skeleton sits in: one layer, so the pulse costs one redraw. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(SKELETON_DELAY.milliseconds)
        appear.animateTo(1f, tween(160))
    }
    val pulse = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    val loading = stringResource(R.string.state_loading)
    Column(
        modifier
            .semantics { contentDescription = loading }
            .graphicsLayer { alpha = appear.value * pulse.value },
        content = content,
    )
}

/** One placeholder block on the sunken fill. A null [width] fills the row. */
@Composable
fun Bone(width: Dp?, height: Dp, modifier: Modifier = Modifier, shape: Shape = ArcaDesignSystem.shapes.small) {
    Box(
        modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .background(ArcaDesignSystem.colors.surfaceSunken, shape),
    )
}

/** A list row's shape: the 40dp tile, two lines, an amount. */
@Composable
fun BoneRow(modifier: Modifier = Modifier, titleWidth: Dp = 140.dp) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
    ) {
        Bone(40.dp, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Bone(titleWidth, 14.dp)
            Bone(90.dp, 10.dp)
        }
        Bone(72.dp, 14.dp)
    }
}

/** Rows under a day header: the Transactions tab and every history. */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = 7) {
    Skeleton(modifier.padding(horizontal = ArcaDesignSystem.spacing.screenMargin)) {
        Bone(110.dp, 12.dp, Modifier.padding(top = ArcaDesignSystem.spacing.space5, bottom = ArcaDesignSystem.spacing.space2))
        repeat(rows) { i -> BoneRow(titleWidth = if (i % 3 == 0) 160.dp else if (i % 3 == 1) 120.dp else 140.dp) }
    }
}

/** A hero figure: a label, the big amount, a line under it. */
@Composable
private fun HeroBones(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3)) {
        Bone(90.dp, 12.dp)
        Bone(220.dp, 40.dp)
        Bone(160.dp, 12.dp)
    }
}

/** A trend's columns, of uneven heights. */
@Composable
private fun ChartBones(modifier: Modifier = Modifier, bars: Int = 12) {
    val heights = remember(bars) { List(bars) { i -> listOf(0.45f, 0.7f, 0.35f, 0.9f, 0.6f, 0.5f, 0.8f, 0.3f)[i % 8] } }
    Row(
        modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .background(ArcaDesignSystem.colors.surfaceSunken, ArcaDesignSystem.shapes.small),
            )
        }
    }
}

/** A category or envelope line: a name, an amount, a bar, a footnote. */
@Composable
private fun BarLineBones(modifier: Modifier = Modifier, nameWidth: Dp = 120.dp) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Bone(nameWidth, 14.dp, Modifier.weight(1f, fill = false))
            Box(Modifier.weight(1f))
            Bone(70.dp, 14.dp)
        }
        Bone(null, 6.dp, shape = ArcaDesignSystem.shapes.pill)
        Bone(130.dp, 10.dp)
    }
}

/** The Overview: the dark hero card, the insight, envelopes, then recent rows. */
@Composable
fun SummarySkeleton(modifier: Modifier = Modifier) {
    val spacing = ArcaDesignSystem.spacing
    Skeleton(modifier.padding(horizontal = spacing.screenMargin)) {
        Bone(null, 250.dp, Modifier.padding(top = spacing.space5), shape = ArcaDesignSystem.shapes.large)
        Bone(null, 84.dp, Modifier.padding(top = spacing.space4), shape = ArcaDesignSystem.shapes.medium)
        Bone(80.dp, 12.dp, Modifier.padding(top = spacing.space7, bottom = spacing.space4))
        repeat(3) { BarLineBones(Modifier.padding(bottom = spacing.space5), nameWidth = (90 + it * 20).dp) }
    }
}

/** Budget: the hero, then envelope lines. */
@Composable
fun BudgetSkeleton(modifier: Modifier = Modifier) {
    val spacing = ArcaDesignSystem.spacing
    Skeleton(modifier.padding(horizontal = spacing.screenMargin)) {
        HeroBones(Modifier.padding(top = spacing.space5))
        Bone(null, 6.dp, Modifier.padding(top = spacing.space4), shape = ArcaDesignSystem.shapes.pill)
        Bone(110.dp, 12.dp, Modifier.padding(top = spacing.space7, bottom = spacing.space4))
        repeat(5) { BarLineBones(Modifier.padding(bottom = spacing.space5), nameWidth = (80 + (it % 3) * 30).dp) }
    }
}

/** Accounts: net worth, the composition bar, the favourites' tiles, then rows. */
@Composable
fun AccountsSkeleton(modifier: Modifier = Modifier) {
    val spacing = ArcaDesignSystem.spacing
    Skeleton(modifier.padding(horizontal = spacing.screenMargin)) {
        HeroBones(Modifier.padding(top = spacing.space5))
        Bone(null, 10.dp, Modifier.padding(top = spacing.space5), shape = ArcaDesignSystem.shapes.pill)
        Row(Modifier.padding(top = spacing.space6), horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            Bone(null, 96.dp, Modifier.weight(1f), shape = ArcaDesignSystem.shapes.medium)
            Bone(null, 96.dp, Modifier.weight(1f), shape = ArcaDesignSystem.shapes.medium)
        }
        Box(Modifier.padding(top = spacing.space5))
        repeat(4) { BoneRow() }
    }
}

/** Reports: the switches, the total, the trend, the categories. */
@Composable
fun ReportsSkeleton(modifier: Modifier = Modifier) {
    val spacing = ArcaDesignSystem.spacing
    Skeleton(modifier) {
        Bone(null, 36.dp, Modifier.padding(top = spacing.space4), shape = ArcaDesignSystem.shapes.pill)
        Bone(170.dp, 16.dp, Modifier.padding(top = spacing.space5).align(Alignment.CenterHorizontally))
        HeroBones(Modifier.padding(top = spacing.space6))
        ChartBones(Modifier.padding(top = spacing.space7), bars = 4)
        Bone(110.dp, 12.dp, Modifier.padding(top = spacing.space7, bottom = spacing.space5))
        repeat(4) { BarLineBones(Modifier.padding(bottom = spacing.space4), nameWidth = (80 + it * 15).dp) }
    }
}

/** A pushed detail screen: the tile and name, the hero amount, three figures, a chart, rows. */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier, chart: Boolean = true) {
    val spacing = ArcaDesignSystem.spacing
    Skeleton(modifier.padding(horizontal = spacing.screenMargin)) {
        Row(Modifier.padding(top = spacing.space5), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            Bone(44.dp, 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bone(130.dp, 14.dp)
                Bone(90.dp, 10.dp)
            }
        }
        Bone(220.dp, 40.dp, Modifier.padding(top = spacing.space5))
        Row(Modifier.padding(top = spacing.space5), horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            repeat(3) { Bone(null, 64.dp, Modifier.weight(1f), shape = ArcaDesignSystem.shapes.medium) }
        }
        if (chart) ChartBones(Modifier.padding(top = spacing.space6), bars = 14)
        Box(Modifier.padding(top = spacing.space5))
        repeat(4) { BoneRow() }
    }
}

/** A transaction's detail: the centred amount and the facts card. */
@Composable
fun TransactionDetailSkeleton(modifier: Modifier = Modifier) {
    val spacing = ArcaDesignSystem.spacing
    Skeleton(modifier.padding(horizontal = spacing.screenMargin)) {
        Column(Modifier.fillMaxWidth().padding(top = spacing.space6), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
            Bone(56.dp, 56.dp, shape = ArcaDesignSystem.shapes.pill)
            Bone(120.dp, 14.dp)
            Bone(200.dp, 40.dp)
            Bone(140.dp, 12.dp)
        }
        Bone(null, 220.dp, Modifier.padding(top = spacing.space7), shape = ArcaDesignSystem.shapes.medium)
    }
}

/** A form: label and field pairs. */
@Composable
fun FormSkeleton(modifier: Modifier = Modifier) {
    val spacing = ArcaDesignSystem.spacing
    Skeleton(modifier.padding(horizontal = spacing.screenMargin)) {
        Bone(76.dp, 76.dp, Modifier.padding(top = spacing.space5).align(Alignment.CenterHorizontally), shape = ArcaDesignSystem.shapes.large)
        repeat(4) {
            Bone(70.dp, 10.dp, Modifier.padding(top = spacing.space6, bottom = spacing.space3))
            Bone(null, 22.dp)
        }
    }
}
