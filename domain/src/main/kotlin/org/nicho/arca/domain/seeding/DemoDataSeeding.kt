package org.nicho.arca.domain.seeding

// FR-902 · SDD-ARC-002 §8.5. Settings shows the entry only when BuildConfig.DEMO_DATA_ENABLED.
interface DemoDataSeeding {
    /**
     * Fills the database with realistic, deterministic demo data.
     * Refuses (DatabaseNotEmpty) if the user already has accounts or transactions, unless
     * [replaceExisting] is true. Pass that only after the user explicitly confirmed losing their data.
     */
    suspend fun seed(config: DemoDataConfig = DemoDataConfig(), replaceExisting: Boolean = false): SeedResult
}

/** The defaults are the §8.5 baseline; the §14.3 runs use 24/36 months and 5/20/40 accounts. */
data class DemoDataConfig(
    val months: Int = 12,
    val accounts: Int = 20,
    val transactionsPerDay: Int = 25,
    val randomSeed: Int = 42,
)

sealed interface SeedResult {
    data class Seeded(val accounts: Int, val categories: Int, val transactions: Int) : SeedResult
    data object DatabaseNotEmpty : SeedResult
}
