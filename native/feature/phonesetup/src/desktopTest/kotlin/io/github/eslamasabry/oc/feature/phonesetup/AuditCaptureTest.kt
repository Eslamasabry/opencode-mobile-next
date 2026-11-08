@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectNameProblem
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectStep
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupItemUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupTransport
import io.github.eslamasabry.oc.feature.phonesetup.ui.PhoneSetupScreen
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentProgress as LComponentProgress
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState as LComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress as LSetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState as LSetupState

/** Audit capture only: the live phone setup journey and the older Set up page in their states. */
class AuditCaptureTest {
    /** Extra states run only when OC_AUDIT_FULL is set, to bound the default CI time. */
    private fun more(block: () -> Unit) {
        if (System.getenv("OC_AUDIT_FULL") != null) block()
    }

    private object JA : SetupJourneyActions {
        override fun back() {}

        override fun retry() {}

        override fun toggle(id: String, checked: Boolean) {}

        override fun accept() {}

        override fun start() {}

        override fun customize() {}

        override fun continueSetup() {}

        override fun askStop() {}

        override fun cancelConfirm() {}

        override fun confirmStop() {}

        override fun askRemoveVoice() {}

        override fun confirmRemoveVoice() {}

        override fun openStorage() {}

        override fun done() {}

        override fun connectComputer() {}

        override fun reportFailure() {}
    }

    private object PA : PhoneSetupActions {
        override fun back() {}

        override fun setUp() {}

        override fun continueSetup() {}

        override fun askStop() {}

        override fun keepGoing() {}

        override fun stop() {}

        override fun openStorage() {}

        override fun done() {}

        override fun reportFailure() {}

        override fun connectComputer() {}
    }

    private val registry =
        listOf(
            SetupComponent("linux", "Linux base", "Linux base", "true", "", required = true, estimatedSeconds = 20, downloadBytes = 30000000),
            SetupComponent("essentials", "Git, SSH and certificates", "Git and SSH", "true", "true", dependsOn = listOf("linux"), required = true, estimatedSeconds = 40, downloadBytes = 15000000),
            SetupComponent("python", "Python", "Python", "true", "true", dependsOn = listOf("essentials"), defaultOn = true, estimatedSeconds = 50, downloadBytes = 30000000, summary = "Run Python scripts"),
            SetupComponent("node", "Node.js", "Node.js", "true", "true", dependsOn = listOf("essentials"), required = true, estimatedSeconds = 40, downloadBytes = 30000000),
            SetupComponent("opencode", "OpenCode", "OpenCode", "true", "true", dependsOn = listOf("node"), required = true, estimatedSeconds = 90, downloadBytes = 60000000),
        )

    private val base =
        SetupJourneyUi(
            checking = false,
            chosen = setOf("python"),
            options = SetupOptions(registry, emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10000000000)),
        )

    private fun journey(state: String, ui: SetupJourneyUi, setup: ComposeUiTest.() -> Unit = {}, screen: String = "journey") {
        Audit.shoot("phonesetup", screen, state, setup = setup) { SetupJourneyScreen(ui, JA) }
    }

    private fun running(percent: Double = 60.0) =
        SetupProgress(
            SetupState.Running,
            listOf(
                ComponentProgress("linux", ComponentState.Done),
                ComponentProgress("essentials", ComponentState.Done),
                ComponentProgress("python", ComponentState.Done),
                ComponentProgress("node", ComponentState.Running, stage = "Downloading Node.js 24", bytesDone = 18000000, bytesTotal = 30000000, percent = percent),
                ComponentProgress("opencode", ComponentState.Pending),
            ),
            .5,
            current = "node",
            etaSeconds = 130,
            jobId = "running",
        )

    private fun failedOffline() =
        SetupProgress(
            SetupState.Failed,
            listOf(
                ComponentProgress("linux", ComponentState.Done, version = "24.04"),
                ComponentProgress("essentials", ComponentState.Done),
                ComponentProgress("python", ComponentState.Done, version = "3.12.3"),
                ComponentProgress("node", ComponentState.Failed, stage = "Downloading Node.js 24", error = SetupError.Offline("Node.js")),
                ComponentProgress("opencode", ComponentState.Pending),
            ),
            .55,
            current = "node",
            error = SetupError.Offline("Node.js"),
            logTail = "example log tail line",
            jobId = "scene",
            firstSetup = true,
        )

    private fun clickText(text: String): ComposeUiTest.() -> Unit = {
        runCatching {
            onNodeWithText(text).performClick()
            waitForIdle()
            mainClock.advanceTimeBy(600)
            waitForIdle()
        }
    }

    @Test
    fun journeyStates() = Audit.guard("phonesetup__journey__build") {
        journey("checking", base.copy(checking = true))
        journey("start", base)
        journey("start-working", base.copy(working = true))
        more { journey("start-check-failed", base.copy(failure = Failure.TimedOut())) }
        more { journey("start-storage-unopened", base.copy(storageUnopened = true)) }
        journey("start-other-ways", base, setup = clickText("Other ways"))
        journey("unsupported-abi", base.copy(options = base.options!!.copy(device = SetupDeviceInfo(listOf("armeabi-v7a"), 4096, 10000000000))))
        journey("low-memory", base.copy(options = base.options!!.copy(device = SetupDeviceInfo(listOf("arm64-v8a"), 1024, 10000000000))))
        journey("low-space", base.copy(options = base.options!!.copy(device = SetupDeviceInfo(listOf("arm64-v8a"), 8192, 100000000))))
        journey("customize", base.copy(customizing = true))
        more { journey("customize-all-installed", base.copy(customizing = true, adding = true, options = base.options!!.copy(installed = setOf("python")))) }
        journey("running", base.copy(progress = running(), transport = SetupTransport.Wifi, canNotify = true))
        journey("running-mobile-data", base.copy(progress = running(), transport = SetupTransport.Mobile))
        more { journey("running-details", base.copy(progress = running()), setup = clickText("Details")) }
        journey("running-confirm-stop", base.copy(progress = running(), confirmStop = "running"))
        journey("paused", base.copy(progress = SetupProgress(SetupState.Cancelled, listOf(ComponentProgress("linux", ComponentState.Done), ComponentProgress("essentials", ComponentState.Pending)), .2, jobId = "stopped")))
        more { journey("interrupted", base.copy(progress = SetupProgress(SetupState.Interrupted, listOf(ComponentProgress("linux", ComponentState.Done), ComponentProgress("essentials", ComponentState.Pending)), .2, jobId = "stopped"))) }
        more { journey("adding-paused", base.copy(progress = SetupProgress(SetupState.Cancelled, listOf(ComponentProgress("python", ComponentState.Pending)), 0.0, adding = listOf("node", "python")))) }
        journey("failed-offline", base.copy(progress = failedOffline()))
        journey("failed-offline-details", base.copy(progress = failedOffline()), setup = clickText("Details"))
        more { journey("failed-continue-error", base.copy(progress = failedOffline(), failure = Failure.TimedOut())) }
        more { journey("failed-unexpected", base.copy(progress = SetupProgress(SetupState.Failed, listOf(ComponentProgress("node", ComponentState.Failed, stage = "Installing Node.js", error = SetupError.Unexpected)), .5))) }
        more { journey("failed-current-only", base.copy(progress = SetupProgress(SetupState.Failed, emptyList(), .5, current = "python"))) }
        more { journey("failed-no-space", base.copy(progress = SetupProgress(SetupState.Failed, listOf(ComponentProgress("node", ComponentState.Failed, error = SetupError.NoSpace("Node.js"))), .5))) }
        journey("failed-ownership", base.copy(progress = SetupProgress(SetupState.Failed, emptyList(), .5, error = SetupError.OwnershipUnknown)))
        journey(
            "failed-node-cannot-run",
            base.copy(
                progress = SetupProgress(
                    SetupState.Failed,
                    listOf(ComponentProgress("linux", ComponentState.Done), ComponentProgress("node", ComponentState.Failed, error = SetupError.NodeCannotRun), ComponentProgress("opencode", ComponentState.Pending)),
                    0.4,
                    current = "node",
                    error = SetupError.NodeCannotRun,
                    logTail = "example log tail line",
                    jobId = "setup-1",
                ),
            ),
        )
    }

    @Test
    fun journeyReadyStates() = Audit.guard("phonesetup__journey-ready__build") {
        val done = SetupProgress(SetupState.Done, emptyList(), 1.0, jobId = "done")
        journey("done-open", base.copy(progress = done), screen = "journey-ready")
        journey("done-project", base.copy(progress = done, canMakeProject = true), screen = "journey-ready")
        more { journey("done-project-name-empty", base.copy(progress = done, canMakeProject = true, projectName = "", nameProblem = FirstProjectNameProblem.Empty), screen = "journey-ready") }
        journey("done-project-name-invalid", base.copy(progress = done, canMakeProject = true, projectName = "my/app", nameProblem = FirstProjectNameProblem.OneFolder), screen = "journey-ready")
        more { journey("done-project-creating", base.copy(progress = done, canMakeProject = true, creatingProject = true), screen = "journey-ready") }
        journey("done-project-create-failed", base.copy(progress = done, canMakeProject = true, projectFailure = FirstProjectStep.Create to Failure.Unknown()), screen = "journey-ready")
        more { journey("done-project-open-failed", base.copy(progress = done, canMakeProject = true, projectFailure = FirstProjectStep.Open to Failure.Unknown()), screen = "journey-ready") }
    }

    private val items = persistentListOf(SetupItemUi("linux", "Linux base"), SetupItemUi("node", "Node.js"), SetupItemUi("opencode", "OpenCode"))

    private fun page(state: String, ui: PhoneSetupUi) {
        Audit.shoot("phonesetup", "setup-page", state) { PhoneSetupScreen(ui, PA) }
    }

    @Test
    fun setupPageStates() = Audit.guard("phonesetup__setup-page__build") {
        page("checking", PhoneSetupUi(checking = true))
        page("start", PhoneSetupUi(checking = false, preflight = SetupPreflightResult.Ok, items = items, downloadBytes = 350_000_000))
        more { page("starting", PhoneSetupUi(checking = false, preflight = SetupPreflightResult.Ok, items = items, downloadBytes = 350_000_000, starting = true)) }
        page("unsupported-abi", PhoneSetupUi(checking = false, preflight = SetupPreflightResult.UnsupportedAbi("armeabi-v7a"), items = items))
        page("low-memory", PhoneSetupUi(checking = false, preflight = SetupPreflightResult.LowMemory(1024), items = items))
        more { page("may-be-slow", PhoneSetupUi(checking = false, preflight = SetupPreflightResult.MayBeSlow(2048), items = items, downloadBytes = 350_000_000)) }
        page("low-space", PhoneSetupUi(checking = false, preflight = SetupPreflightResult.LowSpace(1_200_000_000), items = items))
        more { page("low-space-storage-unopened", PhoneSetupUi(checking = false, preflight = SetupPreflightResult.LowSpace(1_200_000_000), items = items, storageUnopened = true)) }
        val running =
            LSetupProgress(
                state = LSetupState.Running,
                components = listOf(LComponentProgress("linux", LComponentState.Done), LComponentProgress("node", LComponentState.Running, stage = "Installing Node.js")),
                overall = 0.42,
                current = "node",
                jobId = "setup-1",
            )
        page("running", PhoneSetupUi(checking = false, items = items, progress = running))
        page("running-confirm-stop", PhoneSetupUi(checking = false, items = items, progress = running, confirmingStop = true))
        page(
            "failed-offline",
            PhoneSetupUi(
                checking = false,
                items = items,
                progress = LSetupProgress(
                    state = LSetupState.Failed,
                    components = listOf(LComponentProgress("opencode", LComponentState.Failed)),
                    overall = 0.6,
                    error = SetupProblem.Offline("OpenCode"),
                    logTail = "example log tail line",
                    jobId = "setup-1",
                ),
            ),
        )
        page(
            "failed-node-cannot-run",
            PhoneSetupUi(
                checking = false,
                items = items,
                progress = LSetupProgress(
                    state = LSetupState.Failed,
                    components = listOf(LComponentProgress("node", LComponentState.Failed, error = SetupProblem.NodeCannotRun)),
                    overall = 0.4,
                    error = SetupProblem.NodeCannotRun,
                    logTail = "example log tail line",
                    jobId = "setup-1",
                ),
                reportFailed = true,
            ),
        )
        page("done", PhoneSetupUi(checking = false, items = items, progress = LSetupProgress(state = LSetupState.Done, components = emptyList(), overall = 1.0, jobId = "setup-1")))
    }
}
