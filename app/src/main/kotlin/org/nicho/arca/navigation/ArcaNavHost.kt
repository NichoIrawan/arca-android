package org.nicho.arca.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import java.time.YearMonth
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.feature.accounts.AccountDetailRoute as AccountDetailScreenRoute
import org.nicho.arca.feature.accounts.AccountEditorRoute as AccountEditorScreenRoute
import org.nicho.arca.feature.accounts.AccountListRoute
import org.nicho.arca.feature.budget.BudgetRoute as BudgetScreenRoute
import org.nicho.arca.feature.budget.CategoryDetailRoute as CategoryDetailScreenRoute
import org.nicho.arca.feature.budget.CategoryEditorRoute as CategoryEditorScreenRoute
import org.nicho.arca.feature.capture.CaptureRequest
import org.nicho.arca.feature.reports.ReportsRoute as ReportsScreenRoute
import org.nicho.arca.feature.settings.SettingsRoute as SettingsScreenRoute
import org.nicho.arca.feature.summary.SummaryRoute as SummaryScreenRoute
import org.nicho.arca.feature.transactions.SearchRoute as SearchScreenRoute
import org.nicho.arca.feature.transactions.TransactionDetailRoute as TransactionDetailScreenRoute
import org.nicho.arca.feature.transactions.TransactionListRoute

/**
 * Every destination (SDD-ARC-002 §9.8). Tab roots first, then pushed screens. Screens get
 * callbacks, never the NavController, so each stays previewable and testable on its own.
 */
@Composable
fun ArcaNavHost(appState: ArcaAppState, modifier: Modifier = Modifier) {
    val openTransaction: (TransactionId) -> Unit = { appState.navigate(TransactionDetailRoute(it.value.toString())) }
    val openAccount: (AccountId) -> Unit = { appState.navigate(AccountDetailRoute(it.value.toString())) }
    val openCategory: (CategoryId, YearMonth) -> Unit = { id, month ->
        appState.navigate(CategoryDetailRoute(id.value.toString(), month.year, month.monthValue))
    }
    val editCategory: (CategoryId?) -> Unit = { appState.navigate(CategoryEditorRoute(it?.value?.toString())) }
    val openReports: (YearMonth) -> Unit = { appState.navigate(ReportsRoute(it.year, it.monthValue)) }
    val addTransaction = { appState.openCapture() }

    // Between any two screens the old one crossfades into the new in 90ms (the prototype's view
    // transition, tightened to under 100ms at the client's request, 2026-10-15). Fixed chrome is identical in both, so it reads as staying still; pushed screens
    // add their own slide-in for the content.
    NavHost(
        appState.navController,
        startDestination = SummaryRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(90)) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(90)) },
        popExitTransition = { fadeOut(tween(90)) },
    ) {
        // ── Tabs ──
        composable<SummaryRoute> {
            SummaryScreenRoute(
                onOpenReports = { openReports(YearMonth.now()) },
                onOpenSettings = { appState.navigate(SettingsRoute) },
                onOpenBudget = { appState.navigateToTab(TopLevelDestination.BUDGET) },
                onOpenTransactions = { appState.navigateToTab(TopLevelDestination.TRANSACTIONS) },
                onOpenTransaction = openTransaction,
                onOpenCategory = { openCategory(it, YearMonth.now()) },
                onAddTransaction = addTransaction,
            )
        }
        composable<TransactionsRoute> {
            TransactionListRoute(
                onOpenTransaction = openTransaction,
                onOpenSearch = { appState.navigate(SearchRoute) },
                onAddTransaction = addTransaction,
            )
        }
        composable<BudgetRoute> {
            BudgetScreenRoute(onOpenCategory = openCategory, onEditCategory = editCategory, onOpenReport = openReports)
        }
        composable<AccountsRoute> {
            AccountListRoute(onOpenAccount = openAccount, onAddAccount = { type -> appState.navigate(AccountEditorRoute(type = type?.code)) })
        }

        // ── Pushed ──
        composable<ReportsRoute> { ReportsScreenRoute(onBack = appState::back, onOpenCategory = openCategory) }
        composable<SettingsRoute> { SettingsScreenRoute(onBack = appState::back, onOpenReports = { openReports(YearMonth.now()) }) }
        composable<SearchRoute> { SearchScreenRoute(onBack = appState::back, onOpenTransaction = openTransaction) }
        composable<TransactionDetailRoute> {
            TransactionDetailScreenRoute(
                onBack = appState::back,
                onEdit = { appState.openCapture(CaptureRequest(editing = it)) },
            )
        }
        composable<AccountDetailRoute> {
            AccountDetailScreenRoute(
                onBack = appState::back,
                onEdit = { appState.navigate(AccountEditorRoute(it.value.toString())) },
                onOpenTransaction = openTransaction,
            )
        }
        composable<AccountEditorRoute> { AccountEditorScreenRoute(onDone = appState::back) }
        composable<CategoryDetailRoute> {
            CategoryDetailScreenRoute(onBack = appState::back, onEdit = { editCategory(it) }, onOpenTransaction = openTransaction)
        }
        composable<CategoryEditorRoute> { CategoryEditorScreenRoute(onDone = appState::back) }
    }
}
