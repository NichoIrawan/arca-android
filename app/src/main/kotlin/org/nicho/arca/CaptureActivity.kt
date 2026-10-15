package org.nicho.arca

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.nicho.arca.designsystem.ArcaTheme
import org.nicho.arca.domain.repository.AppSettings
import org.nicho.arca.domain.repository.SettingsStore
import org.nicho.arca.feature.capture.CaptureRequest
import org.nicho.arca.feature.capture.CaptureSheet
import org.nicho.arca.feature.capture.CaptureViewModel

/**
 * SDD-ARC-002 §10.3, DD-A12: a translucent host for the capture sheet, opened by widgets,
 * launcher shortcuts, the Quick Settings tile and `arca://capture` links. It composes only the
 * sheet, not the tab shell, so capture from outside the app opens fast (NFR-U02).
 *
 * Pre-fill only (DD-A13): nothing here writes; the user taps Save. Finishes on save or dismiss,
 * returning to wherever the user was.
 */
@AndroidEntryPoint
class CaptureActivity : AppCompatActivity() {

    @Inject lateinit var settingsStore: SettingsStore

    private val captureViewModel: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val request = CaptureRequest.fromUri(intent?.data)
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
            ArcaTheme(theme = settings.theme) {
                CaptureSheet(remember { request }, captureViewModel, onDismiss = ::finish)
            }
        }
    }
}
