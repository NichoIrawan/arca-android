package org.nicho.arca.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.ui.format.appLocale

/**
 * "Archived (2) ⌄": folded away by default; the chevron turns over as it opens (the prototype's
 * archived categories, reused for archived accounts).
 */
@Composable
fun ArchivedSection(title: String, help: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val motion = ArcaDesignSystem.motion
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(motion.moveMillis, easing = motion.easeInOut), label = "chevron")
    val state = androidx.compose.ui.res.stringResource(if (open) org.nicho.arca.R.string.state_expanded else org.nicho.arca.R.string.state_collapsed)
    Column(modifier.padding(top = spacing.space5)) {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics { stateDescription = state }
                .pressable({ open = !open }, pressedScale = 1f, onClickLabel = title)
                .padding(vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title.uppercase(appLocale()), style = ArcaDesignSystem.typography.label, color = colors.inkSecondary, modifier = Modifier.weight(1f))
            ArcaIcon(ArcaIcons.chevronDown, tint = colors.inkTertiary, size = 18.dp, modifier = Modifier.rotate(turn))
        }
        Hairline()
        AnimatedVisibility(open, enter = fadeIn(tween(220)) + expandVertically(tween(motion.moveMillis)), exit = fadeOut(tween(120)) + shrinkVertically(tween(motion.moveMillis))) {
            Column {
                content()
                Text(help, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(top = spacing.space3))
            }
        }
    }
}

