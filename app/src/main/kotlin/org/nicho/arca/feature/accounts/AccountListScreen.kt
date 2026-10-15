package org.nicho.arca.feature.accounts

import org.nicho.arca.ui.components.rememberReveal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import org.nicho.arca.ui.components.AccountsSkeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaColors
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.readmodel.AccountBalance
import org.nicho.arca.domain.readmodel.AccountsModel
import org.nicho.arca.ui.components.ArchivedSection
import org.nicho.arca.ui.components.EmptyState
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.ScreenHeader
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.arriving
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.rememberPageArrival

@Composable
fun AccountListRoute(
    onOpenAccount: (AccountId) -> Unit,
    onAddAccount: (AccountType?) -> Unit,
    viewModel: AccountListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountListScreen(state, onOpenAccount, onAddAccount)
}

/** The Accounts tab (board 03), or its empty state (board 29). */
@Composable
fun AccountListScreen(
    state: AccountListUiState,
    onOpenAccount: (AccountId) -> Unit,
    onAddAccount: (AccountType?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(stringResource(R.string.nav_accounts)) {
            IconAction(ArcaIcons.add, stringResource(R.string.accounts_add), { onAddAccount(null) })
        }
        when (state) {
            AccountListUiState.Loading -> AccountsSkeleton()
            AccountListUiState.Empty -> AccountsEmpty(onAddAccount)
            is AccountListUiState.Ready -> AccountsContent(state.model, onOpenAccount)
        }
    }
}

@Composable
private fun AccountsContent(model: AccountsModel, onOpenAccount: (AccountId) -> Unit) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val arrival = rememberPageArrival()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenMargin)
            .padding(bottom = 110.dp)
            .navigationBarsPadding(),
    ) {
        Column(Modifier.padding(top = spacing.space5).arriving(arrival, 0)) {
            SectionLabel(stringResource(R.string.accounts_net_worth), Modifier.padding(bottom = spacing.space3))
            MoneyFigure(
                model.netPositionMinor,
                style = ArcaDesignSystem.typography.amountTitle,
                color = if (model.netPositionMinor < 0) colors.negative else colors.ink,
            )
            CompositionBar(model, Modifier.padding(top = spacing.space4))
        }

        // A negative balance is a warning (client decision, 2026-10-14): say how many, up top.
        val negative = model.groups.flatMap { it.accounts }.filter { it.balanceMinor < 0 }
        if (negative.isNotEmpty()) {
            NegativeBalanceNotice(negative.map { it.account.name }, Modifier.padding(top = spacing.space5).arriving(arrival, 1))
        }

        if (model.favorites.isNotEmpty()) {
            Column(Modifier.padding(top = spacing.space6).arriving(arrival, 2)) {
                SectionLabel(stringResource(R.string.accounts_favorites), Modifier.padding(bottom = spacing.space3))
                model.favorites.chunked(2).forEachIndexed { i, pair ->
                    Row(
                        Modifier.padding(top = if (i == 0) 0.dp else spacing.space3),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space3),
                    ) {
                        pair.forEach { favorite -> FavoriteCard(favorite, Modifier.weight(1f)) { onOpenAccount(favorite.account.id) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        model.groups.forEachIndexed { index, group ->
            Column(Modifier.padding(top = spacing.space6).arriving(arrival, 3 + index)) {
                Row(Modifier.padding(bottom = spacing.space3), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(accountTypeLabel(group.type), Modifier.weight(1f))
                    Text(ArcaDesignSystem.money.digits(group.subtotalMinor), style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
                }
                group.accounts.forEachIndexed { i, balance ->
                    if (i > 0) Hairline(startInset = 52.dp)
                    AccountRow(balance) { onOpenAccount(balance.account.id) }
                }
            }
        }

        // Archived accounts stay out of the way, behind one row (client request, 2026-10-14).
        if (model.archived.isNotEmpty()) {
            ArchivedSection(
                title = stringResource(R.string.accounts_archived, model.archived.size),
                help = stringResource(R.string.accounts_archived_help),
                modifier = Modifier
                    .padding(top = spacing.space3)
                    .arriving(arrival, 3 + model.groups.size),
            ) {
                model.archived.forEach { balance ->
                    AccountRow(balance, archived = true) { onOpenAccount(balance.account.id) }
                    Hairline(startInset = 52.dp)
                }
            }
        }
    }
}

/** "ShopeePay is below zero": a calm warning, never an error state (FR-222). */
@Composable
private fun NegativeBalanceNotice(names: List<String>, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.negative.copy(alpha = 0.10f), ArcaDesignSystem.shapes.medium)
            .padding(ArcaDesignSystem.spacing.space4),
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcaIcon(ArcaIcons.warning, tint = colors.negative, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(
                if (names.size == 1) stringResource(R.string.accounts_negative_one, names.single()) else pluralStringResource(R.plurals.accounts_negative_many, names.size, names.size),
                style = ArcaDesignSystem.typography.headline,
                color = colors.ink,
            )
            Text(
                stringResource(R.string.accounts_negative_help),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The account-type mix of the money held ("Bank 96%"): one segment per type, 2dp apart, each
 * growing in after the last (m-reveal-x, 260ms in, 70ms apart).
 */
@Composable
private fun CompositionBar(model: AccountsModel, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val parts = model.groups.filter { it.share > 0f }
    if (parts.isEmpty()) return
    Column(modifier) {
        // One drawn bar: the segments grow in the draw phase, so the reveal never recomposes.
        val grown = parts.mapIndexed { i, _ -> rememberReveal(1f, delayMillis = 260 + i * ArcaDesignSystem.motion.revealStepMillis) }
        val hues = parts.map { typeHue(it.type, colors) }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .drawBehind {
                    val gap = 2.dp.toPx()
                    val full = size.width - gap * (parts.size - 1)
                    var x = 0f
                    parts.forEachIndexed { i, part ->
                        val w = full * part.share * grown[i].value
                        if (w > 0f) drawRoundRect(hues[i], Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
                        x += w + gap
                    }
                },
        )
        Row(Modifier.padding(top = ArcaDesignSystem.spacing.space3), horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space4)) {
            parts.forEach { part ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).background(typeHue(part.type, colors), ArcaDesignSystem.shapes.pill))
                    Text(
                        "${accountTypeLabel(part.type)} ${Math.round(part.share * 100)}%",
                        style = ArcaDesignSystem.typography.footnote,
                        color = colors.inkSecondary,
                    )
                }
            }
        }
    }
}

/** The favourite's mark: a 1.5dp brass outline on the tile, brass glyph (DSG-ARC-001 §3.5). */
@Composable
fun AccountTile(account: Account, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val colors = ArcaDesignSystem.colors
    val shape = ArcaDesignSystem.shapes.small
    Box(
        modifier
            .size(size)
            .then(
                if (account.isFavorite) Modifier.border(1.5.dp, colors.brand, shape) else Modifier.background(colors.surfaceSunken, shape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        ArcaIcon(
            ArcaIcons.named(account.icon),
            tint = if (account.isFavorite) colors.brand else colors.inkSecondary,
            size = if (size >= 40.dp) 19.dp else 18.dp,
        )
    }
}

/** A favourite as a card, two to a row. */
@Composable
private fun FavoriteCard(balance: AccountBalance, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val negative = balance.balanceMinor < 0
    Column(
        modifier
            .pressable(onClick, pressedScale = 0.98f)
            .background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
            .padding(ArcaDesignSystem.spacing.space4),
    ) {
        AccountTile(balance.account, size = 36.dp)
        Text(
            balance.account.name,
            style = ArcaDesignSystem.typography.subhead,
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = ArcaDesignSystem.spacing.space3),
        )
        Text(
            ArcaDesignSystem.money.digits(balance.balanceMinor),
            style = ArcaDesignSystem.typography.amountRow,
            color = if (negative) colors.negative else colors.ink,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * An account row: tile, name, and "Favorite" in brass or "Negative balance" in the negative
 * colour with a warning mark, then the balance with its sign (FR-222).
 */
@Composable
fun AccountRow(balance: AccountBalance, archived: Boolean = false, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val account = balance.account
    val negative = balance.balanceMinor < 0
    val caption = when {
        negative -> stringResource(R.string.accounts_negative_balance)
        archived -> null
        account.isFavorite -> stringResource(R.string.accounts_favorite)
        else -> null
    }
    val spoken = ArcaDesignSystem.spokenMoney.format(balance.balanceMinor)
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .clearAndSetSemantics { contentDescription = listOfNotNull(account.name, caption, spoken).joinToString(", ") }
            .padding(vertical = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
    ) {
        AccountTile(account, modifier = if (archived) Modifier.alpha(0.55f) else Modifier)
        Column(Modifier.weight(1f)) {
            Text(account.name, style = ArcaDesignSystem.typography.headline, color = if (archived) colors.inkSecondary else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) {
                Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (negative) ArcaIcon(ArcaIcons.warning, tint = colors.negative, size = 13.dp)
                    Text(caption, style = ArcaDesignSystem.typography.footnote, color = if (negative) colors.negative else colors.brand)
                }
            }
        }
        Text(
            ArcaDesignSystem.money.digits(balance.balanceMinor),
            style = ArcaDesignSystem.typography.amountRow,
            color = when {
                negative -> colors.negative
                archived -> colors.inkSecondary
                else -> colors.ink
            },
        )
    }
}

/** No accounts yet (board 29): the mascot, why accounts matter, and the common types as shortcuts. */
@Composable
private fun AccountsEmpty(onAddAccount: (AccountType?) -> Unit) {
    val colors = ArcaDesignSystem.colors
    EmptyState(
        title = stringResource(R.string.accounts_empty_title),
        body = stringResource(R.string.accounts_empty_body),
        topPadding = 130.dp,
    ) {
        Text(
            stringResource(R.string.accounts_common_types).uppercase(),
            style = ArcaDesignSystem.typography.label,
            color = colors.inkTertiary,
            modifier = Modifier.padding(bottom = ArcaDesignSystem.spacing.space3),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space2)) {
            listOf(AccountType.CASH, AccountType.BANK, AccountType.EWALLET).forEach { type ->
                Row(
                    Modifier
                        .pressable({ onAddAccount(type) }, pressedScale = 0.94f)
                        .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
                        .padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space2),
                ) {
                    ArcaIcon(ArcaIcons.named(ArcaIcons.forAccountType(type)), tint = colors.inkSecondary, size = 16.dp)
                    Text(accountTypeLabel(type), style = ArcaDesignSystem.typography.footnote, color = colors.ink)
                }
            }
        }
    }
}

@Composable
fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_type_cash
        AccountType.BANK -> R.string.account_type_bank
        AccountType.EWALLET -> R.string.account_type_ewallet
        AccountType.OTHER -> R.string.account_type_other
    },
)

/** Account types take the prototype's hues on the composition bar: Bank plum, E-Wallet teal, Cash clay. */
fun typeHue(type: AccountType, colors: ArcaColors): Color = when (type) {
    AccountType.BANK -> colors.data4
    AccountType.EWALLET -> colors.data3
    AccountType.CASH -> colors.data1
    AccountType.OTHER -> colors.data2
}
