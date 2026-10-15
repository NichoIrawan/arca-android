package org.nicho.arca.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.adjustment.BalanceAdjustmentService
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.formatting.MoneyFormat
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.parsing.CurrencyParser
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.time.PeriodClock
import org.nicho.arca.feature.reports.ArcaSheet
import org.nicho.arca.ui.components.GroupedDigits
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.PrimaryButton

data class AdjustBalanceUiState(
    val accountId: AccountId? = null,
    val accountName: String = "",
    val currentMinor: Long = 0,
    val input: String = "",
    val note: String = "",
    val targetMinor: Long? = null,
    val invalid: Boolean = false,
    val change: Pair<TransactionType, Long>? = null, // what will be recorded; null when it already matches
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
)

/**
 * "Adjust balance" (client request, 2026-10-14). The user types what the account really holds;
 * the domain works out the difference and records it as one dated adjustment row. This
 * ViewModel parses and shows; it never subtracts (DD-21).
 */
@HiltViewModel
class AdjustBalanceViewModel @Inject constructor(
    private val service: BalanceAdjustmentService,
    private val accounts: AccountReading,
    private val clock: PeriodClock,
) : ViewModel() {

    private val _state = MutableStateFlow(AdjustBalanceUiState())
    val state: StateFlow<AdjustBalanceUiState> = _state.asStateFlow()

    fun start(accountId: AccountId) {
        _state.value = AdjustBalanceUiState(accountId = accountId)
        viewModelScope.launch {
            val name = accounts.byId(accountId)?.name.orEmpty()
            val current = service.currentBalance(accountId)
            _state.update { it.copy(accountName = name, currentMinor = current) }
        }
    }

    fun onInput(text: String) {
        val parsed = if (text.isBlank()) null else (CurrencyParser.parseBalance(text) as? CurrencyParser.Result.Amount)?.minor
        _state.update {
            it.copy(
                input = text,
                targetMinor = parsed,
                invalid = text.isNotBlank() && parsed == null,
                change = parsed?.let { target -> BalanceCalculator.adjustmentTo(it.currentMinor, target) },
            )
        }
    }

    fun onNote(text: String) = _state.update { it.copy(note = text.take(200)) }

    fun onSave() {
        val s = _state.value
        val id = s.accountId ?: return
        val target = s.targetMinor ?: return
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching { service.adjust(id, target, clock.now(), s.note) }
            _state.update { it.copy(isSaving = false, isDone = true) }
        }
    }
}

/**
 * The adjust sheet: what Arca calculated, the balance the user counted, and — before anything is
 * saved — exactly what will be recorded. A balance below zero is allowed (overdrafts are real) but
 * flagged.
 */
@Composable
fun AdjustBalanceSheet(accountId: AccountId, onDismiss: () -> Unit, viewModel: AdjustBalanceViewModel = hiltViewModel()) {
    LaunchedEffect(accountId) { viewModel.start(accountId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDismiss() }
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val money = ArcaDesignSystem.money
    ArcaSheet(onDismiss) {
        Row(Modifier.padding(start = spacing.space5, end = spacing.space5 - 8.dp, top = spacing.space4), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.adjust_title), style = ArcaDesignSystem.typography.headline, color = colors.inkSecondary, modifier = Modifier.weight(1f))
            IconAction(ArcaIcons.close, stringResource(R.string.action_close), onDismiss, size = 32.dp)
        }
        Column(Modifier.padding(horizontal = spacing.space5)) {
            Text(
                stringResource(R.string.adjust_calculated, state.accountName),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = spacing.space5),
            )
            MoneyFigure(
                state.currentMinor,
                style = ArcaDesignSystem.typography.amountTitle,
                color = if (state.currentMinor < 0) colors.negative else colors.ink,
                modifier = Modifier.padding(top = spacing.space1),
            )

            Text(
                stringResource(R.string.adjust_actual).uppercase(),
                style = ArcaDesignSystem.typography.label,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = spacing.space6, bottom = spacing.space2),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(MoneyFormat.CURRENCY, style = ArcaDesignSystem.typography.currencyMark, color = colors.inkTertiary, modifier = Modifier.padding(end = 4.dp, bottom = 6.dp))
                Box(Modifier.weight(1f)) {
                    if (state.input.isEmpty()) {
                        Text(money.digits(state.currentMinor), style = ArcaDesignSystem.typography.amountTitle, color = colors.inkTertiary.copy(alpha = 0.5f))
                    }
                    BasicTextField(
                        value = state.input,
                        onValueChange = viewModel::onInput,
                        textStyle = ArcaDesignSystem.typography.amountTitle.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.ink),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        visualTransformation = GroupedDigits(money),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(Modifier.padding(top = spacing.space2).fillMaxWidth().height(2.dp).background(if (state.invalid) colors.negative else colors.accent))
            Text(
                stringResource(R.string.adjust_hint),
                style = ArcaDesignSystem.typography.footnote,
                color = if (state.invalid) colors.negative else colors.inkTertiary,
                modifier = Modifier.padding(top = spacing.space2),
            )

            // What will be recorded, in words, before it is.
            state.targetMinor?.let { target ->
                val change = state.change
                Column(
                    Modifier
                        .padding(top = spacing.space5)
                        .fillMaxWidth()
                        .background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
                        .padding(spacing.space4),
                ) {
                    Text(
                        if (change == null) {
                            stringResource(R.string.adjust_matches)
                        } else {
                            stringResource(R.string.adjust_will_record, money.transaction(change.second, change.first, withCurrency = true))
                        },
                        style = ArcaDesignSystem.typography.subhead,
                        color = colors.ink,
                    )
                    Text(
                        stringResource(R.string.adjust_explainer),
                        style = ArcaDesignSystem.typography.footnote,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (BalanceCalculator.isNegative(target)) {
                        Row(Modifier.padding(top = spacing.space3), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArcaIcon(ArcaIcons.warning, tint = colors.negative, size = 14.dp)
                            Text(stringResource(R.string.adjust_negative), style = ArcaDesignSystem.typography.footnote, color = colors.negative)
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.capture_note).uppercase(),
                style = ArcaDesignSystem.typography.label,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = spacing.space5, bottom = spacing.space2),
            )
            Box {
                if (state.note.isEmpty()) {
                    Text(stringResource(R.string.adjust_note_placeholder), style = ArcaDesignSystem.typography.body, color = colors.inkTertiary)
                }
                BasicTextField(
                    value = state.note,
                    onValueChange = viewModel::onNote,
                    textStyle = ArcaDesignSystem.typography.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.padding(top = spacing.space3).fillMaxWidth().height(1.dp).background(colors.hairline))
        }
        PrimaryButton(
            stringResource(R.string.adjust_save),
            viewModel::onSave,
            Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space5, bottom = spacing.space6),
            enabled = state.targetMinor != null && state.change != null && !state.isSaving,
        )
    }
}
