package io.github.eslamasabry.oc.feature.updates

import androidx.lifecycle.ViewModelStore
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateLinks
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesAction
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesLine
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Updates page's ViewModel over a real AppUpdates engine and fake ports. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {
    private class Links : UpdateLinks {
        val opened = mutableListOf<String>()

        override suspend fun openReleasePage(url: String): Outcome<Unit> {
            opened += url
            return Outcome.Ok(Unit)
        }
    }

    private class Navigator : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)
        override fun open(route: AppRoute) {
            top.value = route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) {
            top.value = root
        }

        override fun back() = Unit
    }

    private val clock = TestClock()
    private val links = Links()
    private val nav = Navigator()

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    /** Engine commands run on an unconfined app scope that ends with the test. */
    private fun TestScope.appScope() = AppCoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.started(rig: UpdateRig): UpdatesViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rig.updates.start() }
        val vm = UpdatesViewModel(rig.updates, links, appScope(), clock, nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `B-MIGR-012 the open page steps the app-wide notice aside and gives it back when closed`() = runTest {
        val rig = UpdateRig(clock)
        val vm = started(rig)
        assertTrue(rig.updates.state.value.pageOpen)
        assertFalse(rig.updates.state.value.noticeVisible, "a release waits, but the page is where it shows")
        assertEquals(UpdatesAction.Update, vm.state.value.action)
        val store = ViewModelStore()
        store.put("updates", vm)
        store.clear()
        assertFalse(rig.updates.state.value.pageOpen)
        assertTrue(rig.updates.state.value.noticeVisible)
    }

    @Test
    fun `B-MIGR-012 a checksum mismatch deletes the file, never installs, and offers Try again`() = runTest {
        val rig = UpdateRig(clock)
        rig.platform.hash = "b".repeat(64)
        val vm = started(rig)
        vm.onUpdate()
        val ui = vm.state.value
        assertEquals(UpdateFailure.Checksum, ui.failure)
        assertEquals(UpdatesLine.NotInstalled, ui.line)
        assertEquals(UpdatesAction.TryAgain, ui.action)
        assertEquals(listOf("/cache/app-update/app-release.apk"), rig.platform.deleted)
        assertTrue(rig.platform.installs.isEmpty())
        vm.onTryAgain()
        assertEquals(2, rig.feed.downloads, "Try again downloads again")
    }

    @Test
    fun `B-MIGR-012 a signer refusal offers the release page through the link port, never a retry`() = runTest {
        val rig = UpdateRig(clock)
        rig.platform.signer = "BB22"
        val vm = started(rig)
        vm.onUpdate()
        assertEquals(UpdateFailure.Signer, vm.state.value.failure)
        assertEquals(UpdatesAction.OpenRelease, vm.state.value.action)
        assertTrue(rig.platform.installs.isEmpty())
        vm.onTryAgain()
        assertEquals(1, rig.feed.downloads, "the same file would be refused again")
        vm.onOpenRelease()
        assertEquals(listOf(PAGE_URL), links.opened)
    }

    @Test
    fun `B-MIGR-012 a matching file goes to Android's installer, which asks the person`() = runTest {
        val rig = UpdateRig(clock)
        val vm = started(rig)
        vm.onUpdate()
        assertEquals(listOf("/cache/app-update/app-release.apk"), rig.platform.installs)
        assertEquals(UpdatePhase.Installing, rig.updates.state.value.phase)
        assertEquals(UpdatesLine.Installing, vm.state.value.line)
    }

    @Test
    fun `B-MIGR-012 without the install permission the page says so and asks Android once`() = runTest {
        val rig = UpdateRig(clock)
        rig.platform.allowed = false
        val vm = started(rig)
        vm.onUpdate()
        assertEquals(UpdatesAction.Install, vm.state.value.action)
        assertTrue(vm.state.value.needsPermission)
        assertEquals(1, rig.platform.permissionAsks)
        assertTrue(rig.platform.installs.isEmpty())
    }

    @Test
    fun `B-MIGR-012 Cancel stops the download and the update is offered again`() = runTest {
        val rig = UpdateRig(clock)
        rig.feed.gate = CompletableDeferred()
        val vm = started(rig)
        vm.onUpdate()
        assertEquals(UpdatesAction.Cancel, vm.state.value.action)
        assertTrue(vm.state.value.showProgress)
        assertFalse(vm.state.value.checkNowEnabled)
        vm.onCancel()
        testScheduler.advanceUntilIdle()
        assertEquals(UpdatePhase.Available, rig.updates.state.value.phase)
        assertEquals(UpdatesAction.Update, vm.state.value.action)
    }

    @Test
    fun `B-MIGR-012 the switch turns checking off and Check now still asks`() = runTest {
        val rig = UpdateRig(clock)
        val vm = started(rig)
        assertEquals(1, rig.feed.latestCalls)
        vm.onChecks(false)
        assertFalse(rig.updates.state.value.enabled)
        assertFalse(rig.store.record.enabled)
        assertFalse(vm.state.value.checksOn)
        rig.feed.latestJson = null
        vm.onCheckNow()
        assertEquals(2, rig.feed.latestCalls)
        assertEquals(UpdatesLine.CheckedJustNow, vm.state.value.line)
        assertEquals(null, vm.state.value.action)
    }

    @Test
    fun `B-ANDR-094 where the app cannot install itself the page never claims an update`() = runTest {
        val rig = UpdateRig(clock, supported = false)
        val vm = started(rig)
        vm.onCheckNow()
        vm.onUpdate()
        assertEquals(0, rig.feed.latestCalls)
        assertEquals(0, rig.feed.downloads)
        assertEquals(null, vm.state.value.action)
        assertEquals(UpdatesLine.NeverChecked, vm.state.value.line)
        assertFalse(rig.updates.state.value.noticeVisible)
    }
}
