package io.github.eslamasabry.oc.feature.computer

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.feature.computer.ui.ComputerScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Audit capture only: the computer page in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Taps : ComputerActions {
        override fun back() {}
        override fun askUpdate() {}
        override fun cancelUpdate() {}
        override fun confirmUpdate() {}
        override fun reloadImports() {}
        override fun import(key: String) {}
    }

    private val long = ImportItem(
        "claude/h3",
        "A conversation title that is much too long to fit on one line of a phone and has to wrap or be cut gracefully",
        "/work/a/very/deeply/nested/project/folder/that/keeps/going",
        "3 weeks ago",
    )
    private val list = ImportList("/work/acme", persistentListOf(NEWER, OLDER, long), alreadyHere = 2)
    private val base = ComputerUi(name = "Studio Mac", version = "1.4.0", imports = ImportPhase.Ready(list))

    private fun shot(name: String, ui: ComputerUi) =
        Audit.guard("computer__computer__${name}__build") {
            Audit.shoot("computer", "computer", name) { KitSheetHost { ComputerScreen(ui, Taps) } }
        }

    @Test
    fun computer() {
        shot("idle-imports", base)
        shot("loading", ComputerUi(name = "Studio Mac"))
        shot("unknown-version", base.copy(version = null))
        shot("long-name", base.copy(name = "A computer with an extremely long name that goes on and on and on"))
        shot("confirming", base.copy(confirming = true))
        shot("updating-starting", base.copy(updating = true, step = UpdateStep.Starting))
        shot("updating-downloading", base.copy(updating = true, step = UpdateStep.Downloading))
        shot("updating-installing", base.copy(updating = true, step = UpdateStep.Installing))
        shot("updating-finishing", base.copy(updating = true, step = UpdateStep.Finishing))
        shot("update-done", base.copy(update = UpdateLine.Done("1.5.0")))
        shot("update-current", base.copy(update = UpdateLine.Current))
        shot("update-failed", base.copy(update = UpdateLine.Failed(null)))
        shot("update-failed-offline", base.copy(update = UpdateLine.Failed(Failure.Network(NetworkWhy.Offline))))
        shot("update-dropped", base.copy(update = UpdateLine.Dropped))
        shot("imports-empty", base.copy(imports = ImportPhase.Ready(ImportList("/work/acme", persistentListOf(), 0))))
        shot("imports-loading", base.copy(imports = ImportPhase.Loading))
        shot("imports-failed", base.copy(imports = ImportPhase.Failed(Failure.Network(NetworkWhy.Reset))))
        shot("importing", base.copy(importing = NEWER.key))
        shot("import-failed", base.copy(importFailed = Failure.Network(NetworkWhy.Offline)))
    }
}
