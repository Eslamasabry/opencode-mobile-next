package io.github.eslamasabry.oc.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.security.RedactingWriter
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.updates.DefaultDesktopReleaseUpdates
import io.github.eslamasabry.oc.engine.updates.ReleaseChecker
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopUpdatesViewModel
import io.github.eslamasabry.oc.feature.desktopupdates.ui.DesktopUpdatesScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitDesktopSettingsMenu
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.shell.App
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.desktopSettingsMenu
import org.jetbrains.compose.resources.stringResource

/** Shared desktop shell, with a platform Settings menu opening the common release-check screen. */
fun main() {
    val redactor = Redactor()
    AppLog.install(RedactingWriter(redactor, emptyList()), Severity.Info)
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(desktopBuildInfo, redactor)
    val updates =
        DefaultDesktopReleaseUpdates(
            ReleaseChecker(DesktopReleaseFeed()),
            desktopBuildInfo.versionCode,
            graph.dispatchers,
        )
    application {
        var showUpdates by remember { mutableStateOf(false) }
        val platform = remember { DesktopKitPlatform() }
        val updatesTitle = stringResource(Res.string.appUpdatesTitle)
        val settingsTitle = stringResource(Res.string.desktopSettingsMenu)
        val updatesVm = remember {
            DesktopUpdatesViewModel(
                updates,
                desktopBuildInfo.versionName,
                DesktopReleaseLinkPolicy(graph.dispatchers),
            ) {
                showUpdates = false
            }
        }
        Window(onCloseRequest = ::exitApplication, title = "OpenCode native (desktop preview)") {
            KitDesktopSettingsMenu(settingsTitle, updatesTitle) { showUpdates = true }
            App(graph.shell, platform, graph.metroViewModelFactory)
        }
        if (showUpdates) {
            DialogWindow(onCloseRequest = { showUpdates = false }, title = updatesTitle) {
                CompositionLocalProvider(LocalKitPlatform provides platform) {
                    KitTheme {
                        val state by updatesVm.state.collectAsState()
                        DesktopUpdatesScreen(state, updatesVm)
                    }
                }
            }
        }
    }
}
