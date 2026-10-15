package org.nicho.arca.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The in-app language choice (Settings). SYSTEM follows the device's language. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    INDONESIAN("in"),
}

/**
 * Switches the app's language without touching the device's (SDD-ARC-002 §9.11). AppCompat stores
 * the choice itself, and on Android 13+ it also appears in the system's per-app language settings.
 * The activity recreates, so every string resource, date and number format follows.
 */
object LanguageController {

    fun current(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tag.isEmpty() -> AppLanguage.SYSTEM
            tag.startsWith("in") || tag.startsWith("id") -> AppLanguage.INDONESIAN
            else -> AppLanguage.ENGLISH
        }
    }

    fun set(language: AppLanguage) {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
