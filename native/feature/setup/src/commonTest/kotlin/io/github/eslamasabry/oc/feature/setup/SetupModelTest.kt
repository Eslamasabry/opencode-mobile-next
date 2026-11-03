@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, io.github.eslamasabry.oc.core.common.RedactorApi::class)

package io.github.eslamasabry.oc.feature.setup

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupJobParams
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.registry.PhoneSetupRegistry
import io.github.eslamasabry.oc.engine.runtime.setup.registry.PhoneSetupRegistryWords
import io.github.eslamasabry.oc.feature.setup.presentation.SetupDetails
import io.github.eslamasabry.oc.feature.setup.presentation.SetupFailureLine
import io.github.eslamasabry.oc.feature.setup.presentation.SetupHeadline
import io.github.eslamasabry.oc.feature.setup.presentation.SetupModel
import io.github.eslamasabry.oc.feature.setup.presentation.SetupNavigator
import io.github.eslamasabry.oc.feature.setup.presentation.SetupPhase
import io.github.eslamasabry.oc.feature.setup.presentation.SetupProgressUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupSession
import io.github.eslamasabry.oc.feature.setup.presentation.SetupSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The setup page's rules (port of phone_setup_start_screen.dart / phone_setup_progress_screen.dart behaviour). */
class SetupModelTest {
    private class FakeSource : SetupSource {
        val progress = MutableStateFlow(SetupProgress.Idle)
        val registry =
            PhoneSetupRegistry(PhoneSetupRegistryWords(linuxTitle = "Linux base", essentialsTitle = "Git", essentialsShort = "Git and SSH", startTitle = "Start OpenCode"))

        override suspend fun open(): SetupSession =
            SetupSession(progress, registry.components(emptyMap()), "Downloading Linux base", "Unpacking Linux base")

        var restoreGate: CompletableDeferred<Unit>? = null
        val runs = mutableListOf<Pair<Set<String>, Map<String, Map<String, String>>>>()
        var cancels = 0

        override suspend fun restore(): Outcome<Unit> {
            restoreGate?.await()
            return Outcome.Ok(Unit)
        }

        override suspend fun run(ids: Set<String>, params: Map<String, Map<String, String>>) {
            runs += ids to params
        }

        override suspend fun cancel() {
            cancels++
        }
    }

    private class Nav : SetupNavigator {
        val calls = mutableListOf<String>()
        var opens = true

        override fun back() {
            calls += "back"
        }

        override fun openAppSettings(): Boolean {
            calls += "settings"
            return opens
        }
    }

    private val source = FakeSource()
    private val nav = Nav()
    private val all = listOf("linux", "essentials", "python", "node", "opencode", "start")

    private fun TestScope.model(): SetupModel = SetupModel(source, nav, backgroundScope, SetupDetails { Redacted(it) }).also { runCurrent() }

    private fun job(state: SetupState, vararg rows: ComponentProgress, current: String? = null, error: SetupError? = null, log: String = "") =
        SetupProgress(
            state = state,
            components = all.map { id -> rows.firstOrNull { it.id == id } ?: ComponentProgress(id, ComponentState.Pending) },
            overall = 0.2,
            current = current,
            error = error,
            logTail = log,
            jobId = "setup-1",
        )

    private fun SetupModel.progress(): SetupProgressUi = assertIs<SetupPhase.Progress>(state.value.phase).progress

    @Test
    fun `N1-5 nothing is said before the saved job is read, then a phone with no job gets the start page`() = runTest {
        source.restoreGate = CompletableDeferred()
        val model = model()
        assertEquals(SetupPhase.Loading, model.state.value.phase)
        source.restoreGate!!.complete(Unit)
        runCurrent()
        val start = assertIs<SetupPhase.Start>(model.state.value.phase).start
        assertEquals(208_000_000L, start.downloadBytes)
        assertEquals(listOf("Git and SSH", "Python", "Node.js"), start.includes)
    }

    @Test
    fun `B-LINUX-196 Set up starts the first setup with the default selection and the first-setup flag`() = runTest {
        val model = model()
        model.start()
        model.start() // a second tap while the first is sent does nothing
        runCurrent()
        assertEquals(1, source.runs.size)
        assertEquals(all.toSet(), source.runs.single().first)
        assertEquals(SetupJobParams.firstSetup, source.runs.single().second)
        assertFalse(model.state.value.working)
    }

    @Test
    fun `B-LINUX-201 a download shows its step, its bytes and never moves backwards within the stage`() = runTest {
        val model = model()
        val done = ComponentProgress("linux", ComponentState.Skipped, version = "24.04")
        source.progress.value = job(
            SetupState.Running, done, ComponentProgress("essentials", ComponentState.Done),
            ComponentProgress("python", ComponentState.Done),
            ComponentProgress("node", ComponentState.Running, stage = "Downloading Node.js 24.21.0", bytesDone = 18_000_000, bytesTotal = 30_000_000),
            current = "node",
        )
        runCurrent()
        var p = model.progress()
        assertEquals(SetupHeadline.Downloading, p.headline)
        assertEquals("Node.js", p.subject)
        assertEquals(4, p.step)
        assertEquals(6, p.of)
        assertEquals("18" to "30 MB", p.bytes)
        assertEquals(0.6f, p.measured)
        assertTrue(p.running)
        // A smaller reading in the same stage keeps the ring where it was.
        source.progress.value = source.progress.value.copy(
            components = source.progress.value.components.map {
                if (it.id == "node") it.copy(bytesDone = 9_000_000) else it
            },
        )
        runCurrent()
        p = model.progress()
        assertEquals(0.6f, p.measured)
        assertEquals("9" to "30 MB", p.bytes)
        assertEquals("24.04", p.steps.first().version)
        // Unpacking the Linux base is said as unpacking, by its label.
        source.progress.value = job(SetupState.Running, ComponentProgress("linux", ComponentState.Running, stage = "Unpacking Linux base"), current = "linux")
        runCurrent()
        assertEquals(SetupHeadline.Unpacking, model.progress().headline)
    }

    @Test
    fun `B-LINUX-199 Pause stops the job, and a paused job continues with the same steps`() = runTest {
        val model = model()
        source.progress.value = job(SetupState.Running, ComponentProgress("linux", ComponentState.Running), current = "linux")
        runCurrent()
        model.pause()
        runCurrent()
        assertEquals(1, source.cancels)
        source.progress.value = job(SetupState.Cancelled, ComponentProgress("linux", ComponentState.Done))
        runCurrent()
        val p = model.progress()
        assertEquals(SetupHeadline.Paused, p.headline)
        assertTrue(p.canContinue)
        assertNull(p.failure)
        model.resume()
        runCurrent()
        assertEquals(all.toSet(), source.runs.single().first)
        assertEquals(emptyMap(), source.runs.single().second)
    }

    @Test
    fun `B-LINUX-203 a failure is one plain line with the log only under Details`() = runTest {
        val model = model()
        source.progress.value = job(
            SetupState.Failed,
            ComponentProgress("linux", ComponentState.Done),
            ComponentProgress("essentials", ComponentState.Failed, error = SetupError.Offline("Git and SSH")),
            current = "essentials",
            error = SetupError.NoInternet,
            log = "curl: (6) Could not resolve host: archive.ubuntu.com",
        )
        runCurrent()
        var p = model.progress()
        assertEquals(SetupHeadline.Failed, p.headline)
        assertEquals(SetupFailureLine.Error(SetupError.Offline("Git and SSH")), p.failure)
        assertEquals("curl: (6) Could not resolve host: archive.ubuntu.com", p.details?.text)
        assertTrue(p.canContinue)
        // A host refusal has no sentence of its own: it says where it stopped.
        source.progress.value = job(SetupState.Failed, current = "node", error = SetupError.HostRefused)
        runCurrent()
        p = model.progress()
        assertEquals(SetupFailureLine.At("Node.js"), p.failure)
        assertNull(p.details)
    }

    @Test
    fun `B-LINUX-193 an ownership failure offers app settings instead of Continue`() = runTest {
        val model = model()
        source.progress.value = job(SetupState.Failed, error = SetupError.OwnershipUnknown)
        runCurrent()
        val p = model.progress()
        assertEquals(SetupFailureLine.Ownership, p.failure)
        assertFalse(p.canContinue)
        nav.opens = false
        model.openAppSettings()
        runCurrent()
        assertEquals(listOf("settings"), nav.calls)
        assertTrue(model.state.value.settingsMissing)
    }

    @Test
    fun `B-LINUX-163 a finished job says so and Done goes back to This phone`() = runTest {
        val model = model()
        source.progress.value =
            SetupProgress(SetupState.Done, all.map { ComponentProgress(it, ComponentState.Done) }, 1.0, jobId = "setup-1")
        runCurrent()
        val p = model.progress()
        assertEquals(SetupHeadline.Done, p.headline)
        assertNull(p.step)
        assertFalse(p.canContinue)
        model.back()
        assertEquals(listOf("back"), nav.calls)
    }
}
