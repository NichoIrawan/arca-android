package org.nicho.arca.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaIcons

/**
 * The four tabs, in the prototype's order: Overview · Transactions · [+] · Budget · Accounts
 * (SDD-ARC-002 DD-A20). The add button sits between Transactions and Budget.
 */
enum class TopLevelDestination(
    val route: Any,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    SUMMARY(SummaryRoute, ArcaIcons.overview, R.string.nav_summary),
    TRANSACTIONS(TransactionsRoute, ArcaIcons.transactions, R.string.nav_transactions),
    BUDGET(BudgetRoute, ArcaIcons.budget, R.string.nav_budget),
    ACCOUNTS(AccountsRoute, ArcaIcons.accounts, R.string.nav_accounts),
}
