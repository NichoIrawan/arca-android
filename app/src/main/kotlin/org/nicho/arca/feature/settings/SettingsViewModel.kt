package org.nicho.arca.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nicho.arca.BuildConfig
import org.nicho.arca.domain.repository.SettingsStore
import org.nicho.arca.domain.repository.ThemePreference
import org.nicho.arca.domain.seeding.DemoDataSeeding
import org.nicho.arca.domain.seeding.SeedResult
import org.nicho.arca.locale.AppLanguage
import org.nicho.arca.locale.LanguageController

/** Settings (FR-904 theme, NFR-S02 lock, FR-902 demo data, the in-app language switch). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val demoData: DemoDataSeeding,
) : ViewModel() {

    private val local = MutableStateFlow(
        SettingsUiState(
            language = LanguageController.current(),
            demoDataAvailable = BuildConfig.DEMO_DATA_ENABLED,
            versionName = BuildConfig.VERSION_NAME,
        ),
    )

    val state: StateFlow<SettingsUiState> = combine(settingsStore.settings, local) { settings, ui -> ui.copy(settings = settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onThemeChange(theme: ThemePreference) {
        viewModelScope.launch { settingsStore.setTheme(theme) }
    }

    fun onLanguageChange(language: AppLanguage) {
        local.update { it.copy(language = language) }
        LanguageController.set(language) // recreates the activity in the new language
    }

    fun onBiometricLockChange(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBiometricLock(enabled) }
    }

    /**
     * FR-902. Refuses over existing data; [replaceExisting] is passed only after the user
     * confirmed in the dialog that their data will be replaced.
     */
    fun onLoadDemoData(replaceExisting: Boolean) {
        if (!BuildConfig.DEMO_DATA_ENABLED || local.value.isSeeding) return
        local.update { it.copy(isSeeding = true, demoResult = null) }
        viewModelScope.launch {
            val result = runCatching { demoData.seed(replaceExisting = replaceExisting) }.getOrNull()
            local.update {
                it.copy(
                    isSeeding = false,
                    demoResult = when (result) {
                        is SeedResult.Seeded -> DemoResult.LOADED
                        SeedResult.DatabaseNotEmpty -> DemoResult.NEEDS_CONFIRMATION
                        null -> DemoResult.FAILED
                    },
                )
            }
        }
    }

    fun onDemoResultShown() = local.update { it.copy(demoResult = null) }
}
