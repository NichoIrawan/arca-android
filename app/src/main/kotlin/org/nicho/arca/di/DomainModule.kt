package org.nicho.arca.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.nicho.arca.domain.adjustment.BalanceAdjustmentService
import org.nicho.arca.domain.budget.BudgetService
import org.nicho.arca.domain.readmodel.ObserveAccountDetail
import org.nicho.arca.domain.readmodel.ObserveAccounts
import org.nicho.arca.domain.readmodel.ObserveBudget
import org.nicho.arca.domain.readmodel.ObserveCategoryDetail
import org.nicho.arca.domain.readmodel.ObserveReports
import org.nicho.arca.domain.readmodel.ObserveSummary
import org.nicho.arca.domain.readmodel.ObserveTransactionList
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.repository.TransactionWriting
import org.nicho.arca.domain.repository.TransferPersisting
import org.nicho.arca.domain.time.PeriodClock
import org.nicho.arca.domain.transfer.TransferService
import org.nicho.arca.domain.validation.TransactionValidator

/**
 * SDD-ARC-002 DD-A05: every domain object is built here, because :domain carries no @Inject.
 * This is the composition root: reading it shows what each piece is made of.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideTransactionValidator(): TransactionValidator = TransactionValidator.standard

    @Provides
    fun provideTransferService(persistence: TransferPersisting, categories: CategoryStore) =
        TransferService(persistence, categories)

    @Provides
    fun provideBudgetService(categories: CategoryStore, budgets: BudgetStore, clock: PeriodClock) =
        BudgetService(categories, budgets, clock)

    @Provides
    fun provideBalanceAdjustmentService(reading: TransactionReading, writing: TransactionWriting, accounts: AccountReading) =
        BalanceAdjustmentService(reading, writing, accounts)

    // ── Read models ──

    @Provides
    fun provideObserveSummary(
        transactions: TransactionReading,
        accounts: AccountReading,
        categories: CategoryStore,
        budgets: BudgetStore,
        clock: PeriodClock,
    ) = ObserveSummary(transactions, accounts, categories, budgets, clock)

    @Provides
    fun provideObserveAccounts(transactions: TransactionReading, accounts: AccountReading) =
        ObserveAccounts(transactions, accounts)

    @Provides
    fun provideObserveAccountDetail(
        transactions: TransactionReading,
        accounts: AccountReading,
        categories: CategoryStore,
        clock: PeriodClock,
    ) = ObserveAccountDetail(transactions, accounts, categories, clock)

    @Provides
    fun provideObserveTransactionList(
        transactions: TransactionReading,
        accounts: AccountReading,
        categories: CategoryStore,
        clock: PeriodClock,
    ) = ObserveTransactionList(transactions, accounts, categories, clock)

    @Provides
    fun provideObserveReports(transactions: TransactionReading, categories: CategoryStore, clock: PeriodClock) =
        ObserveReports(transactions, categories, clock)

    @Provides
    fun provideObserveBudget(
        transactions: TransactionReading,
        categories: CategoryStore,
        budgets: BudgetStore,
        clock: PeriodClock,
    ) = ObserveBudget(transactions, categories, budgets, clock)

    @Provides
    fun provideObserveCategoryDetail(
        transactions: TransactionReading,
        accounts: AccountReading,
        categories: CategoryStore,
        budgets: BudgetStore,
        clock: PeriodClock,
    ) = ObserveCategoryDetail(transactions, accounts, categories, budgets, clock)
}
