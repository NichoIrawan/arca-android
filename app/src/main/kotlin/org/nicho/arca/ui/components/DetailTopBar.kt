package org.nicho.arca.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcons

/**
 * A pushed screen's header (the prototype's detail boards): Back on the left, pulled 8dp into
 * the margin, and either a text action ("Edit", "Save") or an icon on the right. It never animates,
 * so Back is there the instant the screen is.
 */
@Composable
fun DetailTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    @DrawableRes actionIcon: Int? = null,
    actionIconDescription: String? = null,
) {
    val spacing = ArcaDesignSystem.spacing
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = spacing.screenMargin - 8.dp, end = spacing.screenMargin, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(ArcaIcons.back, stringResource(R.string.action_back), onBack)
        Spacer(Modifier.weight(1f))
        when {
            actionIcon != null && onAction != null ->
                IconAction(actionIcon, actionIconDescription.orEmpty(), onAction, enabled = actionEnabled, modifier = Modifier.offset(x = 8.dp))
            actionLabel != null && onAction != null -> Text(
                actionLabel,
                style = ArcaDesignSystem.typography.headline,
                color = if (actionEnabled) ArcaDesignSystem.colors.ink else ArcaDesignSystem.colors.inkTertiary,
                modifier = Modifier.pressable(onAction, enabled = actionEnabled),
            )
        }
    }
}
