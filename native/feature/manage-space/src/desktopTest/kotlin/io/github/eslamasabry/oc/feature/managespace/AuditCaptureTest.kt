package io.github.eslamasabry.oc.feature.managespace

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.eslamasabry.oc.engine.lifecycle.AppStorageFacts
import io.github.eslamasabry.oc.engine.lifecycle.ManageSpaceState
import io.github.eslamasabry.oc.engine.lifecycle.ProjectFacts
import io.github.eslamasabry.oc.engine.lifecycle.StorageExport
import io.github.eslamasabry.oc.engine.lifecycle.StorageNotice
import io.github.eslamasabry.oc.feature.managespace.presentation.ManageSpaceActions
import io.github.eslamasabry.oc.feature.managespace.ui.ManageSpaceScreen
import kotlin.test.Test

/** Audit capture only: the Manage space page in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Taps : ManageSpaceActions {
        override fun back() {}
        override fun refresh() {}
        override fun includePrivate(value: Boolean) {}
        override fun export() {}
        override fun stopExport() {}
        override fun clearCache() {}
        override fun deleteAll() {}
        override fun dismiss() {}
    }

    private val facts =
        AppStorageFacts(
            listOf(ProjectFacts("shop", 2097152, 1, 0), ProjectFacts("blog", 1048576, 1, 0)),
            true,
            943718400,
            0,
            2,
        )

    private val bigFacts =
        AppStorageFacts(
            listOf(
                ProjectFacts("a-project-with-an-extremely-long-name-that-keeps-going-and-going", 5_368_709_120, 4200, 12),
                ProjectFacts("blog", 1048576, 1, 3),
                ProjectFacts("shop", 2097152, 20, 0),
            ),
            true,
            2_147_483_648,
            52_428_800,
            7,
        )

    private fun shot(name: String, state: ManageSpaceState, setup: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {}) =
        Audit.guard("manage-space__manage-space__${name}__build") {
            Audit.shoot("manage-space", "manage-space", name, setup = setup) { ManageSpaceScreen(state, Taps) }
        }

    @Test
    fun manageSpace() {
        shot("loading", ManageSpaceState())
        shot("measured", ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880))
        shot("big-long-names", ManageSpaceState(loading = false, facts = bigFacts, cacheBytes = 734_003_200))
        shot("nothing-installed", ManageSpaceState(loading = false, facts = AppStorageFacts(emptyList(), false, 0, 0, 0), cacheBytes = 0))
        shot("include-private", ManageSpaceState(loading = false, facts = bigFacts, cacheBytes = 5242880, includePrivate = true))
        shot("exporting", ManageSpaceState(loading = false, facts = facts, busy = true, exporting = true, bytesDone = 1_572_864))
        shot("busy", ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880, busy = true))
        shot("measure-failed", ManageSpaceState(loading = false, failed = true))
        shot("exported", ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880, notice = StorageNotice.Exported, export = StorageExport(3_145_728, 2, false)))
        shot("cache-cleared", ManageSpaceState(loading = false, facts = facts, cacheBytes = 0, notice = StorageNotice.CacheCleared, cacheFreed = 5_242_880))
        shot("cache-failed", ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880, notice = StorageNotice.CacheFailed))
        shot("export-failed", ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880, notice = StorageNotice.ExportFailed))
        shot("export-stopped", ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880, notice = StorageNotice.ExportStopped))
        shot("delete-failed", ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880, notice = StorageNotice.DeleteFailed))
        shot(
            "delete-confirm",
            ManageSpaceState(loading = false, facts = bigFacts, cacheBytes = 5242880),
            setup = {
                onNodeWithText("Delete everything").performScrollTo().performClick()
                waitForIdle()
                mainClock.advanceTimeBy(500)
            },
        )
    }
}
