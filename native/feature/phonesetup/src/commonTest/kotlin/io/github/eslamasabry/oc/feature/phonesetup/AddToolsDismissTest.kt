@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneySource
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyViewModel
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.ui.JourneyBody
import io.github.eslamasabry.oc.feature.phonesetup.ui.journeyBody
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S9 (gate): the Add tools page draws nothing under its sheet, so closing the sheet must leave that
 * page too: the person lands back on the page they came from (This phone), never on an empty page
 * with only a "This phone" bar.
 */
class AddToolsDismissTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private class Source : SetupJourneySource {
        override val progress =
            MutableStateFlow(SetupProgress(SetupState.Done, emptyList(), 1.0, jobId = "setup-1"))
        override val toolsChanged = MutableStateFlow(0)
        override val selection = MutableStateFlow(emptySet<String>())

        override fun details(progress: SetupProgress): Redacted? = null

        override suspend fun restore(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun options(adding: Boolean): Outcome<SetupOptions> =
            Outcome.Ok(SetupOptions(emptyList(), emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10_000_000_000)))

        override suspend fun choose(ids: Set<String>): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun run(ids: Set<String>, adding: Boolean): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun resume(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun cancel(jobId: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun removeVoice(): Outcome<Unit> = Outcome.Ok(Unit)

        override fun openStorage(): Boolean = true
    }

    private class Nav : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        var backs = 0

        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            backs++
        }
    }

    @Test
    fun `S9 closing the Add tools sheet leaves its empty page and returns where it was opened`() {
        val nav = Nav()
        val vm = SetupJourneyViewModel(PhoneSetupRoute.Customize(adding = true), Source(), nav)
        // The page under the sheet is empty for Add tools on an installed phone ...
        assertTrue(vm.state.value.customizing && vm.state.value.adding)
        assertEquals(JourneyBody.Nothing, journeyBody(vm.state.value.copy(checking = false)))
        // ... so the sheet's dismissal (KitSheet onDismissRequest = actions::back) pops that whole page:
        // one step back, nothing opened in its place.
        vm.back()
        assertEquals(1, nav.backs)
        assertTrue(nav.opened.isEmpty())
    }
}
