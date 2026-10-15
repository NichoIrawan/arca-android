package org.nicho.arca

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.nicho.arca.designsystem.ArcaTheme
import org.nicho.arca.domain.repository.AppSettings
import org.nicho.arca.domain.repository.SettingsStore
import org.nicho.arca.navigation.rememberArcaAppState
import org.nicho.arca.ui.ArcaApp

/**
 * The single-activity shell. AppCompatActivity (rather than ComponentActivity) so the in-app
 * language switch works on Android 10–12 as well (LanguageController).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
            ArcaTheme(theme = settings.theme) {
                ArcaApp(rememberArcaAppState())
            }
        }
    }
}
