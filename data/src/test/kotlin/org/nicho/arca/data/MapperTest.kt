package org.nicho.arca.data

import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.mapper.toDomain
import org.nicho.arca.data.mapper.toEntity
import org.nicho.arca.data.mapper.toYearMonth
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.model.TransferGroupId

// Plain JVM: mappers touch no Android API. A field the mapper forgets shows up here as a
// round trip that doesn't come back equal.
class MapperTest {

    @Test
    fun account_roundTrips_andStoresTheTypeCode() {
        val account = domainAccount(type = AccountType.EWALLET, isFavorite = true)

        val entity = account.toEntity(createdAt = 1, updatedAt = 2)

        assertEquals("ewallet", entity.type)
        assertEquals(account, entity.toDomain())
    }

    @Test
    fun category_roundTrips_includingBudgetFields() {
        val category = domainCategory(kind = CategoryKind.EXPENSE).copy(budgetMinor = 1_300_000, rollsOver = true)

        val entity = category.toEntity(createdAt = 1, updatedAt = 2)

        assertEquals("expense", entity.kind)
        assertEquals(category, entity.toDomain())
    }

    @Test
    fun transferLeg_roundTrips_withEveryOptionalField() {
        val tx = domainTransaction(
            accountId = AccountId.random(),
            categoryId = null,
            type = TransactionType.TRANSFER_OUT,
            amountMinor = 3_000_000_000, // past Int.MAX_VALUE
            date = Instant.parse("2026-09-30T23:59:59.999Z"),
            transferGroupId = TransferGroupId.random(),
            counterAccountId = AccountId.random(),
            note = "top-up",
        )

        val entity = tx.toEntity(createdAt = 1, updatedAt = 2)

        assertEquals("transferOut", entity.type)
        assertEquals(Instant.parse("2026-09-30T23:59:59.999Z").toEpochMilli(), entity.date)
        assertEquals(tx, entity.toDomain())
    }

    @Test
    fun ids_areStoredAsLowercaseHyphenatedUuids() {
        val id = CategoryId(java.util.UUID.fromString("0F8FAD5B-D9CB-469F-A165-70867728950E"))

        assertEquals("0f8fad5b-d9cb-469f-a165-70867728950e", id.key())
    }

    @Test
    fun month_isTheFixedWidthWireString() {
        assertEquals("2026-09", YearMonth.of(2026, 9).key())
        assertEquals(YearMonth.of(2026, 9), "2026-09".toYearMonth())
        assertEquals("0999-01", YearMonth.of(999, 1).key()) // zero-padded, so text order is month order
    }
}
