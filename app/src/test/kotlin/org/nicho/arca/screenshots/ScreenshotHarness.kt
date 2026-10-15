package org.nicho.arca.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaTheme
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.readmodel.ObserveAccountDetail
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.readmodel.ReportMetric
import org.nicho.arca.domain.readmodel.BalanceScale
import org.nicho.arca.domain.readmodel.AccountDetailSelection
import org.nicho.arca.domain.readmodel.ObserveAccounts
import org.nicho.arca.domain.readmodel.ObserveBudget
import org.nicho.arca.domain.readmodel.ObserveCategoryDetail
import org.nicho.arca.domain.readmodel.ObserveReports
import org.nicho.arca.domain.readmodel.ObserveSummary
import org.nicho.arca.domain.readmodel.ObserveTransactionList
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.readmodel.ReportRange
import org.nicho.arca.domain.readmodel.ReportSelection
import org.nicho.arca.domain.readmodel.SortOrder
import org.nicho.arca.domain.readmodel.TransactionFilter
import org.nicho.arca.domain.readmodel.TrendGranularity
import org.nicho.arca.domain.readmodel.TypeFilter
import org.nicho.arca.domain.repository.ThemePreference
import org.nicho.arca.feature.accounts.AccountDetailScreen
import org.nicho.arca.feature.accounts.AccountDetailUiState
import org.nicho.arca.feature.accounts.AccountEditorScreen
import org.nicho.arca.feature.accounts.AccountEditorUiState
import org.nicho.arca.feature.accounts.AccountListScreen
import org.nicho.arca.feature.accounts.AccountListUiState
import org.nicho.arca.feature.budget.BudgetScreen
import org.nicho.arca.feature.budget.BudgetUiState
import org.nicho.arca.feature.budget.CategoryDetailScreen
import org.nicho.arca.feature.budget.CategoryDetailUiState
import org.nicho.arca.feature.capture.CaptureForm
import org.nicho.arca.feature.capture.CaptureMode
import org.nicho.arca.feature.capture.CaptureUiState
import org.nicho.arca.feature.reports.ExportState
import org.nicho.arca.feature.reports.ReportsScreen
import org.nicho.arca.feature.reports.ReportsUiState
import org.nicho.arca.feature.summary.SummaryScreen
import org.nicho.arca.feature.summary.SummaryUiState
import org.nicho.arca.feature.transactions.TransactionDetailScreen
import org.nicho.arca.feature.transactions.TransactionDetailUiState
import org.nicho.arca.feature.transactions.TransactionListScreen
import org.nicho.arca.feature.transactions.TransactionListUiState
import org.nicho.arca.navigation.TopLevelDestination
import org.nicho.arca.ui.FloatingTabBar
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders screens to PNG on the JVM, to hold them against the prototype's boards (412 × 915).
 * Opt-in, because it writes files rather than asserting anything:
 *
 *     ./gradlew :app:testDebugUnitTest -Pscreenshots --tests "*ScreenshotHarness*"
 *
 * Images land in app/build/screenshots. "-tall" renders use a 2000dp window to show a whole page.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-port-hdpi")
class ScreenshotHarness {

    @get:Rule
    val compose = createComposeRule()

    private val outDir: File? = System.getProperty("arca.screenshots.dir")?.let(::File)

    private fun sample(): SampleLedger = runBlocking { SampleLedger().build() }

    private fun shoot(name: String, theme: ThemePreference = ThemePreference.LIGHT, tab: TopLevelDestination? = null, content: @Composable () -> Unit) {
        assumeTrue("Run with -Pscreenshots", outDir != null)
        compose.mainClock.autoAdvance = false // the mascot blinks forever; drive the clock by hand
        compose.setContent {
            ArcaTheme(theme) {
                Box(Modifier.fillMaxSize().background(ArcaDesignSystem.colors.surface)) {
                    content()
                    if (tab != null) {
                        Box(Modifier.align(Alignment.BottomCenter)) { FloatingTabBar(tab, {}, {}) }
                    }
                }
            }
        }
        repeat(30) { compose.mainClock.advanceTimeBy(100) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        outDir!!.mkdirs()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun summary(s: SampleLedger) = runBlocking {
        ObserveSummary(s.ledger.transactions, s.ledger.accounts, s.ledger.categories, s.ledger.budgets, s.clock.periodClock, dispatcher = Dispatchers.Unconfined)().first()
    }

    private fun accounts(s: SampleLedger) = runBlocking { ObserveAccounts(s.ledger.transactions, s.ledger.accounts, dispatcher = Dispatchers.Unconfined)().first() }

    private fun budget(s: SampleLedger) = runBlocking {
        ObserveBudget(s.ledger.transactions, s.ledger.categories, s.ledger.budgets, s.clock.periodClock, dispatcher = Dispatchers.Unconfined)(flowOf(s.month)).first()
    }

    private fun reports(s: SampleLedger, selection: ReportSelection) = runBlocking {
        ObserveReports(s.ledger.transactions, s.ledger.categories, s.clock.periodClock, dispatcher = Dispatchers.Unconfined)(flowOf(selection)).first()
    }

    private fun transactions(s: SampleLedger, filter: TransactionFilter = TransactionFilter(PeriodChoice.Month(s.month))): TransactionListUiState.Ready {
        val model = runBlocking {
            ObserveTransactionList(s.ledger.transactions, s.ledger.accounts, s.ledger.categories, s.clock.periodClock, dispatcher = Dispatchers.Unconfined)(flowOf(filter)).first()
        }
        val accounts = runBlocking { s.ledger.accounts.observeAll(true).first() }
        val categories = runBlocking { s.ledger.categories.observeAll(true).first() }
        return TransactionListUiState.Ready(model, accounts, categories)
    }

    @Test fun overview() {
        val s = sample()
        shoot("00-overview", tab = TopLevelDestination.SUMMARY) { SummaryScreen(SummaryUiState.Ready(summary(s)), {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun overviewTall() {
        val s = sample()
        shoot("00-overview-tall") { SummaryScreen(SummaryUiState.Ready(summary(s)), {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun overviewDark() {
        val s = sample()
        shoot("00-overview-dark", ThemePreference.DARK, tab = TopLevelDestination.SUMMARY) { SummaryScreen(SummaryUiState.Ready(summary(s)), {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun overviewEmpty() {
        val empty = SampleLedger()
        shoot("11-overview-empty", tab = TopLevelDestination.SUMMARY) { SummaryScreen(SummaryUiState.Ready(summary(empty)), {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun transactionsList() {
        val s = sample()
        shoot("01-transactions", tab = TopLevelDestination.TRANSACTIONS) {
            TransactionListScreen(transactions(s), null, {}, {}, {}, {}, {}, {})
        }
    }

    @Test fun transactionsFiltered() {
        val s = sample()
        val filter = TransactionFilter(PeriodChoice.Month(s.month), type = TypeFilter.EXPENSE, accountIds = setOf(s.gopay.id), categoryIds = setOf(s.food.id))
        shoot("13-transactions-filtered", tab = TopLevelDestination.TRANSACTIONS) {
            TransactionListScreen(transactions(s, filter), null, {}, {}, {}, {}, {}, {})
        }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun budgetTall() {
        val s = sample()
        shoot("02-budget-tall") { BudgetScreen(BudgetUiState.Ready(s.month, budget(s)), {}, { _, _ -> }, {}, {}) }
    }

    @Test fun budget() {
        val s = sample()
        shoot("02-budget", tab = TopLevelDestination.BUDGET) { BudgetScreen(BudgetUiState.Ready(s.month, budget(s)), {}, { _, _ -> }, {}, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun accountsTall() {
        val s = sample()
        shoot("03-accounts-tall") { AccountListScreen(AccountListUiState.Ready(accounts(s)), {}, {}) }
    }

    @Test fun accountsEmpty() {
        shoot("29-accounts-empty", tab = TopLevelDestination.ACCOUNTS) { AccountListScreen(AccountListUiState.Empty, {}, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun reports28Tall() {
        val s = sample()
        val model = reports(s, ReportSelection(ReportRange.LastDays(28), TrendGranularity.WEEKLY))
        shoot("06-reports-28-tall") { ReportsScreen(ReportsUiState.Ready(model), ExportState.Idle, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}) }
    }

    @Test fun reportsMonthDaily() {
        val s = sample()
        val model = reports(s, ReportSelection(ReportRange.Month(s.month), TrendGranularity.DAILY))
        shoot("06-reports-month-daily") { ReportsScreen(ReportsUiState.Ready(model), ExportState.Idle, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun reportsIncomeTall() {
        val s = sample()
        val model = reports(s, ReportSelection(ReportRange.Month(s.month), TrendGranularity.WEEKLY))
        shoot("06-reports-income-tall") { ReportsScreen(ReportsUiState.Ready(model, ReportMetric.INCOME), ExportState.Idle, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun reportsNetTall() {
        val s = sample()
        val model = reports(s, ReportSelection(ReportRange.Month(s.month), TrendGranularity.WEEKLY))
        shoot("06-reports-net-tall") { ReportsScreen(ReportsUiState.Ready(model, ReportMetric.NET), ExportState.Idle, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}) }
    }

    @Test fun overviewLoading() {
        shoot("00-overview-loading", tab = TopLevelDestination.SUMMARY) { SummaryScreen(SummaryUiState.Loading, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun accountDetailLoading() {
        shoot("08-account-detail-loading") { AccountDetailScreen(AccountDetailUiState.Loading, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    private fun accountDetail(s: SampleLedger, account: AccountId, selection: AccountDetailSelection) = runBlocking {
        ObserveAccountDetail(s.ledger.transactions, s.ledger.accounts, s.ledger.categories, s.clock.periodClock, dispatcher = Dispatchers.Unconfined)(account, flowOf(selection)).first()!!
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun accountDetailTall() {
        val s = sample()
        val model = accountDetail(s, s.bca.id, AccountDetailSelection(PeriodChoice.Month(s.month)))
        shoot("08-account-detail-tall") { AccountDetailScreen(AccountDetailUiState.Ready(model), {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun accountDetailFocusedWeek() {
        val s = sample()
        val month = accountDetail(s, s.bca.id, AccountDetailSelection(PeriodChoice.Month(s.month), scale = BalanceScale.WEEKLY))
        val model = accountDetail(s, s.bca.id, month.selection.copy(focus = month.trend[2].first))
        shoot("08-account-detail-week") { AccountDetailScreen(AccountDetailUiState.Ready(model), {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun accountDetailNegative() {
        val s = sample()
        val model = accountDetail(s, s.shopee.id, AccountDetailSelection(PeriodChoice.AllTime))
        shoot("08-account-detail-negative") { AccountDetailScreen(AccountDetailUiState.Ready(model), {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun accountEditorTall() {
        val state = AccountEditorUiState(
            isLoading = false, isNew = false, name = "BCA Utama", type = AccountType.BANK, icon = "landmark",
            openingText = "12000000", openingLocked = true, isFavorite = true, currentBalanceMinor = 14_500_000,
        )
        shoot("09-account-editor-tall") { AccountEditorScreen(state, null, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test @Config(qualifiers = "w412dp-h2000dp-port-hdpi")
    fun categoryDetailTall() {
        val s = sample()
        val model = runBlocking {
            ObserveCategoryDetail(s.ledger.transactions, s.ledger.accounts, s.ledger.categories, s.ledger.budgets, s.clock.periodClock, dispatcher = Dispatchers.Unconfined)(
                s.food.id, flowOf(s.month), flowOf(null), flowOf(SortOrder.NEWEST),
            ).first()!!
        }
        val accounts = runBlocking { s.ledger.accounts.observeAll(true).first() }.filter { it.id in model.accountIds }
        shoot("25-category-detail-tall") {
            CategoryDetailScreen(CategoryDetailUiState.Ready(model, accounts, null, SortOrder.NEWEST), {}, {}, {}, {}, {}, {})
        }
    }

    @Test fun transferDetail() {
        val s = sample()
        val items = transactions(s).model.days.flatMap { it.items }
        val transfer: TransactionListItem = items.first { it.transaction.transferGroupId != null }
        shoot("07-transfer-detail") {
            TransactionDetailScreen(TransactionDetailUiState.Ready(transfer, listOf(transfer)), {}, {}, {})
        }
    }

    @Test fun capture() {
        val s = sample()
        val accounts = runBlocking { s.ledger.accounts.observeAll(false).first() }.sortedByDescending { it.isFavorite }
        val categories = runBlocking { s.ledger.categories.observeAll(false).first() }.filter { it.kind == org.nicho.arca.domain.model.CategoryKind.EXPENSE }
        val balances = mapOf(s.shopee.id to -120_000L)
        val state = CaptureUiState(
            mode = CaptureMode.EXPENSE, amountText = "50000", amountMinor = 50_000, accounts = accounts, accountId = s.shopee.id,
            categories = categories, categoryId = s.food.id, balances = balances,
        )
        shoot("04-capture") {
            CaptureForm(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

}
