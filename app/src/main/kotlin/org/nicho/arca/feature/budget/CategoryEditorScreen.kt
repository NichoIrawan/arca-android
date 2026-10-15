package org.nicho.arca.feature.budget

import org.nicho.arca.ui.components.FormSkeleton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.budget.BudgetStep
import org.nicho.arca.domain.formatting.MoneyFormat
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.parsing.CurrencyParser
import org.nicho.arca.domain.seeding.DefaultCategoryProvider
import org.nicho.arca.ui.components.CheckRow
import org.nicho.arca.ui.components.ColorPicker
import org.nicho.arca.ui.components.DetailTopBar
import org.nicho.arca.ui.components.FormSectionHeader
import org.nicho.arca.ui.components.GroupedDigits
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.IconPicker
import org.nicho.arca.ui.components.LabeledField
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.colorName
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.pushArrival
import org.nicho.arca.ui.format.shortDate

@Composable
fun CategoryEditorRoute(onDone: () -> Unit, viewModel: CategoryEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    CategoryEditorScreen(state, onDone, viewModel)
}

/**
 * New / Edit Category (boards 26, 27): a live preview of the tile, then name, the monthly budget
 * (a − / + stepper in Rp50,000 steps, or none), roll-over, colour and icon; the type is locked
 * once the category exists.
 */
@Composable
private fun CategoryEditorScreen(state: CategoryEditorUiState, onBack: () -> Unit, vm: CategoryEditorViewModel) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    val hue = if (state.kind == CategoryKind.INCOME) colors.income else colors.category(state.colorHex)
    val budget = CurrencyParser.parseOrNull(state.budgetText)
    val kindLabel = stringResource(if (state.kind == CategoryKind.INCOME) R.string.capture_mode_income else R.string.capture_mode_expense)
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailTopBar(onBack, actionLabel = stringResource(R.string.action_save), onAction = vm::onSave, actionEnabled = !state.isLoading)
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
                stringResource(if (state.isNew) R.string.category_new_title else R.string.category_edit_title),
                style = ArcaDesignSystem.typography.title,
                color = colors.ink,
                modifier = Modifier.padding(top = spacing.space5),
            )
            if (state.isNew) {
                SegmentedControl(
                    options = listOf(
                        CategoryKind.EXPENSE to stringResource(R.string.capture_mode_expense),
                        CategoryKind.INCOME to stringResource(R.string.capture_mode_income),
                    ),
                    selected = state.kind,
                    onSelect = vm::onKindChange,
                    modifier = Modifier.padding(top = spacing.space5),
                )
            }

            // Live preview: the tile as the lists will draw it.
            Column(Modifier.fillMaxWidth().padding(top = spacing.space6), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(76.dp).background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium), contentAlignment = Alignment.Center) {
                    ArcaIcon(ArcaIcons.named(state.icon), tint = hue, size = 30.dp)
                }
                Text(
                    state.name.ifBlank { stringResource(R.string.category_preview_name) },
                    style = ArcaDesignSystem.typography.headline,
                    color = if (state.name.isBlank()) colors.inkTertiary else colors.ink,
                    modifier = Modifier.padding(top = spacing.space3),
                )
                Text(
                    if (state.canBudget && budget != null) {
                        stringResource(R.string.category_preview_budget, kindLabel, money.amount(budget))
                    } else {
                        stringResource(R.string.category_preview_no_budget, kindLabel)
                    },
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(Modifier.padding(top = spacing.space6)) {
                LabeledField(
                    label = stringResource(R.string.accounts_name),
                    value = state.name,
                    onChange = vm::onNameChange,
                    placeholder = stringResource(R.string.category_name_placeholder),
                    error = when {
                        CategoryEditorIssue.NAME_REQUIRED in state.issues -> stringResource(R.string.issue_name_required)
                        CategoryEditorIssue.NAME_TOO_LONG in state.issues -> stringResource(R.string.issue_category_name_too_long)
                        else -> null
                    },
                )

                when {
                    state.canBudget -> {
                        BudgetSection(state, budget, vm)
                    }
                    state.kind == CategoryKind.INCOME -> {
                        Column(Modifier.padding(vertical = spacing.space4)) {
                            FormSectionHeader(stringResource(R.string.category_monthly_budget), modifier = Modifier.padding(bottom = spacing.space2))
                            Text(stringResource(R.string.category_income_no_budget), style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
                        }
                        Hairline()
                    }
                }

                if (state.kind == CategoryKind.EXPENSE) {
                    Column(Modifier.padding(top = spacing.space4, bottom = spacing.space3)) {
                        FormSectionHeader(stringResource(R.string.editor_color), value = colorName(state.colorHex), modifier = Modifier.padding(bottom = spacing.space2))
                        ColorPicker(state.colorHex, DefaultCategoryProvider.palette, vm::onColorChange)
                    }
                    Hairline()
                }
                Column(Modifier.padding(vertical = spacing.space4)) {
                    FormSectionHeader(stringResource(R.string.editor_icon), modifier = Modifier.padding(bottom = spacing.space3))
                    IconPicker(state.icon, vm::onIconChange, tint = colors.inkSecondary)
                }
                Hairline()

                if (!state.isNew) {
                    Column(Modifier.padding(vertical = spacing.space4)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.accounts_type), style = ArcaDesignSystem.typography.body, color = colors.inkTertiary)
                            ArcaIcon(ArcaIcons.lock, tint = colors.inkTertiary, size = 14.dp, modifier = Modifier.padding(start = 6.dp))
                            Box(Modifier.weight(1f))
                            Text(kindLabel, style = ArcaDesignSystem.typography.body, color = colors.inkTertiary)
                        }
                        Text(
                            stringResource(R.string.category_type_locked_use),
                            style = ArcaDesignSystem.typography.footnote,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = spacing.space2),
                        )
                    }
                    Hairline()
                }
            }

            if (CategoryEditorIssue.SAVE_FAILED in state.issues) {
                Text(stringResource(R.string.issue_save_failed), style = ArcaDesignSystem.typography.footnote, color = colors.negative, modifier = Modifier.padding(top = spacing.space3))
            }
            if (!state.isNew) {
                // Archive, never delete — unless there's nothing to lose.
                Column(Modifier.padding(top = spacing.space7)) {
                    Text(
                        stringResource(if (state.isArchived) R.string.category_unarchive else R.string.category_archive_this),
                        style = ArcaDesignSystem.typography.body,
                        color = colors.ink,
                        modifier = Modifier.pressable(vm::onArchiveToggle),
                    )
                    Text(
                        stringResource(if (CategoryEditorIssue.IN_USE in state.issues) R.string.category_in_use else R.string.category_archive_help),
                        style = ArcaDesignSystem.typography.footnote,
                        color = if (CategoryEditorIssue.IN_USE in state.issues) colors.ink else colors.inkTertiary,
                        modifier = Modifier.padding(top = spacing.space2),
                    )
                    if (!state.isSystemLocked) {
                        Text(
                            stringResource(R.string.category_delete_this),
                            style = ArcaDesignSystem.typography.body,
                            color = colors.negative,
                            modifier = Modifier
                                .padding(top = spacing.space5)
                                .pressable(vm::onDelete),
                        )
                    }
                }
            }
        }
    }
}

/**
 * MONTHLY BUDGET: "No budget ………… Set a budget", or − Rp2,500,000 + with "Remove budget", then
 * the roll-over checkbox. The amount can also be typed.
 */
@Composable
private fun BudgetSection(state: CategoryEditorUiState, budget: Long?, vm: CategoryEditorViewModel) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    val on = state.budgetText.isNotBlank()
    Column(Modifier.padding(vertical = spacing.space4)) {
        Row(Modifier.heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            FormSectionHeader(stringResource(R.string.category_monthly_budget), modifier = Modifier.weight(1f))
            if (on) {
                Text(
                    stringResource(R.string.category_remove_budget),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkSecondary,
                    modifier = Modifier.pressable(vm::onRemoveBudget),
                )
            }
        }
        AnimatedContent(
            targetState = on,
            transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(90))) },
            label = "budget",
        ) { budgeted ->
            if (budgeted) {
                Row(Modifier.fillMaxWidth().padding(top = spacing.space3), verticalAlignment = Alignment.CenterVertically) {
                    StepButton(ArcaIcons.minus, stringResource(R.string.category_budget_decrease, money.amount(BudgetStep.STEP_MINOR)), enabled = BudgetStep.canGoDown(budget)) { vm.onBudgetStep(up = false) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        Text(MoneyFormat.CURRENCY, style = ArcaDesignSystem.typography.currencyMark, color = colors.inkTertiary, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp))
                        BasicTextField(
                            value = state.budgetText,
                            onValueChange = vm::onBudgetChange,
                            singleLine = true,
                            textStyle = ArcaDesignSystem.typography.amountTitle.copy(color = colors.ink, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(colors.ink),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            visualTransformation = GroupedDigits(money),
                            modifier = Modifier.width(IntrinsicWidth),
                        )
                    }
                    StepButton(ArcaIcons.add, stringResource(R.string.category_budget_increase, money.amount(BudgetStep.STEP_MINOR)), enabled = true) { vm.onBudgetStep(up = true) }
                }
            } else {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = spacing.space2), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.category_no_budget), style = ArcaDesignSystem.typography.body, color = colors.inkSecondary, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.category_set_a_budget),
                            style = ArcaDesignSystem.typography.body.copy(fontWeight = FontWeight.Medium),
                            color = colors.ink,
                            modifier = Modifier.pressable(vm::onSetBudget),
                        )
                    }
                    Text(
                        stringResource(if (state.isNew) R.string.category_budget_help_new else R.string.category_budget_help_edit),
                        style = ArcaDesignSystem.typography.footnote,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = spacing.space2),
                    )
                }
            }
        }
        if (CategoryEditorIssue.BUDGET_UNCLEAR in state.issues) {
            Text(stringResource(R.string.issue_amount_unclear), style = ArcaDesignSystem.typography.footnote, color = colors.negative, modifier = Modifier.padding(top = spacing.space2))
        }
    }
    Hairline()
    if (on) {
        val next = YearMonth.now().plusMonths(1).atDay(1)
        CheckRow(
            stringResource(R.string.category_roll_over),
            state.rollsOver,
            vm::onRollsOverChange,
            helper = if (state.rollsOver) {
                stringResource(R.string.category_roll_over_on)
            } else {
                stringResource(R.string.category_roll_over_off_amount, budget?.let(money::amount).orEmpty(), shortDate(next))
            },
            modifier = Modifier.padding(top = spacing.space2, bottom = spacing.space4),
        )
        Hairline()
    }
}

private val IntrinsicWidth = 200.dp

/** A 44dp round − / + with a hairline ring. */
@Composable
private fun StepButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconAction(
        icon,
        label,
        onClick,
        size = 44.dp,
        enabled = enabled,
        tint = ArcaDesignSystem.colors.ink,
        modifier = Modifier.border(1.dp, ArcaDesignSystem.colors.hairline, ArcaDesignSystem.shapes.pill),
    )
}
