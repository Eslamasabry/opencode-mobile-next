package io.github.eslamasabry.oc.feature.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptActions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptCrashRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptErrorRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptExitRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptExits
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptNotice
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptPerfRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptPreview
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptUi
import io.github.eslamasabry.oc.feature.diagnostics.presentation.ReportVersions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.StaleFailureGate
import io.github.eslamasabry.oc.feature.diagnostics.ui.KeptScreen
import io.github.eslamasabry.oc.feature.diagnostics.ui.StaleFailurePrompt
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test

/** Audit capture only: the App diagnostics page and the stale-failure line. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Taps : KeptActions {
        override fun onBack() {}
        override fun onCrashCapture(on: Boolean) {}
        override fun onPreviewCrashReport() {}
        override fun onShareCrashReport() {}
        override fun onClosePreview() {}
        override fun onDeleteCrashReports() {}
        override fun onConfirmDelete() {}
        override fun onClearErrors() {}
        override fun onConfirmClear() {}
        override fun onCancelConfirm() {}
        override fun onRetryExits() {}
        override fun onDismissNotice() {}
    }

    private fun kept(name: String, ui: KeptUi) =
        Audit.guard("diagnostics__kept__${name}__build") {
            Audit.shoot("diagnostics", "kept", name) { KitSheetHost { KeptScreen(ui, Taps) } }
        }

    private val longMessage = "Bad state: the connection controller was asked to reconnect while a previous reconnect was still running " +
        "and the profile with a very long name could not be found anywhere in the store"

    private val exits = listOf(
        KeptExitRow(ExitKind.Crash, "Yesterday 21:42"),
        KeptExitRow(ExitKind.LowMemory, "Yesterday 18:10"),
        KeptExitRow(ExitKind.MemoryLimit, "Yesterday 16:00"),
        KeptExitRow(ExitKind.ForceStop, "Today 08:00"),
        KeptExitRow(ExitKind.Update, "Today 09:00"),
        KeptExitRow(ExitKind.Normal, "Today 09:30"),
        KeptExitRow(ExitKind.Killed, "Today 10:15"),
    )

    private val errors = listOf(
        KeptErrorRow(1, "Today 12:00", "connection", longMessage, "at a.b.C.run(C.kt:10)\nat a.b.D.go(D.kt:22)", occurrences = 3),
        KeptErrorRow(2, "Today 11:00", "render", "Overflow by 12 pixels", "at x.Y.z(Y.kt:1)"),
    )

    private val perf = listOf(
        KeptPerfRow("Open conversation", 14, 0, "120 ms", "380 ms", "910 ms"),
        KeptPerfRow("Send message with a very long step name that wraps", 6, 2, "1.2 s", "3.4 s", "6.1 s"),
    )

    @Test
    fun keptPage() {
        kept("loading", KeptUi())
        kept("off", KeptUi(crashAvailable = true, exits = KeptExits.Ready(emptyList(), 0)))
        kept("unavailable", KeptUi(crashAvailable = false, exits = KeptExits.Unsupported))
        kept(
            "full",
            KeptUi(
                crashAvailable = true,
                crashOn = true,
                crashRows = listOf(
                    KeptCrashRow("Today 12:00", "Invalid state"),
                    KeptCrashRow("Today 10:00", "Not responding", CrashSource.Anr),
                    KeptCrashRow("Yesterday 09:00", "Native signal", CrashSource.Native),
                ),
                exits = KeptExits.Ready(exits, 2),
                errorsKept = errors.size,
                errors = errors,
                perf = perf,
            ),
        )
        kept("exits-failed", KeptUi(crashAvailable = true, exits = KeptExits.Failed))
        kept("exits-unsupported", KeptUi(crashAvailable = true, exits = KeptExits.Unsupported))
        kept("errors-only", KeptUi(crashAvailable = true, exits = KeptExits.Ready(emptyList(), 0), errorsKept = 2, errors = errors))
        kept("perf-only", KeptUi(crashAvailable = true, exits = KeptExits.Ready(emptyList(), 0), perf = perf))
        kept("preview", KeptUi(crashAvailable = true, crashOn = true, preview = KeptPreview("OpenCode Mobile crash report\nCaptured error categories and times only.\n\n12:00 Invalid state", 2, 256)))
        kept("preview-sharing", KeptUi(crashAvailable = true, crashOn = true, preview = KeptPreview("Safe report", 1, 2048), sharing = true))
        kept("confirm-delete", KeptUi(crashAvailable = true, crashOn = true, crashRows = listOf(KeptCrashRow("Today 12:00", "Invalid state")), confirmDelete = true))
        kept("confirm-clear", KeptUi(crashAvailable = true, errorsKept = 2, errors = errors, exits = KeptExits.Ready(emptyList(), 0), confirmClear = true))
        for (n in KeptNotice.entries) {
            kept("notice-${n.name.lowercase()}", KeptUi(crashAvailable = true, exits = KeptExits.Ready(exits, 0), notice = n))
        }
    }

    private class Versions(val running: String?, val latest: String?) : ReportVersions {
        override fun running(): String? = running
        override fun latestRelease(): String? = latest
        override fun openUpdate() {}
    }

    private fun stale(name: String, running: String?, latest: String?, saved: String) =
        Audit.guard("diagnostics__stale-failure__${name}__build") {
            Audit.shoot("diagnostics", "stale-failure", name) {
                val gate = remember { StaleFailureGate(Versions(running, latest)).also { it.request(saved) {} } }
                KitSheetHost {
                    val state by gate.state.collectAsState()
                    StaleFailurePrompt(state, gate)
                }
            }
        }

    @Test
    fun staleFailure() {
        stale("update-available", running = "2.1.4", latest = "3.0.0", saved = "2.1.1")
        stale("running-newer", running = "2.1.4", latest = "2.1.4", saved = "2.1.1")
    }
}
