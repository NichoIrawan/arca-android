package org.nicho.arca.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.data.di.SettingsPreferences
import org.nicho.arca.domain.repository.AppSettings
import org.nicho.arca.domain.repository.SettingsStore
import org.nicho.arca.domain.repository.ThemePreference

// FR-904, NFR-S02. Theme is stored by code, like every enum (DD-A08).
internal class DataStoreSettingsStore @Inject constructor(
    @param:SettingsPreferences private val dataStore: DataStore<Preferences>,
) : SettingsStore {

    override val settings: Flow<AppSettings> = dataStore.data
        .orEmptyOnIoError()
        .map { prefs ->
            AppSettings(
                theme = ThemePreference.fromCode(prefs[THEME]),
                biometricLockEnabled = prefs[BIOMETRIC_LOCK] ?: false,
            )
        }

    override suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { it[THEME] = theme.code }
    }

    override suspend fun setBiometricLock(enabled: Boolean) {
        dataStore.edit { it[BIOMETRIC_LOCK] = enabled }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometricLock")
    }
}
