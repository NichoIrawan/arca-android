package org.nicho.arca.domain.repository

import kotlinx.coroutines.flow.Flow

// FR-904 (theme), NFR-S02 (biometric lock). Language isn't here: AppCompat stores the per-app
// locale itself (SDD-ARC-002 §9.11).
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: ThemePreference)
    suspend fun setBiometricLock(enabled: Boolean)
}

data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val biometricLockEnabled: Boolean = false,
)

// Persisted by code, like every other enum (DD-A08).
enum class ThemePreference(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromCode(code: String?): ThemePreference = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}
