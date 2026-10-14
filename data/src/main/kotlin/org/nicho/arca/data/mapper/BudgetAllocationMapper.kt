package org.nicho.arca.data.mapper

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import org.nicho.arca.data.db.entities.BudgetAllocationEntity
import org.nicho.arca.domain.model.BudgetAllocation

// "yyyy-MM" on the wire (SDD-ARC-002 §6.6). Spelled out rather than relying on
// YearMonth.toString(), whose format is documented but would be an invisible part of the contract.
// "uuuu" is the proleptic year; "yyyy" is year-of-era and needs an era to parse strictly.
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM")

internal fun YearMonth.key(): String = format(MONTH_FORMAT)

internal fun String.toYearMonth(): YearMonth = YearMonth.parse(this, MONTH_FORMAT)

internal fun BudgetAllocationEntity.toDomain() = BudgetAllocation(
    categoryId = categoryId.toCategoryId(),
    month = month.toYearMonth(),
    amountMinor = amountMinor,
)
