package org.nicho.arca.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Corner radii (DSG-ARC-001 §6.1).
 * - small (10dp): chips, small controls, every row's icon tile
 * - medium (16dp): the few raised or sunken blocks, e.g. stat blocks
 * - large (28dp): sheet tops
 * - pill: the FAB, account chips, ranked bars
 */
@Immutable
data class ArcaShapes(
    val radiusSm: Dp,
    val radiusMd: Dp,
    val radiusLg: Dp,
) {
    val small: RoundedCornerShape get() = RoundedCornerShape(radiusSm)
    val medium: RoundedCornerShape get() = RoundedCornerShape(radiusMd)
    val large: RoundedCornerShape get() = RoundedCornerShape(radiusLg)
    val sheetTop: RoundedCornerShape get() = RoundedCornerShape(topStart = radiusLg, topEnd = radiusLg)
    val pill: RoundedCornerShape get() = CircleShape
}

val ArcaShapeTokens = ArcaShapes(
    radiusSm = 10.dp,
    radiusMd = 16.dp,
    radiusLg = 28.dp,
)

/**
 * The one shadow, `shadow-float` (DSG-ARC-001 §6.2). It belongs to exactly two components: the
 * capture FAB and the undo banner. Nothing else casts a shadow; stat blocks are sunken, not
 * raised. Theme-dependent, because a light-theme shadow vanishes on a dark page.
 */
@Immutable
data class ArcaShadow(
    val blur: Dp,
    val offsetY: Dp,
    val color: Color,
)

val ArcaLightShadow = ArcaShadow(blur = 12.dp, offsetY = 2.dp, color = Color(0x1A16140F)) // rgba(22,20,15,0.10)
val ArcaDarkShadow = ArcaShadow(blur = 16.dp, offsetY = 2.dp, color = Color(0x99000000))  // rgba(0,0,0,0.6)

/** Draws `shadow-float` behind [shape]. Only for the FAB and the undo banner. */
fun Modifier.floatShadow(shadow: ArcaShadow, shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = shadow.blur, color = shadow.color, offset = DpOffset(0.dp, shadow.offsetY)))
