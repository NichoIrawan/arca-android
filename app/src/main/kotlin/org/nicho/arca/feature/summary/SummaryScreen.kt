package org.nicho.arca.feature.summary

import org.nicho.arca.ui.components.SummarySkeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.designsystem.Mascot
import org.nicho.arca.designsystem.MascotMood
import org.nicho.arca.domain.formatting.MoneyFormat
import org.nicho.arca.domain.model.BudgetStatus
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.readmodel.SummaryModel
import org.nicho.arca.ui.components.CompactEnvelopeRow
import org.nicho.arca.ui.components.EmptyState
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.ProgressBar
import org.nicho.arca.ui.components.ScreenHeader
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.TransactionRow
import org.nicho.arca.ui.components.arriving
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.rememberPageArrival
import org.nicho.arca.ui.format.monthLabel

@Composable
fun SummaryRoute(
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenCategory: (CategoryId) -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryScreen(state, onOpenReports, onOpenSettings, onOpenBudget, onOpenTransactions, onOpenTransaction, onOpenCategory, onAddTransaction)
}

/** The Overview tab (board 00), or its first-run empty state (board 11). */
@Composable
fun SummaryScreen(
    state: SummaryUiState,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenCategory: (CategoryId) -> Unit,
    onAddTransaction: () -> Unit,
) {
    val model = (state as? SummaryUiState.Ready)?.model
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (model == null) {
            ScreenHeader(
                title = stringResource(R.string.summary_title, monthLabel(java.time.YearMonth.now())),
                overline = stringResource(greeting()),
            ) {
                IconAction(ArcaIcons.reports, stringResource(R.string.reports_title), onOpenReports)
                IconAction(ArcaIcons.settings, stringResource(R.string.settings_title), onOpenSettings)
            }
            SummarySkeleton()
            return@Column
        }
        if (model.isEmpty) {
            // Empty: just "Overview" and Settings; there is nothing to report on yet.
            ScreenHeader(stringResource(R.string.nav_summary)) {
                IconAction(ArcaIcons.settings, stringResource(R.string.settings_title), onOpenSettings)
            }
            EmptyState(
                title = stringResource(R.string.summary_empty_title),
                body = stringResource(R.string.summary_empty_body),
                actionLabel = stringResource(R.string.summary_empty_action),
                onAction = onAddTransaction,
            )
            return@Column
        }
        ScreenHeader(
            title = stringResource(R.string.summary_title, monthLabel(model.month)),
            overline = stringResource(greeting()),
        ) {
            IconAction(ArcaIcons.reports, stringResource(R.string.reports_title), onOpenReports)
            IconAction(ArcaIcons.settings, stringResource(R.string.settings_title), onOpenSettings)
        }
        SummaryContent(model, onOpenReports, onOpenBudget, onOpenTransactions, onOpenTransaction, onOpenCategory)
    }
}

@Composable
private fun SummaryContent(
    model: SummaryModel,
    onOpenReports: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenCategory: (CategoryId) -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    val hasBudget = model.budget.envelopes.isNotEmpty()
    val arrival = rememberPageArrival()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenMargin)
            .padding(bottom = 110.dp)
            .navigationBarsPadding(),
    ) {
        Hero(model, hasBudget, onOpenBudget, Modifier.arriving(arrival, 0))

        model.expenseChangePercent?.let { change ->
            Insight(model, change, hasBudget, onOpenReports, Modifier.arriving(arrival, 1))
        }

        if (hasBudget) {
            Column(Modifier.padding(top = spacing.space6).arriving(arrival, 2)) {
                SectionLabel(
                    stringResource(R.string.nav_budget),
                    Modifier.padding(bottom = spacing.space1),
                    trailing = stringResource(R.string.action_see_all),
                    onTrailingClick = onOpenBudget,
                )
                val over = model.budget.envelopes.count { it.status == BudgetStatus.OVER_BUDGET }
                val near = model.budget.envelopes.count { it.status == BudgetStatus.NEAR_LIMIT }
                val states = listOfNotNull(
                    if (over > 0) pluralStringResource(R.plurals.summary_budget_over, over, over) else null,
                    if (near > 0) pluralStringResource(R.plurals.summary_budget_near, near, near) else null,
                ).joinToString(", ")
                Text(
                    listOfNotNull(
                        stringResource(R.string.summary_budget_left, money.compact(model.budget.totalLeftMinor), money.compact(model.budget.totalAvailableMinor)),
                        states.ifEmpty { null },
                    ).joinToString(" · "),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(bottom = spacing.space3),
                )
                model.needsAttention.forEach { envelope ->
                    val category = model.categories[envelope.categoryId]
                    CompactEnvelopeRow(
                        name = category?.name.orEmpty(),
                        envelope = envelope,
                        hue = category?.let { colors.category(it.colorHex) } ?: colors.inkSecondary,
                        onClick = { onOpenCategory(envelope.categoryId) },
                    )
                }
            }
        }

        if (model.categorySpend.isNotEmpty()) {
            Column(Modifier.padding(top = spacing.space6).arriving(arrival, 3)) {
                Row(Modifier.padding(bottom = spacing.space4), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.summary_by_category), Modifier.weight(1f))
                    Text(money.compact(model.summary.expenseMinor), style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
                }
                // The four largest, then everything else as one "Other" bar (client request,
                // 2026-10-14). Neutral, because it's no single category's colour.
                model.categoryBars.top.forEachIndexed { index, spend ->
                    CategoryBar(
                        name = spend.name,
                        amount = money.compact(spend.totalMinor, withCurrency = false),
                        share = spend.share,
                        color = colors.category(spend.colorHex),
                        index = index,
                        onClick = { onOpenCategory(spend.categoryId) },
                    )
                }
                model.categoryBars.rest?.let { rest ->
                    CategoryBar(
                        name = stringResource(R.string.summary_other),
                        amount = money.compact(rest.totalMinor, withCurrency = false),
                        share = rest.share,
                        color = colors.inkTertiary,
                        index = model.categoryBars.top.size,
                        onClick = onOpenReports,
                    )
                }
                Row(
                    Modifier
                        .padding(top = spacing.space4)
                        .pressable(onOpenReports),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.summary_full_report), style = ArcaDesignSystem.typography.footnote, color = colors.ink)
                    ArcaIcon(ArcaIcons.chevronRight, tint = colors.ink, size = 14.dp)
                }
            }
        }

        if (model.recent.isNotEmpty()) {
            Column(Modifier.padding(top = spacing.space6).arriving(arrival, 4)) {
                SectionLabel(
                    stringResource(R.string.summary_recent),
                    Modifier.padding(bottom = spacing.space3),
                    trailing = stringResource(R.string.action_see_all),
                    onTrailingClick = onOpenTransactions,
                )
                model.recent.forEachIndexed { i, item ->
                    if (i > 0) Hairline(startInset = 52.dp)
                    TransactionRow(item, onClick = { onOpenTransaction(item.transaction.id) })
                }
            }
        }
    }
}

/** One category's share of the month's spending: name, compact amount, a bar that grows in. */
@Composable
private fun CategoryBar(name: String, amount: String, share: Float, color: Color, index: Int, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .padding(vertical = ArcaDesignSystem.spacing.space2),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, style = ArcaDesignSystem.typography.subhead, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(amount, style = ArcaDesignSystem.typography.amountRow, color = colors.ink, modifier = Modifier.padding(start = ArcaDesignSystem.spacing.space2))
        }
        ProgressBar(share, color, Modifier.padding(top = 7.dp), delayMillis = 300 + index * ArcaDesignSystem.motion.revealStepMillis)
    }
}

/**
 * The hero (DSG-ARC-001 §5.3): net worth on the dark hero fill, the month's "left to spend" with a
 * drain bar, then In / Out / Net. The fill doesn't invert by theme (§3.4a).
 */
@Composable
private fun Hero(model: SummaryModel, hasBudget: Boolean, onOpenBudget: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val money = ArcaDesignSystem.money
    val onHero = colors.inkOnHero
    Column(
        modifier
            .padding(top = spacing.space5)
            .fillMaxWidth()
            .background(colors.surfaceHero, ArcaDesignSystem.shapes.large)
            .padding(spacing.space5),
    ) {
        Text(stringResource(R.string.accounts_net_worth).uppercase(), style = ArcaDesignSystem.typography.label, color = onHero.copy(alpha = 0.6f))
        MoneyFigure(model.netPositionMinor, color = onHero, markAlpha = 0.6f, modifier = Modifier.padding(top = spacing.space2))
        if (hasBudget) {
            Column(Modifier.pressable(onOpenBudget, pressedScale = 0.97f, onClickLabel = stringResource(R.string.summary_view_budget))) {
                Row(Modifier.padding(top = spacing.space4), verticalAlignment = Alignment.Bottom) {
                    Text(
                        stringResource(R.string.summary_left_to_spend, monthLabel(model.month)),
                        style = ArcaDesignSystem.typography.footnote,
                        color = onHero.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.summary_left_of, money.compact(model.budget.totalLeftMinor), money.compact(model.budget.totalAvailableMinor, withCurrency = false)),
                        style = ArcaDesignSystem.typography.footnote,
                        color = if (model.budget.totalLeftMinor < 0) colors.negative else onHero,
                    )
                }
                ProgressBar(model.leftToSpendFraction, onHero, Modifier.padding(top = 7.dp), track = onHero.copy(alpha = 0.22f), delayMillis = 240)
            }
        }
        Box(
            Modifier
                .padding(top = spacing.space5)
                .fillMaxWidth()
                .height(1.dp)
                .background(onHero.copy(alpha = 0.18f)),
        )
        Row(Modifier.padding(top = spacing.space4).height(IntrinsicSize.Min)) {
            HeroStat(stringResource(R.string.flow_in), money.compactSigned(model.summary.incomeMinor), onHero, Modifier.weight(1f))
            HeroDivider(onHero)
            HeroStat(
                stringResource(R.string.flow_out),
                if (model.summary.expenseMinor == 0L) money.compactSigned(0) else MoneyFormat.MINUS + money.compactSigned(model.summary.expenseMinor).removePrefix("+"),
                onHero,
                Modifier.weight(1f),
            )
            HeroDivider(onHero)
            HeroStat(stringResource(R.string.flow_net), money.compactSigned(model.summary.netMinor), onHero, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = ArcaDesignSystem.typography.footnote, color = color.copy(alpha = 0.6f))
        Text(value, style = ArcaDesignSystem.typography.amountRow, color = color, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun HeroDivider(color: Color) {
    Box(
        Modifier
            .padding(horizontal = ArcaDesignSystem.spacing.space3)
            .width(1.dp)
            .fillMaxHeight()
            .background(color.copy(alpha = 0.18f)),
    )
}

/**
 * "Spending less than in August": a calm comparison, never a verdict (BEH-P2), with the mascot
 * beside it. The mascot's mood is the month's budget as a whole (DSG-ARC-001 §13: Overview = the
 * total).
 */
@Composable
private fun Insight(model: SummaryModel, change: Int, hasBudget: Boolean, onOpenReports: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val previous = monthLabel(model.month.minusMonths(1))
    val headline = when {
        change < 0 -> stringResource(R.string.summary_insight_less, previous)
        change > 0 -> stringResource(R.string.summary_insight_more, previous)
        else -> stringResource(R.string.summary_insight_same, previous)
    }
    val detail = buildList {
        add(
            when {
                change < 0 -> stringResource(R.string.summary_insight_down, -change)
                change > 0 -> stringResource(R.string.summary_insight_up, change)
                else -> stringResource(R.string.summary_insight_flat)
            },
        )
        if (hasBudget) add(stringResource(R.string.summary_insight_left, ArcaDesignSystem.money.compact(model.budget.totalLeftMinor)))
    }.joinToString(" ")
    Row(
        modifier
            .padding(top = spacing.space4)
            .fillMaxWidth()
            .pressable(onOpenReports, pressedScale = 0.98f)
            .background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
            .padding(spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space3),
    ) {
        Mascot(if (hasBudget) MascotMood.of(model.budget.totalStatus) else MascotMood.CALM, size = 34.dp)
        Column(Modifier.weight(1f)) {
            Text(headline, style = ArcaDesignSystem.typography.headline, color = colors.ink)
            Text(detail, style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        ArcaIcon(ArcaIcons.chevronRight, tint = colors.inkTertiary, size = 16.dp)
    }
}

private fun greeting(): Int {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 4..10 -> R.string.greeting_morning
        in 11..14 -> R.string.greeting_afternoon
        in 15..17 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
}
