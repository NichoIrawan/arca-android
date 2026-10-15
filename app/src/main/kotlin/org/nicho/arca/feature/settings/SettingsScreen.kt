package org.nicho.arca.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.domain.repository.ThemePreference
import org.nicho.arca.ui.components.DetailTopBar
import org.nicho.arca.locale.AppLanguage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.designsystem.Mascot
import org.nicho.arca.designsystem.MascotMood
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.pushArrival
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.SwitchRow

@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenReports: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        DetailTopBar(onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .pushArrival()
                .padding(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.space5)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = ArcaDesignSystem.typography.title,
                color = colors.ink,
                modifier = Modifier
                    .padding(top = spacing.space5)
                    .semantics { heading() },
            )

            SectionLabel(stringResource(R.string.settings_appearance), Modifier.padding(top = spacing.space7, bottom = spacing.space3))
            SegmentedControl(
                options = listOf(
                    ThemePreference.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemePreference.DARK to stringResource(R.string.settings_theme_dark),
                    ThemePreference.SYSTEM to stringResource(R.string.settings_theme_system),
                ),
                selected = state.settings.theme,
                onSelect = viewModel::onThemeChange,
            )

            // Not on the board: the app ships in English and Indonesian, so the choice lives here.
            SectionLabel(stringResource(R.string.settings_language), Modifier.padding(top = spacing.space6, bottom = spacing.space3))
            SegmentedControl(
                options = listOf(
                    AppLanguage.ENGLISH to "English",
                    AppLanguage.INDONESIAN to "Indonesia",
                    AppLanguage.SYSTEM to stringResource(R.string.settings_theme_system),
                ),
                selected = state.language,
                onSelect = viewModel::onLanguageChange,
            )

            SectionLabel(stringResource(R.string.settings_security), Modifier.padding(top = spacing.space6, bottom = spacing.space2))
            SwitchRow(
                stringResource(R.string.settings_fingerprint),
                state.settings.biometricLockEnabled,
                viewModel::onBiometricLockChange,
            )

            SectionLabel(stringResource(R.string.settings_data), Modifier.padding(top = spacing.space6, bottom = spacing.space2))
            SettingRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_where), onClick = onOpenReports)
            if (state.demoDataAvailable) {
                SettingRow(
                    stringResource(R.string.settings_demo_data),
                    if (state.isSeeding) stringResource(R.string.settings_demo_loading) else stringResource(R.string.settings_demo_debug_only),
                    onClick = { viewModel.onLoadDemoData(replaceExisting = false) },
                )
            }

            SectionLabel(stringResource(R.string.settings_mascot), Modifier.padding(top = spacing.space6, bottom = spacing.space3))
            Row(Modifier.padding(vertical = spacing.space4), horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
                listOf(MascotMood.CALM to R.string.settings_mascot_calm, MascotMood.CONCERNED to R.string.settings_mascot_alert).forEach { (mood, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        Mascot(mood, size = 44.dp)
                        Text(stringResource(label), style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
                    }
                }
            }
            Hairline()
            Text(
                stringResource(R.string.settings_mascot_help),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = spacing.space2),
            )

            SectionLabel(stringResource(R.string.settings_about), Modifier.padding(top = spacing.space6, bottom = spacing.space2))
            Row(Modifier.padding(vertical = spacing.space4), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_version), style = ArcaDesignSystem.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
                Text(state.versionName, style = ArcaDesignSystem.typography.body, color = colors.inkTertiary)
            }
            Hairline()
        }
    }

    when (state.demoResult) {
        DemoResult.NEEDS_CONFIRMATION -> AlertDialog(
            onDismissRequest = viewModel::onDemoResultShown,
            title = { Text(stringResource(R.string.settings_demo_confirm_title)) },
            text = { Text(stringResource(R.string.settings_demo_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDemoResultShown()
                    viewModel.onLoadDemoData(replaceExisting = true)
                }) { Text(stringResource(R.string.settings_demo_confirm_replace), color = colors.negative) }
            },
            dismissButton = { TextButton(onClick = viewModel::onDemoResultShown) { Text(stringResource(R.string.action_cancel)) } },
        )
        DemoResult.LOADED, DemoResult.FAILED -> AlertDialog(
            onDismissRequest = viewModel::onDemoResultShown,
            text = {
                Text(stringResource(if (state.demoResult == DemoResult.LOADED) R.string.settings_demo_loaded else R.string.issue_save_failed))
            },
            confirmButton = { TextButton(onClick = viewModel::onDemoResultShown) { Text("OK") } },
        )
        null -> Unit
    }
}

/** A settings row: a title, a quiet line under it, and a chevron when it leads somewhere. */
@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
    val colors = ArcaDesignSystem.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick, pressedScale = 1f) else Modifier)
            .padding(vertical = ArcaDesignSystem.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ArcaDesignSystem.typography.body, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClick != null) ArcaIcon(ArcaIcons.chevronRight, tint = colors.inkTertiary, size = 18.dp)
    }
    Hairline()
}
