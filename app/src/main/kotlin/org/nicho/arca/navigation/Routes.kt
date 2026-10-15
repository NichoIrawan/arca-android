package org.nicho.arca.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (SDD-ARC-002 §9.8). Ids travel as UUID strings; months as year + month.
// The capture sheet is not a destination: it's shell state (ArcaAppState.captureRequest).

// ── Tab roots ──
@Serializable data object SummaryRoute
@Serializable data object TransactionsRoute
@Serializable data object BudgetRoute
@Serializable data object AccountsRoute

// ── Pushed ──
@Serializable data class ReportsRoute(val year: Int, val month: Int)
@Serializable data object SettingsRoute
@Serializable data object SearchRoute
@Serializable data class TransactionDetailRoute(val transactionId: String)
@Serializable data class AccountDetailRoute(val accountId: String)
@Serializable data class AccountEditorRoute(val accountId: String? = null, val type: String? = null)
@Serializable data class CategoryDetailRoute(val categoryId: String, val year: Int, val month: Int)
@Serializable data class CategoryEditorRoute(val categoryId: String? = null)
