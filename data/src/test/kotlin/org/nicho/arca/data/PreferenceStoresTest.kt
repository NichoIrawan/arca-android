package org.nicho.arca.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nicho.arca.data.prefs.DataStoreCaptureContextStore
import org.nicho.arca.data.prefs.DataStoreSettingsStore
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.repository.AppSettings
import org.nicho.arca.domain.repository.CaptureContext
import org.nicho.arca.domain.repository.ThemePreference

/**
 * In memory, not file-backed: these tests are about our keys and mapping, not DataStore's file
 * I/O. A file-backed DataStore also can't do a second write on a Windows JVM: it renames its temp
 * file over the existing one, and java.io.File.renameTo won't replace a file on Windows. It does
 * on Android, which is Linux.
 */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class PreferenceStoresTest {

    @Test
    fun captureContext_startsEmpty_thenRemembers() = runTest {
        val store = DataStoreCaptureContextStore(InMemoryPreferences())
        assertEquals(CaptureContext(lastAccountId = null, lastDate = null), store.context.first())

        val account = AccountId.random()
        val date = Instant.ofEpochMilli(T0)
        store.remember(account, date)

        assertEquals(CaptureContext(account, date), store.context.first())
    }

    @Test
    fun captureContext_ignoresAnUnreadableAccountId() = runTest {
        val dataStore = InMemoryPreferences()
        dataStore.edit { it[stringPreferencesKey("lastAccountId")] = "not-a-uuid" }

        assertNull(DataStoreCaptureContextStore(dataStore).context.first().lastAccountId)
    }

    @Test
    fun settings_defaultToSystemThemeAndNoLock_thenPersistByCode() = runTest {
        val dataStore = InMemoryPreferences()
        val store = DataStoreSettingsStore(dataStore)
        assertEquals(AppSettings(), store.settings.first())

        store.setTheme(ThemePreference.DARK)
        store.setBiometricLock(true)

        assertEquals(AppSettings(ThemePreference.DARK, biometricLockEnabled = true), store.settings.first())
        assertEquals("dark", dataStore.data.first()[stringPreferencesKey("theme")])
    }
}
