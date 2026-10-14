package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.repository.RoomAccountStore
import org.nicho.arca.data.repository.RoomCategoryStore
import org.nicho.arca.data.seeding.DemoDataSeeder
import org.nicho.arca.domain.seeding.DemoDataConfig
import org.nicho.arca.domain.seeding.SeedResult

// FR-902. Small config so the test is quick; the generator test covers the data's shape.
@RunWith(AndroidJUnit4::class)
class DemoDataSeederTest {

    private lateinit var db: ArcaDatabase
    private lateinit var seeder: DemoDataSeeder
    private val config = DemoDataConfig(months = 2, accounts = 5)

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        seeder = DemoDataSeeder(db, TestClock(millis = 1_760_400_000_000))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun freshInstall_isSeeded_replacingTheDefaultCategories() = runTest {
        RoomCategoryStore(db, db.categoryDao(), TestClock()).seedDefaultsIfNeeded() // as at first launch

        val result = seeder.seed(config) as SeedResult.Seeded

        assertEquals(5, result.accounts)
        assertEquals(result.transactions, db.transactionDao().count())
        assertTrue(result.transactions > 0)
        val categories = db.categoryDao().observeAll(includeArchived = true).first()
        assertEquals(1, categories.count { it.isSystemLocked }) // the old Fees went, not doubled
    }

    @Test
    fun existingUserData_isRefused_andLeftAlone() = runTest {
        val mine = domainAccount(name = "Mine")
        RoomAccountStore(db, db.accountDao(), TestClock()).insert(mine)

        assertEquals(SeedResult.DatabaseNotEmpty, seeder.seed(config))

        assertEquals(0, db.transactionDao().count())
        assertEquals(listOf("Mine"), db.accountDao().observeAll(includeArchived = true).first().map { it.name })
    }

    @Test
    fun replaceExisting_wipesAndSeeds() = runTest {
        seeder.seed(config)

        val result = seeder.seed(config, replaceExisting = true) as SeedResult.Seeded

        assertEquals(result.transactions, db.transactionDao().count()) // not doubled
    }
}
