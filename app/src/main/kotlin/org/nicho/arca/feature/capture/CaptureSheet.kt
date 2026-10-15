package org.nicho.arca.feature.capture

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.formatting.NumberStyle
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.feature.accounts.AccountTile
import org.nicho.arca.feature.reports.ArcaSheet
import org.nicho.arca.ui.components.DatePickerSheet
import org.nicho.arca.ui.components.FormRow
import org.nicho.arca.ui.components.GroupedDigits
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.SheetTitleBar
import org.nicho.arca.ui.components.PrimaryButton
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.TextChip
import org.nicho.arca.ui.components.WellChip
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.format.captureDate

/**
 * The capture sheet (DD-A12, board 04): one composable, hosted by the main shell (+) and by
 * CaptureActivity (widget, shortcut, tile, deep link). Tap budget: + → amount → favourite
 * account → category → Save, four taps (NFR-U03).
 */
@Composable
fun CaptureSheet(
    request: CaptureRequest,
    viewModel: CaptureViewModel,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(request.token) { viewModel.start(request) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read the live value: the composed one can still be the previous sheet's "done".
    LaunchedEffect(state.isDone) { if (viewModel.state.value.isDone) onDismiss() }

    ArcaSheet(onDismiss) {
        CaptureForm(
            state = state,
            onClose = onDismiss,
            onModeChange = viewModel::onModeChange,
            onAmountChange = viewModel::onAmountChange,
            onAppendSuffix = viewModel::onAppendSuffix,
            onAccountChange = viewModel::onAccountChange,
            onDestinationChange = viewModel::onDestinationChange,
            onCategoryChange = viewModel::onCategoryChange,
            onFeeChange = viewModel::onFeeChange,
            onDateChange = viewModel::onDateChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::onSave,
        )
    }
}

@Composable
fun CaptureForm(
    state: CaptureUiState,
    onClose: () -> Unit,
    onModeChange: (CaptureMode) -> Unit,
    onAmountChange: (String) -> Unit,
    onAppendSuffix: (String) -> Unit,
    onAccountChange: (AccountId) -> Unit,
    onDestinationChange: (AccountId) -> Unit,
    onCategoryChange: (org.nicho.arca.domain.model.CategoryId) -> Unit,
    onFeeChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    var picking by remember { mutableStateOf<AccountSlot?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    Column(Modifier.imePadding()) {
        SheetTitleBar(stringResource(if (state.isEditing) R.string.capture_title_edit else R.string.capture_title_new), onClose)

        if (!state.isEditing || !state.isTransfer) {
            SegmentedControl(
                options = buildList {
                    add(CaptureMode.EXPENSE to stringResource(R.string.capture_mode_expense))
                    add(CaptureMode.INCOME to stringResource(R.string.capture_mode_income))
                    if (!state.isEditing) add(CaptureMode.TRANSFER to stringResource(R.string.capture_mode_transfer))
                },
                selected = state.mode,
                onSelect = onModeChange,
                modifier = Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space5),
            )
        }

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.space5)
                .padding(bottom = spacing.space6),
        ) {
            AmountField(
                text = state.amountText,
                amountMinor = state.amountMinor,
                onChange = onAmountChange,
                onAppendSuffix = onAppendSuffix,
                autoFocus = !state.isEditing,
                modifier = Modifier.padding(top = spacing.space7),
            )
            Issues(state.issues, AMOUNT_ISSUES)

            if (state.isTransfer) {
                Column(Modifier.padding(top = spacing.space7)) {
                    AccountField(stringResource(R.string.capture_from), state.account(state.accountId)) { picking = AccountSlot.SOURCE }
                    BelowZeroWarning(state)
                    AccountField(stringResource(R.string.capture_to), state.account(state.destinationId), Modifier.padding(top = spacing.space5)) { picking = AccountSlot.DESTINATION }
                    Issues(state.issues, setOf(CaptureIssue.SAME_ACCOUNT, CaptureIssue.DESTINATION_REQUIRED, CaptureIssue.ACCOUNT_ARCHIVED))
                    FeeField(state.feeText, onFeeChange, Modifier.padding(top = spacing.space5))
                    Issues(state.issues, setOf(CaptureIssue.FEE_INVALID))
                }
            } else {
                SectionLabel(stringResource(R.string.capture_account), Modifier.padding(top = spacing.space7, bottom = spacing.space3))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.space4)) {
                    items(state.accounts, key = { it.id.value }) { account ->
                        WellChip(
                            label = account.name,
                            icon = ArcaIcons.named(account.icon),
                            selected = account.id == state.accountId,
                            onClick = { onAccountChange(account.id) },
                        )
                    }
                    item {
                        WellChip(
                            label = stringResource(R.string.capture_all),
                            icon = ArcaIcons.more,
                            selected = false,
                            onClick = { picking = AccountSlot.SOURCE },
                            dashed = true,
                        )
                    }
                }
                Issues(state.issues, setOf(CaptureIssue.ACCOUNT_REQUIRED, CaptureIssue.ACCOUNT_ARCHIVED))
                BelowZeroWarning(state)

                SectionLabel(stringResource(R.string.capture_category), Modifier.padding(top = spacing.space6, bottom = spacing.space3))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.categories, key = { it.id.value }) { category ->
                        WellChip(
                            label = category.name,
                            icon = ArcaIcons.named(category.icon),
                            selected = category.id == state.categoryId,
                            onClick = { onCategoryChange(category.id) },
                            wellSize = 44.dp,
                        )
                    }
                }
                Issues(state.issues, setOf(CaptureIssue.CATEGORY_REQUIRED, CaptureIssue.CATEGORY_MISMATCH))
            }

            Column(Modifier.padding(top = spacing.space6)) {
                FormRow(stringResource(R.string.capture_date), captureDate(state.date), onClick = { pickingDate = true })
                Issues(state.issues, setOf(CaptureIssue.FUTURE_DATE))
                InlineField(
                    label = stringResource(R.string.capture_note),
                    value = state.note,
                    placeholder = stringResource(R.string.capture_optional),
                    onChange = onNoteChange,
                    keyboardType = KeyboardType.Text,
                )
                Issues(state.issues, setOf(CaptureIssue.NOTE_TOO_LONG, CaptureIssue.SAVE_FAILED))
            }
        }

        Box(Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space4, bottom = spacing.space6)) {
            PrimaryButton(stringResource(R.string.action_save), onSave, enabled = !state.isSaving)
        }
    }

    picking?.let { slot ->
        AccountPickerSheet(
            accounts = state.accounts,
            selected = if (slot == AccountSlot.SOURCE) state.accountId else state.destinationId,
            onPick = {
                if (slot == AccountSlot.SOURCE) onAccountChange(it) else onDestinationChange(it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }

    if (pickingDate) {
        DatePickerSheet(
            initial = state.date,
            today = LocalDate.now(),
            onPick = {
                onDateChange(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/**
 * "GoPay will be −Rp120,000 after this": shown before saving when the paying account would end up
 * below zero (client decision, 2026-10-14). A warning, not a block: overdrafts are real (FR-132).
 */
@Composable
private fun BelowZeroWarning(state: CaptureUiState) {
    val after = state.balanceAfterIfNegative ?: return
    val name = state.account(state.accountId)?.name.orEmpty()
    val colors = ArcaDesignSystem.colors
    Row(
        Modifier
            .padding(top = ArcaDesignSystem.spacing.space3)
            .fillMaxWidth()
            .background(colors.negative.copy(alpha = 0.10f), ArcaDesignSystem.shapes.small)
            .padding(horizontal = ArcaDesignSystem.spacing.space3, vertical = ArcaDesignSystem.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space2),
    ) {
        ArcaIcon(ArcaIcons.warning, tint = colors.negative, size = 16.dp)
        Text(
            stringResource(R.string.capture_below_zero, name, ArcaDesignSystem.money.amount(after)),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.negative,
        )
    }
}

/** "FROM / BCA Utama ›": a labelled account row that opens the account list. */
@Composable
private fun AccountField(label: String, account: Account?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    Column(modifier) {
        SectionLabel(label, Modifier.padding(bottom = ArcaDesignSystem.spacing.space2))
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onClick, pressedScale = 1f)
                .padding(vertical = ArcaDesignSystem.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
        ) {
            ArcaIcon(ArcaIcons.named(account?.icon ?: "wallet"), tint = colors.inkSecondary)
            Text(
                account?.name ?: stringResource(R.string.capture_choose_account),
                style = ArcaDesignSystem.typography.headline,
                color = if (account == null) colors.inkTertiary else colors.ink,
                modifier = Modifier.weight(1f),
            )
            ArcaIcon(ArcaIcons.chevronRight, tint = colors.inkTertiary, size = 16.dp)
        }
        Hairline()
    }
}

/** "FEE (OPTIONAL) / Rp 2,500": a transfer's fee, typed like any amount. */
@Composable
private fun FeeField(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Column(modifier) {
        SectionLabel(stringResource(R.string.capture_fee_optional), Modifier.padding(bottom = ArcaDesignSystem.spacing.space2))
        Row(Modifier.padding(vertical = ArcaDesignSystem.spacing.space3), verticalAlignment = Alignment.Bottom) {
            Text("Rp", style = ArcaDesignSystem.typography.currencyMark, color = colors.inkTertiary, modifier = Modifier.padding(end = 4.dp, bottom = 1.dp))
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = ArcaDesignSystem.typography.amountRow.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = GroupedDigits(ArcaDesignSystem.money),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("0", style = ArcaDesignSystem.typography.amountRow, color = colors.inkTertiary)
                        inner()
                    }
                },
            )
        }
        Hairline()
    }
}

private enum class AccountSlot { SOURCE, DESTINATION }

/**
 * The amount, set as large as the net-position figure, with an accent underline. A number pad
 * for speed, plus suffix chips, because a number pad can't type "rb" or "K" (SDD-ARC-002 §9.6).
 */
@Composable
private fun AmountField(
    text: String,
    amountMinor: Long?,
    onChange: (String) -> Unit,
    onAppendSuffix: (String) -> Unit,
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Rp",
                style = ArcaDesignSystem.typography.currencyMark,
                color = colors.inkTertiary,
                modifier = Modifier.padding(end = 4.dp, bottom = 8.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = onChange,
                textStyle = ArcaDesignSystem.typography.amountHero.copy(color = colors.ink),
                singleLine = true,
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = GroupedDigits(money),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text("0", style = ArcaDesignSystem.typography.amountHero, color = colors.hairline)
                    }
                    inner()
                },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.accent),
        )
        val hint = if (amountMinor != null) "= " + money.amount(amountMinor) else stringResource(R.string.capture_amount_hint)
        Text(
            hint,
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = ArcaDesignSystem.spacing.space3),
        )
        val suffixes = if (money.style == NumberStyle.INDONESIAN) listOf("000", "rb", "jt") else listOf("000", "K")
        Row(
            Modifier.padding(top = ArcaDesignSystem.spacing.space3),
            horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space2),
        ) {
            suffixes.forEach { suffix -> TextChip(suffix, selected = false, onClick = { onAppendSuffix(suffix.lowercase()) }) }
        }
    }
}

/** "Note ………… Optional": an inline text field in a form row. */
@Composable
private fun InlineField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    val colors = ArcaDesignSystem.colors
    Column {
        Row(Modifier.padding(vertical = ArcaDesignSystem.spacing.space3), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ArcaDesignSystem.typography.body, color = colors.inkSecondary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = ArcaDesignSystem.typography.body.copy(color = colors.ink, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ArcaDesignSystem.spacing.space4),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty()) Text(placeholder, style = ArcaDesignSystem.typography.body, color = colors.inkTertiary)
                        inner()
                    }
                },
            )
        }
        Hairline()
    }
}

/** Messages for whichever of [shown] are present: the problem and the fix, never blame. */
@Composable
private fun Issues(issues: Set<CaptureIssue>, shown: Set<CaptureIssue>) {
    issues.filter { it in shown }.forEach { issue ->
        Text(
            stringResource(issueMessage(issue)),
            style = ArcaDesignSystem.typography.footnote,
            color = ArcaDesignSystem.colors.negative,
            modifier = Modifier.padding(top = ArcaDesignSystem.spacing.space2),
        )
    }
}

@StringRes
private fun issueMessage(issue: CaptureIssue): Int = when (issue) {
    CaptureIssue.AMOUNT_EMPTY, CaptureIssue.AMOUNT_NOT_POSITIVE -> R.string.issue_amount_positive
    CaptureIssue.AMOUNT_UNCLEAR -> R.string.issue_amount_unclear
    CaptureIssue.AMOUNT_TOO_LARGE -> R.string.issue_amount_too_large
    CaptureIssue.ACCOUNT_REQUIRED -> R.string.issue_account_required
    CaptureIssue.ACCOUNT_ARCHIVED -> R.string.issue_account_archived
    CaptureIssue.CATEGORY_REQUIRED -> R.string.issue_category_required
    CaptureIssue.CATEGORY_MISMATCH -> R.string.issue_category_mismatch
    CaptureIssue.FUTURE_DATE -> R.string.issue_future_date
    CaptureIssue.NOTE_TOO_LONG -> R.string.issue_note_too_long
    CaptureIssue.SAME_ACCOUNT -> R.string.issue_same_account
    CaptureIssue.DESTINATION_REQUIRED -> R.string.issue_destination_required
    CaptureIssue.FEE_INVALID -> R.string.issue_fee_invalid
    CaptureIssue.SAVE_FAILED -> R.string.issue_save_failed
}

private val AMOUNT_ISSUES = setOf(
    CaptureIssue.AMOUNT_EMPTY,
    CaptureIssue.AMOUNT_UNCLEAR,
    CaptureIssue.AMOUNT_NOT_POSITIVE,
    CaptureIssue.AMOUNT_TOO_LARGE,
)

/** A sheet listing every active account (FR-206): favourites first, for transfers and "All". */
@Composable
fun AccountPickerSheet(
    accounts: List<Account>,
    selected: AccountId?,
    onPick: (AccountId) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ArcaDesignSystem.colors
    ArcaSheet(onDismiss) {
        SheetTitleBar(stringResource(R.string.capture_choose_account), onDismiss)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = ArcaDesignSystem.spacing.space5, end = ArcaDesignSystem.spacing.space5, top = ArcaDesignSystem.spacing.space4, bottom = ArcaDesignSystem.spacing.space6),
        ) {
            accounts.forEachIndexed { i, account ->
                if (i > 0) Hairline(startInset = 52.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable({ onPick(account.id) }, pressedScale = 1f)
                        .padding(vertical = ArcaDesignSystem.spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
                ) {
                    AccountTile(account)
                    Text(account.name, style = ArcaDesignSystem.typography.headline, color = colors.ink, modifier = Modifier.weight(1f))
                    if (account.id == selected) ArcaIcon(ArcaIcons.check, tint = colors.ink)
                }
            }
        }
    }
}
