package org.nicho.arca.feature.reports

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nicho.arca.designsystem.ArcaDesignSystem

/** The change chip's small filled caret: up for more, turned over for less (board 06's nf-arrow). */
@Composable
fun ChangeCaret(up: Boolean, color: androidx.compose.ui.graphics.Color, size: Dp = 10.dp) {
    val motion = ArcaDesignSystem.motion
    val turn by androidx.compose.animation.core.animateFloatAsState(
        if (up) 0f else 180f,
        tween(motion.rollingNumberMillis, easing = motion.rollingNumberEasing),
        label = "caret",
    )
    androidx.compose.foundation.Canvas(
        Modifier
            .width(size)
            .height(size)
            .rotate(turn),
    ) {
        val s = this.size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * s, 4f * s)
            lineTo(20f * s, 14f * s)
            lineTo(4f * s, 14f * s)
            close()
        }
        drawPath(path, color)
    }
}
