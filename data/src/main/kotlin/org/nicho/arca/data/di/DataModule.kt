package org.nicho.arca.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.dao.AccountDao
import org.nicho.arca.data.db.dao.BudgetAllocationDao
import org.nicho.arca.data.db.dao.CategoryDao
import org.nicho.arca.data.db.dao.DemoDataDao
import org.nicho.arca.data.db.dao.TransactionDao
import org.nicho.arca.data.prefs.DataStoreCaptureContextStore
import org.nicho.arca.data.prefs.DataStoreSettingsStore
import org.nicho.arca.data.prefs.PreferenceFiles
import org.nicho.arca.data.repository.RoomAccountStore
import org.nicho.arca.data.repository.RoomBudgetStore
import org.nicho.arca.data.repository.RoomCategoryStore
import org.nicho.arca.data.repository.RoomTransactionStore
import org.nicho.arca.data.seeding.DemoDataSeeder
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.AccountWriting
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CaptureContextStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.SettingsStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.repository.TransactionWriting
import org.nicho.arca.domain.repository.TransferPersisting
import org.nicho.arca.domain.seeding.DemoDataSeeding

// Two DataStore<Preferences> instances share a type, so qualifiers tell Hilt which is which.
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CaptureContextPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SettingsPreferences

// Internal, like everything it provides: :app gets these bindings through Hilt's generated
// component, but its source can't name ArcaDatabase, a DAO or a store class (DD-A02).
@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    // One instance for the whole process: the app, CaptureActivity and the widgets all read
    // the same invalidation tracker (SDD-ARC-002 §4.4).
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ArcaDatabase =
        Room.databaseBuilder(context, ArcaDatabase::class.java, ArcaDatabase.NAME).build()

    @Provides
    fun provideAccountDao(db: ArcaDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: ArcaDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: ArcaDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideBudgetAllocationDao(db: ArcaDatabase): BudgetAllocationDao = db.budgetAllocationDao()

    @Provides
    fun provideDemoDataDao(db: ArcaDatabase): DemoDataDao = db.demoDataDao()

    // DataStore requires exactly one instance per file in a process, hence @Singleton.
    @Provides
    @Singleton
    @CaptureContextPreferences
    fun provideCaptureContextDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        preferencesDataStore(context, PreferenceFiles.CAPTURE_CONTEXT)

    @Provides
    @Singleton
    @SettingsPreferences
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        preferencesDataStore(context, PreferenceFiles.SETTINGS)

    // Stores only need "now" as an instant for createdAt/updatedAt. Time zones matter only for
    // period boundaries, which PeriodClock owns (DD-A18), so UTC keeps the zone out of here.
    // This is the app's only Clock binding: don't add another in :app.
    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    private fun preferencesDataStore(context: Context, name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // A corrupt file holds nothing worth refusing to start over.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile(name) },
        )
}

// Binds each implementation to the domain interfaces it satisfies. :app asks for the interface.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class StoreBindings {

    @Binds
    abstract fun transactionReading(store: RoomTransactionStore): TransactionReading

    @Binds
    abstract fun transactionWriting(store: RoomTransactionStore): TransactionWriting

    @Binds
    abstract fun transferPersisting(store: RoomTransactionStore): TransferPersisting

    @Binds
    abstract fun accountReading(store: RoomAccountStore): AccountReading

    @Binds
    abstract fun accountWriting(store: RoomAccountStore): AccountWriting

    @Binds
    abstract fun categoryStore(store: RoomCategoryStore): CategoryStore

    @Binds
    abstract fun budgetStore(store: RoomBudgetStore): BudgetStore

    @Binds
    abstract fun captureContextStore(store: DataStoreCaptureContextStore): CaptureContextStore

    @Binds
    abstract fun settingsStore(store: DataStoreSettingsStore): SettingsStore

    @Binds
    abstract fun demoDataSeeding(seeder: DemoDataSeeder): DemoDataSeeding
}
