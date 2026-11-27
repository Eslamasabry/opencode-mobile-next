package io.github.eslamasabry.oc.feature.updates

import androidx.lifecycle.ViewModelStore
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.presentation.NoteLinkAction
import io.github.eslamasabry.oc.feature.updates.presentation.NoteLinkPrompt
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Updates page's ViewModel over a real AppUpdates engine and fake ports. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {
    private class Links : UpdateLinks {
        val opened = mutableListOf<String>()
        val openedNotes = mutableListOf<String>()
        var openNoteResult: Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun openReleasePage(url: String): Outcome<Unit> {
            opened += url
            return Outcome.Ok(Unit)
        }

        override fun inspectNoteLink(url: String): NoteLinkPrompt? {
            if (!url.startsWith("https://")) return null
            val host = url.removePrefix("https://").substringBefore('/').substringBefore('?')
            return if (host.isBlank()) null else NoteLinkPrompt(url, host)
        }

        override suspend fun openNoteLink(url: String): Outcome<Unit> {
            if (openNoteResult is Outcome.Ok) openedNotes += url
            return openNoteResult
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
        // A fixed name, never the server's.
        assertEquals(listOf("/cache/app-update/update-58.apk"), rig.platform.deleted)
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
        assertEquals(listOf("/cache/app-update/update-58.apk"), rig.platform.installs)
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

    @Test
    fun `B-628 What's new safe https link asks first and opens outside the app after confirmation`() = runTest {
        val rig = UpdateRig(clock)
        val vm = started(rig)
        val url = "https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v3.0.3"
        vm.onNoteLink(NoteLinkAction.Open(url))
        assertEquals(NoteLinkPrompt(url, "github.com"), vm.state.value.linkPrompt)
        assertTrue(links.openedNotes.isEmpty(), "must wait for confirmation before opening")
        vm.onNoteLink(NoteLinkAction.DismissPrompt)
        assertNull(vm.state.value.linkPrompt)
        assertTrue(links.openedNotes.isEmpty())

        vm.onNoteLink(NoteLinkAction.Open(url))
        vm.onNoteLink(NoteLinkAction.Confirm)
        assertNull(vm.state.value.linkPrompt)
        assertEquals(listOf(url), links.openedNotes)
        assertFalse(vm.state.value.noBrowser)
    }

    @Test
    fun `B-628 unsafe or non-https What's new links never prompt, open or crash`() = runTest {
        val rig = UpdateRig(clock)
        val vm = started(rig)
        val unsafe =
            listOf(
                "http://example.com",
                "javascript:alert(1)",
                "intent://scan/#Intent;end",
                "file:///etc/passwd",
                "",
            )
        for (bad in unsafe) {
            vm.onNoteLink(NoteLinkAction.Open(bad))
            assertNull(vm.state.value.linkPrompt, "refused link must not prompt: $bad")
            assertTrue(links.openedNotes.isEmpty())
            assertFalse(vm.state.value.noBrowser)
        }
    }

    @Test
    fun `B-628 missing browser shows one plain notice that can be dismissed`() = runTest {
        val rig = UpdateRig(clock)
        links.openNoteResult = Outcome.Failed(Failure.Device())
        val vm = started(rig)
        val url = "https://example.com/guide"
        vm.onNoteLink(NoteLinkAction.Open(url))
        vm.onNoteLink(NoteLinkAction.Confirm)
        assertTrue(vm.state.value.noBrowser)
        vm.onNoteLink(NoteLinkAction.DismissNoBrowser)
        assertFalse(vm.state.value.noBrowser)
    }
}
