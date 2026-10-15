package org.nicho.arca.feature.settings

import org.nicho.arca.domain.repository.AppSettings
import org.nicho.arca.locale.AppLanguage

/** The outcome of "Load sample data", shown once. */
enum class DemoResult { LOADED, NEEDS_CONFIRMATION, FAILED }

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val demoDataAvailable: Boolean = false,  // BuildConfig.DEMO_DATA_ENABLED (FR-902)
    val isSeeding: Boolean = false,
    val demoResult: DemoResult? = null,
    val versionName: String = "",
)
