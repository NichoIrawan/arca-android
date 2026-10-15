package org.nicho.arca.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import org.nicho.arca.undo.UndoCoordinator
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.designsystem.floatShadow
import org.nicho.arca.feature.capture.CaptureSheet
import org.nicho.arca.feature.capture.CaptureViewModel
import org.nicho.arca.navigation.ArcaAppState
import org.nicho.arca.navigation.ArcaNavHost
import org.nicho.arca.navigation.TopLevelDestination
import org.nicho.arca.ui.components.pressable
import kotlin.time.Duration.Companion.milliseconds

/**
 * The app shell (SDD-ARC-002 §9.1), drawn as the prototype draws it: content edge to edge, a
 * floating pill tab bar with the add button merged into its centre (OPEN-A12 resolved in the
 * prototype's favour, 2026-10-14). The bar shows on tab roots only; pushed screens get the
 * whole height.
 */
@Composable
fun ArcaApp(appState: ArcaAppState) {
    val shell: ShellViewModel = hiltViewModel()
    val pending by shell.pendingDeletion.collectAsStateWithLifecycle()
    val currentTab = appState.currentTab()

    val deletedOne = stringResource(R.string.undo_transaction_deleted)
    val transferDeleted = pending?.let { pluralStringResource(R.plurals.undo_transfer_deleted, it.memberCount, it.memberCount) }

    Box(
        Modifier
            .fillMaxSize()
            .background(ArcaDesignSystem.colors.surface),
    ) {
        ArcaNavHost(appState, Modifier.fillMaxSize())

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            // DD-A11: the banner stays for exactly the coordinator's 5-second window.
            AnimatedVisibility(
                visible = pending != null,
                enter = fadeIn(tween(360)) + slideInVertically(tween(360, easing = ArcaDesignSystem.motion.easeOut)) { it / 4 },
                exit = fadeOut(tween(180)),
            ) {
                val deletion = pending
                if (deletion != null) {
                    UndoBanner(
                        message = if (deletion.transferGroupId != null) transferDeleted.orEmpty() else deletedOne,
                        token = deletion.token,
                        onUndo = shell::undo,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = if (currentTab != null) 14.dp else 22.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = currentTab != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                FloatingTabBar(
                    current = currentTab,
                    onTab = appState::navigateToTab,
                    onAdd = { appState.openCapture() },
                )
            }
        }
    }

    appState.captureRequest?.let { request ->
        val capture: CaptureViewModel = hiltViewModel()
        CaptureSheet(request, capture, onDismiss = appState::closeCapture)
    }
}

/** Overview · Transactions · [+] · Budget · Accounts. 64dp pill, 16dp from the sides, 22dp up. */
@Composable
internal fun FloatingTabBar(
    current: TopLevelDestination?,
    onTab: (TopLevelDestination) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = ArcaDesignSystem.colors
    val shapes = ArcaDesignSystem.shapes
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 22.dp, top = 8.dp)
            .fillMaxWidth()
            .height(64.dp)
            // Shape the fill and the shadow, but don't clip: the + button rises 9dp out of the bar
            // and casts its own shadow, and a clip would cut both off.
            .floatShadow(ArcaDesignSystem.shadow, shapes.pill)
            .background(colors.surfaceRaised, shapes.pill)
            .padding(horizontal = ArcaDesignSystem.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (left, right) = TopLevelDestination.entries.partition { it.ordinal < 2 }
        left.forEach { TabItem(it, it == current, onTab, Modifier.weight(1f)) }
        Box(
            Modifier
                .padding(horizontal = ArcaDesignSystem.spacing.space1)
                .offset(y = (-9).dp)
                .size(54.dp)
                .floatShadow(ArcaDesignSystem.shadow, shapes.pill)
                .background(colors.accent, shapes.pill)
                .pressable(onAdd, pressedScale = 0.9f, onClickLabel = stringResource(R.string.action_add_transaction)),
            contentAlignment = Alignment.Center,
        ) {
            ArcaIcon(ArcaIcons.add, tint = colors.accentOn, size = 23.dp, contentDescription = stringResource(R.string.action_add_transaction))
        }
        right.forEach { TabItem(it, it == current, onTab, Modifier.weight(1f)) }
    }
}

@Composable
private fun TabItem(
    tab: TopLevelDestination,
    selected: Boolean,
    onTab: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) ArcaDesignSystem.colors.ink else ArcaDesignSystem.colors.inkSecondary
    Column(
        modifier
            .semantics { this.selected = selected }
            .pressable({ onTab(tab) }, pressedScale = 0.9f, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ArcaIcon(tab.icon, tint = color)
        Text(stringResource(tab.label), color = color, fontSize = 10.sp, letterSpacing = 0.1.sp, maxLines = 1)
    }
}

/**
 * "Transaction deleted · UNDO" (board 28): an accent bar 48dp tall, sitting just above the tab bar,
 * with a thin line along its foot that runs out over the 5-second undo window.
 */
@Composable
private fun UndoBanner(message: String, token: Long, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val left = remember(token) { Animatable(1f) }
    LaunchedEffect(token) {
        delay(360.milliseconds)
        left.animateTo(0f, tween((UndoCoordinator.WINDOW_MILLIS - 360).toInt(), easing = LinearEasing))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .floatShadow(ArcaDesignSystem.shadow, ArcaDesignSystem.shapes.small)
            .background(colors.accent, ArcaDesignSystem.shapes.small)
            .clip(ArcaDesignSystem.shapes.small)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.fillMaxSize().padding(start = ArcaDesignSystem.spacing.space4), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = ArcaDesignSystem.typography.subhead, color = colors.accentOn, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                stringResource(R.string.action_undo).uppercase(),
                style = ArcaDesignSystem.typography.subhead.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.06.em),
                color = colors.accentOn,
                modifier = Modifier
                    .fillMaxHeight()
                    .pressable(onUndo)
                    .padding(horizontal = ArcaDesignSystem.spacing.space4)
                    .wrapContentHeight(),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(left.value)
                .height(2.dp)
                .background(colors.accentOn.copy(alpha = 0.45f)),
        )
    }
}
