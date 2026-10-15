package org.nicho.arca.feature.accounts

import org.nicho.arca.ui.components.FormSkeleton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.parsing.CurrencyParser
import org.nicho.arca.feature.reports.ArcaSheet
import org.nicho.arca.feature.reports.RadioRow
import org.nicho.arca.ui.components.DetailTopBar
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconPicker
import org.nicho.arca.ui.components.LabeledField
import org.nicho.arca.ui.components.LockedRow
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SheetTitleBar
import org.nicho.arca.ui.components.SwitchRow
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.pushArrival

@Composable
fun AccountEditorRoute(onDone: () -> Unit, viewModel: AccountEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    AccountEditorScreen(
        state = state,
        accountId = viewModel.accountId,
        onBack = onDone,
        onSave = viewModel::onSave,
        onNameChange = viewModel::onNameChange,
        onTypeChange = viewModel::onTypeChange,
        onIconChange = viewModel::onIconChange,
        onOpeningChange = viewModel::onOpeningChange,
        onFavoriteChange = viewModel::onFavoriteChange,
        onArchiveToggle = viewModel::onArchiveToggle,
        onDelete = viewModel::onDelete,
    )
}

/**
 * New / Edit Account (board 09): the current balance — calculated, never typed, but adjustable
 * (client request, 2026-10-14) — then name, type, opening balance (locked once there's history),
 * icon and favourite as rows, and Archive at the foot.
 */
@Composable
fun AccountEditorScreen(
    state: AccountEditorUiState,
    accountId: AccountId?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onIconChange: (String) -> Unit,
    onOpeningChange: (String) -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    var pickingType by rememberSaveable { mutableStateOf(false) }
    var pickingIcon by rememberSaveable { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailTopBar(onBack, actionLabel = stringResource(R.string.action_save), onAction = onSave, actionEnabled = !state.isLoading)
        if (state.isLoading) {
            FormSkeleton()
            return@Column
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .pushArrival()
                .padding(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.space5)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(if (state.isNew) R.string.accounts_new_title else R.string.accounts_edit_title),
                style = ArcaDesignSystem.typography.title,
                color = colors.ink,
                modifier = Modifier.padding(top = spacing.space6),
            )

            state.currentBalanceMinor?.let { balance ->
                Column(
                    Modifier
                        .padding(top = spacing.space6)
                        .fillMaxWidth()
                        .background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
                        .padding(spacing.space4),
                ) {
                    Row(Modifier.padding(bottom = spacing.space2), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(stringResource(R.string.accounts_current_balance), Modifier.weight(1f))
                        Text(
                            stringResource(R.string.adjust_action),
                            style = ArcaDesignSystem.typography.footnote.copy(fontWeight = FontWeight.Medium),
                            color = colors.ink,
                            modifier = Modifier.pressable({ adjusting = true }),
                        )
                    }
                    MoneyFigure(
                        balance,
                        style = ArcaDesignSystem.typography.amountTitle,
                        color = if (balance < 0) colors.negative else colors.ink,
                        rolling = true,
                    )
                    if (balance < 0) {
                        Row(Modifier.padding(top = spacing.space2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ArcaIcon(ArcaIcons.warning, tint = colors.negative, size = 14.dp)
                            Text(stringResource(R.string.accounts_negative_this), style = ArcaDesignSystem.typography.footnote, color = colors.negative)
                        }
                    }
                    Text(
                        stringResource(R.string.accounts_current_balance_help),
                        style = ArcaDesignSystem.typography.footnote,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = spacing.space2),
                    )
                }
            }

            Column(Modifier.padding(top = spacing.space6)) {
                LabeledField(
                    label = stringResource(R.string.accounts_name),
                    value = state.name,
                    onChange = onNameChange,
                    placeholder = stringResource(R.string.accounts_name_placeholder),
                    error = when {
                        AccountEditorIssue.NAME_REQUIRED in state.issues -> stringResource(R.string.issue_name_required)
                        AccountEditorIssue.NAME_TOO_LONG in state.issues -> stringResource(R.string.issue_account_name_too_long)
                        else -> null
                    },
                )
                ValueRow(stringResource(R.string.accounts_type), accountTypeLabel(state.type)) { pickingType = true }
                if (state.openingLocked) {
                    LockedRow(
                        stringResource(R.string.accounts_opening_label_short),
                        ArcaDesignSystem.money.amount(CurrencyParser.parseBalance(state.openingText).let { (it as? CurrencyParser.Result.Amount)?.minor ?: 0L }),
                        helper = stringResource(R.string.accounts_opening_locked),
                    )
                } else {
                    LabeledField(
                        label = stringResource(R.string.accounts_opening_balance_label),
                        value = state.openingText,
                        onChange = onOpeningChange,
                        keyboardType = KeyboardType.Text,
                        prefix = "Rp",
                        textStyle = ArcaDesignSystem.typography.amountRow,
                        helper = stringResource(R.string.accounts_opening_help),
                        error = if (AccountEditorIssue.OPENING_UNCLEAR in state.issues) stringResource(R.string.issue_amount_unclear) else null,
                    )
                    // A negative opening balance is allowed (an overdraft is real) but flagged.
                    val opening = (CurrencyParser.parseBalance(state.openingText) as? CurrencyParser.Result.Amount)?.minor
                    if (opening != null && opening < 0) {
                        Row(Modifier.padding(top = spacing.space2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ArcaIcon(ArcaIcons.warning, tint = colors.negative, size = 14.dp)
                            Text(stringResource(R.string.accounts_opening_negative), style = ArcaDesignSystem.typography.footnote, color = colors.negative)
                        }
                    }
                }
                IconRow(state.icon, open = pickingIcon) { pickingIcon = !pickingIcon }
                AnimatedVisibility(pickingIcon, enter = fadeIn(tween(200)) + expandVertically(), exit = fadeOut(tween(120)) + shrinkVertically()) {
                    Column {
                        IconPicker(state.icon, { onIconChange(it); pickingIcon = false }, tint = colors.inkSecondary, modifier = Modifier.padding(vertical = spacing.space3))
                        Hairline()
                    }
                }
                SwitchRow(
                    stringResource(R.string.accounts_favorite),
                    state.isFavorite,
                    onFavoriteChange,
                    icon = ArcaIcons.star,
                    iconTint = colors.brand,
                )
                Text(
                    stringResource(R.string.accounts_favorite_help),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = spacing.space2),
                )
            }
            if (AccountEditorIssue.SAVE_FAILED in state.issues) {
                Text(stringResource(R.string.issue_save_failed), style = ArcaDesignSystem.typography.footnote, color = colors.negative, modifier = Modifier.padding(top = spacing.space3))
            }
            if (!state.isNew) {
                // Archive, never delete — unless there's no history to lose (FR-203).
                Column(Modifier.padding(top = spacing.space7)) {
                    Text(
                        stringResource(if (state.isArchived) R.string.accounts_unarchive_this else R.string.accounts_archive_this),
                        style = ArcaDesignSystem.typography.body,
                        color = colors.ink,
                        modifier = Modifier.pressable(onArchiveToggle),
                    )
                    Text(
                        stringResource(R.string.accounts_archive_help),
                        style = ArcaDesignSystem.typography.footnote,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = spacing.space2),
                    )
                    if (state.canDelete) {
                        Text(
                            stringResource(R.string.accounts_delete_this),
                            style = ArcaDesignSystem.typography.body,
                            color = colors.negative,
                            modifier = Modifier
                                .padding(top = spacing.space5)
                                .pressable(onDelete),
                        )
                    }
                }
            }
        }
    }
    if (pickingType) {
        ArcaSheet({ pickingType = false }) {
            SheetTitleBar(stringResource(R.string.accounts_type_pick), { pickingType = false })
            Column(Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space4, bottom = spacing.space6)) {
                AccountType.entries.forEachIndexed { i, type ->
                    if (i > 0) Hairline(startInset = 34.dp)
                    RadioRow(accountTypeLabel(type), null, selected = state.type == type, onClick = { onTypeChange(type); pickingType = false })
                }
            }
        }
    }
    if (adjusting && accountId != null) {
        AdjustBalanceSheet(accountId, onDismiss = { adjusting = false })
    }
}

/** "Type ………… Bank ›": a row that opens a choice. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .padding(vertical = ArcaDesignSystem.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ArcaDesignSystem.typography.body, color = colors.inkSecondary, modifier = Modifier.weight(1f))
        Text(value, style = ArcaDesignSystem.typography.body, color = colors.ink)
        ArcaIcon(ArcaIcons.chevronRight, tint = colors.inkTertiary, size = 16.dp, modifier = Modifier.padding(start = ArcaDesignSystem.spacing.space2))
    }
    Hairline()
}

/** "Icon ………… glyph ›": opens the icon grid in place. */
@Composable
private fun IconRow(icon: String, open: Boolean, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val turn by animateFloatAsState(if (open) 90f else 0f, tween(ArcaDesignSystem.motion.stateMillis), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .padding(vertical = ArcaDesignSystem.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space2),
    ) {
        Text(stringResource(R.string.editor_icon), style = ArcaDesignSystem.typography.body, color = colors.inkSecondary, modifier = Modifier.weight(1f))
        ArcaIcon(ArcaIcons.named(icon), tint = colors.inkSecondary)
        ArcaIcon(ArcaIcons.chevronRight, tint = colors.inkTertiary, size = 16.dp, modifier = Modifier.rotate(turn))
    }
    if (!open) Hairline()
}
