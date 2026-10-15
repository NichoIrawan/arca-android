package org.nicho.arca.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.designsystem.floatShadow
import org.nicho.arca.domain.seeding.DefaultCategoryProvider

/*
 * The editors' form kit, as the prototype's editor boards (09, 26, 27) draw it: rows on the page
 * itself, separated by rules, never boxed fields.
 */

/**
 * A labelled text entry: the label above, the value in body type, and a rule under it that
 * thickens to the accent while the field has focus.
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    prefix: String? = null,
    helper: String? = null,
    error: String? = null,
    textStyle: TextStyle = ArcaDesignSystem.typography.body,
) {
    val colors = ArcaDesignSystem.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ruleColor by animateColorAsState(
        when {
            error != null -> colors.negative
            focused -> colors.accent
            else -> colors.hairline
        },
        tween(ArcaDesignSystem.motion.stateMillis),
        label = "rule",
    )
    val ruleHeight by animateDpAsState(if (focused || error != null) 2.dp else 1.dp, tween(ArcaDesignSystem.motion.stateMillis), label = "ruleHeight")
    Column(modifier.fillMaxWidth()) {
        SectionLabel(label, Modifier.padding(top = ArcaDesignSystem.spacing.space3, bottom = ArcaDesignSystem.spacing.space2))
        Row(verticalAlignment = Alignment.Bottom) {
            if (prefix != null) {
                Text(prefix, style = ArcaDesignSystem.typography.currencyMark, color = colors.inkTertiary, modifier = Modifier.padding(end = 4.dp, bottom = 2.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                interactionSource = interaction,
                textStyle = textStyle.copy(color = if (enabled) colors.ink else colors.inkTertiary),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.inkTertiary)
                        inner()
                    }
                },
            )
        }
        Box(
            Modifier
                .padding(top = ArcaDesignSystem.spacing.space3)
                .fillMaxWidth()
                .height(ruleHeight)
                .background(ruleColor),
        )
        val message = error ?: helper
        if (message != null) {
            Text(
                message,
                style = ArcaDesignSystem.typography.footnote,
                color = if (error != null) colors.negative else colors.inkTertiary,
                modifier = Modifier.padding(top = ArcaDesignSystem.spacing.space2),
            )
        }
    }
}

/** The prototype's switch: a 46 × 28 pill, accent when on; the knob travels 18dp. */
@Composable
fun ArcaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = ArcaDesignSystem.colors
    val motion = ArcaDesignSystem.motion
    val track by animateColorAsState(if (checked) colors.accent else colors.surfaceSunken, tween(motion.stateMillis), label = "track")
    val knob by animateColorAsState(if (checked) colors.accentOn else colors.surfaceRaised, tween(motion.stateMillis), label = "knob")
    val travel by animateDpAsState(if (checked) 18.dp else 0.dp, tween(motion.moveMillis, easing = motion.easeInOut), label = "travel")
    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)
    Box(
        modifier
            .size(width = 46.dp, height = 28.dp)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) on else off
            }
            .pressable({ onCheckedChange(!checked) }, enabled = enabled, pressedScale = 0.95f, role = Role.Switch)
            .background(track, ArcaDesignSystem.shapes.pill),
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + travel, y = 3.dp)
                .size(22.dp)
                .floatShadow(ArcaDesignSystem.shadow, ArcaDesignSystem.shapes.pill)
                .background(knob, ArcaDesignSystem.shapes.pill),
        )
    }
}

/**
 * A form row with a switch: an optional leading glyph, the label, the switch, a rule under it
 * (board 09's "Favorite"); [helper] sits under the label.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    iconTint: Color = ArcaDesignSystem.colors.inkSecondary,
    divider: Boolean = true,
) {
    val colors = ArcaDesignSystem.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = ArcaDesignSystem.spacing.space4), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) ArcaIcon(icon, tint = iconTint, size = 20.dp, modifier = Modifier.padding(end = ArcaDesignSystem.spacing.space2))
            Text(label, style = ArcaDesignSystem.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
            ArcaSwitch(checked, onCheckedChange, label, enabled = enabled)
        }
        if (helper != null) {
            Text(helper, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(bottom = ArcaDesignSystem.spacing.space3))
        }
        if (divider) Hairline()
    }
}

/** "☐ Roll over leftover to next month": a 22dp box that fills with the accent and a tick. */
@Composable
fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, helper: String?, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val motion = ArcaDesignSystem.motion
    val fill by animateColorAsState(if (checked) colors.accent else Color.Transparent, tween(motion.stateMillis), label = "box")
    val ring by animateColorAsState(if (checked) colors.accent else colors.inkTertiary, tween(motion.stateMillis), label = "ring")
    val tick by animateFloatAsState(if (checked) 1f else 0.6f, tween(motion.stateMillis, easing = motion.easeOut), label = "tick")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { this.selected = checked }
                .pressable({ onCheckedChange(!checked) }, pressedScale = 1f, role = Role.Checkbox)
                .padding(vertical = ArcaDesignSystem.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(fill, RoundedCornerShape(6.dp))
                    .border(1.5.dp, ring, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ArcaIcon(ArcaIcons.check, tint = colors.accentOn, size = 15.dp, modifier = Modifier.scale(tick).alpha(if (checked) 1f else 0f))
            }
            Text(label, style = ArcaDesignSystem.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
        }
        if (helper != null) {
            Text(helper, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(start = 34.dp))
        }
    }
}

/** The icon grid: six to a row, the chosen one filled with the accent. */
@Composable
fun IconPicker(selected: String, onPick: (String) -> Unit, tint: Color, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ArcaIcons.pickable.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { name ->
                    val isSelected = name == selected
                    val fill by animateColorAsState(if (isSelected) colors.accent else colors.surfaceSunken, tween(ArcaDesignSystem.motion.stateMillis), label = "well")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics {
                                contentDescription = name
                                this.selected = isSelected
                            }
                            .pressable({ onPick(name) }, pressedScale = 0.94f)
                            .background(fill, ArcaDesignSystem.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArcaIcon(ArcaIcons.named(name), tint = if (isSelected) colors.accentOn else tint)
                    }
                }
                repeat(6 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** The five category hues as 28dp swatches; the chosen one gets a ring (DSG-ARC-001 §3.6). */
@Composable
fun ColorPicker(selectedHex: String, palette: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Row(modifier.fillMaxWidth().offset(x = (-4).dp), horizontalArrangement = Arrangement.SpaceBetween) {
        palette.forEach { hex ->
            val isSelected = hex.equals(selectedHex, ignoreCase = true)
            val name = colorName(hex)
            Box(
                Modifier
                    .size(44.dp)
                    .semantics {
                        contentDescription = name
                        this.selected = isSelected
                    }
                    .pressable({ onPick(hex) }, pressedScale = 0.9f),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Box(Modifier.size(35.dp).border(1.5.dp, colors.ink, ArcaDesignSystem.shapes.pill))
                Box(Modifier.size(28.dp).background(colors.category(hex), ArcaDesignSystem.shapes.pill))
            }
        }
    }
}

/** "Terracotta", "Blue", "Teal", "Purple", "Olive". */
@Composable
fun colorName(hex: String): String = stringResource(
    when (hex.lowercase()) {
        DefaultCategoryProvider.CLAY -> R.string.color_terracotta
        DefaultCategoryProvider.BLUE -> R.string.color_blue
        DefaultCategoryProvider.TEAL -> R.string.color_teal
        DefaultCategoryProvider.PLUM -> R.string.color_purple
        else -> R.string.color_olive
    },
)

/** A form section header: "COLOR ………… Olive". */
@Composable
fun FormSectionHeader(label: String, modifier: Modifier = Modifier, value: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(label, Modifier.weight(1f))
        if (value != null) Text(value, style = ArcaDesignSystem.typography.footnote, color = ArcaDesignSystem.colors.inkTertiary)
    }
}

/** A read-only form row with a padlock: "Opening balance ………… Rp12,000,000". */
@Composable
fun LockedRow(label: String, value: String, modifier: Modifier = Modifier, helper: String?) {
    val colors = ArcaDesignSystem.colors
    Column(modifier.fillMaxWidth().padding(vertical = ArcaDesignSystem.spacing.space4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ArcaDesignSystem.typography.body, color = colors.inkTertiary)
            ArcaIcon(ArcaIcons.lock, tint = colors.inkTertiary, size = 14.dp, modifier = Modifier.padding(start = 6.dp))
            Box(Modifier.weight(1f))
            Text(value, style = ArcaDesignSystem.typography.amountRow, color = colors.inkTertiary)
        }
        if (helper != null) Text(helper, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(top = ArcaDesignSystem.spacing.space2))
    }
    Hairline()
}
