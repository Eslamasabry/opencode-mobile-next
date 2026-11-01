package io.github.eslamasabry.oc.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.security.RedactingWriter
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.shell.App

/** Slice 0: a window that proves the desktop target runs the shared shell. The kit gallery replaces it in slice 1. */
fun main() {
    val redactor = Redactor()
    AppLog.install(RedactingWriter(redactor, emptyList()), Severity.Info)
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(desktopBuildInfo, redactor)
    application {
        Window(onCloseRequest = ::exitApplication, title = "OpenCode native (desktop preview)") {
            App(graph.shell, DesktopKitPlatform(), graph.metroViewModelFactory)
        }
    }
}
