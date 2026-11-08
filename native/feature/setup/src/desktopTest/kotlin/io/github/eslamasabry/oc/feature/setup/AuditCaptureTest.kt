@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, io.github.eslamasabry.oc.core.common.RedactorApi::class)

package io.github.eslamasabry.oc.feature.setup

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateActions
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateUi
import io.github.eslamasabry.oc.feature.setup.presentation.RecheckAnswer
import io.github.eslamasabry.oc.feature.setup.presentation.SetupActions
import io.github.eslamasabry.oc.feature.setup.presentation.SetupFailureLine
import io.github.eslamasabry.oc.feature.setup.presentation.SetupHeadline
import io.github.eslamasabry.oc.feature.setup.presentation.SetupPhase
import io.github.eslamasabry.oc.feature.setup.presentation.SetupProgressUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupStartUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupStepUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupUi
import io.github.eslamasabry.oc.feature.setup.ui.ComputerUpdateScreen
import io.github.eslamasabry.oc.feature.setup.ui.SetupScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** Audit capture only: the phone setup page in each phase and the OpenCode 1 computer update guide. */
class AuditCaptureTest {
    private object A : SetupActions {
        override fun start() {}

        override fun pause() {}

        override fun resume() {}

        override fun openAppSettings() {}

        override fun back() {}
    }

    private object CA : ComputerUpdateActions {
        override fun recheck() {}

        override fun openGuide() {}

        override fun changeServer() {}

        override fun back() {}
    }

    private val steps =
        persistentListOf(
            SetupStepUi("linux", "Linux base", ComponentState.Done, version = "24.04"),
            SetupStepUi("essentials", "Git and SSH", ComponentState.Done),
            SetupStepUi("python", "Python", ComponentState.Done),
            SetupStepUi("node", "Node.js", ComponentState.Running, stage = "Downloading Node.js 24.21.0", bytes = "18" to "30 MB"),
            SetupStepUi("opencode", "OpenCode", ComponentState.Pending),
            SetupStepUi("start", "Start OpenCode", ComponentState.Pending),
        )

    private val failedSteps =
        persistentListOf(
            SetupStepUi("linux", "Linux base", ComponentState.Done, version = "24.04"),
            SetupStepUi("essentials", "Git and SSH", ComponentState.Failed),
            SetupStepUi("python", "Python", ComponentState.Pending),
            SetupStepUi("node", "Node.js", ComponentState.Pending),
            SetupStepUi("opencode", "OpenCode", ComponentState.Pending),
            SetupStepUi("start", "Start OpenCode", ComponentState.Pending),
        )

    private fun progress(
        state: SetupState,
        headline: SetupHeadline,
        subject: String? = null,
        step: Int? = null,
        measured: Float? = null,
        bytes: Pair<String, String>? = null,
        minutesLeft: Int? = null,
        failure: SetupFailureLine? = null,
        rows: kotlinx.collections.immutable.ImmutableList<SetupStepUi> = steps,
        details: Redacted? = null,
    ) = SetupUi(
        phase =
            SetupPhase.Progress(
                SetupProgressUi(
                    state = state,
                    headline = headline,
                    subject = subject,
                    step = step,
                    of = 6,
                    measured = measured,
                    bytes = bytes,
                    minutesLeft = minutesLeft,
                    failure = failure,
                    steps = rows,
                    details = details,
                ),
            ),
    )

    private fun ComposeUiTest.openDetails() {
        runCatching {
            onNodeWithText("Details").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(600)
            waitForIdle()
        }
    }

    private fun shoot(state: String, ui: SetupUi, details: Boolean = false) {
        Audit.shoot(
            "setup",
            if (details) "phone-setup-details" else "phone-setup",
            state,
            setup = { if (details) openDetails() },
        ) {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) { SetupScreen(ui, A) }
        }
    }

    @Test
    fun phoneSetupStates() = Audit.guard("setup__phone-setup__build") {
        shoot("loading", SetupUi(phase = SetupPhase.Loading))
        val start = SetupStartUi(208_000_000L, persistentListOf("Git and SSH", "Python", "Node.js"))
        shoot("start", SetupUi(phase = SetupPhase.Start(start)))
        shoot("start-working", SetupUi(phase = SetupPhase.Start(start), working = true))
        shoot("start-settings-missing", SetupUi(phase = SetupPhase.Start(start), settingsMissing = true))
        shoot("checking", progress(SetupState.Running, SetupHeadline.Checking))
        shoot("getting-started", progress(SetupState.Running, SetupHeadline.GettingStarted, step = 1))
        val downloading =
            progress(
                SetupState.Running, SetupHeadline.Downloading, subject = "Node.js", step = 4,
                measured = 0.6f, bytes = "18" to "30 MB", minutesLeft = 3,
            )
        shoot("downloading", downloading)
        shoot("downloading-working", downloading.copy(working = true))
        shoot("unpacking", progress(SetupState.Running, SetupHeadline.Unpacking, subject = "Node.js", step = 4, measured = 0.9f))
        shoot("installing", progress(SetupState.Running, SetupHeadline.Installing, subject = "OpenCode", step = 5))
        shoot("starting", progress(SetupState.Running, SetupHeadline.Starting, step = 6))
        shoot("paused", progress(SetupState.Cancelled, SetupHeadline.Paused, step = 4, measured = 0.6f))
        shoot("interrupted", progress(SetupState.Interrupted, SetupHeadline.Interrupted, step = 4))
        val failed =
            progress(
                SetupState.Failed, SetupHeadline.Failed, step = 2,
                failure = SetupFailureLine.Error(SetupError.Offline("Git and SSH")),
                rows = failedSteps,
                details = Redacted("E: Failed to fetch the package index (example log line)"),
            )
        shoot("failed-offline", failed)
        shoot("failed-no-space", failed.copy(phase = (failed.phase as SetupPhase.Progress).let {
            SetupPhase.Progress(it.progress.copy(failure = SetupFailureLine.Error(SetupError.NoSpace("Node.js"))))
        }))
        shoot("failed-ownership", progress(SetupState.Failed, SetupHeadline.Failed, step = 2, failure = SetupFailureLine.Ownership))
        shoot("failed-unknown", progress(SetupState.Failed, SetupHeadline.Failed, step = 2, failure = SetupFailureLine.Unknown))
        shoot("failed-during-long", progress(SetupState.Failed, SetupHeadline.Failed, step = 2,
            failure = SetupFailureLine.During("Downloading a very long component name that keeps going and going and going")))
        shoot("done", progress(SetupState.Done, SetupHeadline.Done))
    }

    @Test
    fun phoneSetupDetailsSheet() = Audit.guard("setup__phone-setup-details__build") {
        shoot(
            "downloading",
            progress(SetupState.Running, SetupHeadline.Downloading, subject = "Node.js", step = 4, measured = 0.6f, bytes = "18" to "30 MB", minutesLeft = 3),
            details = true,
        )
        shoot(
            "failed",
            progress(
                SetupState.Failed, SetupHeadline.Failed, step = 2,
                failure = SetupFailureLine.Error(SetupError.Offline("Git and SSH")),
                rows = failedSteps,
                details = Redacted("E: Failed to fetch the package index (example log line)"),
            ),
            details = true,
        )
    }

    @Test
    fun computerUpdateStates() = Audit.guard("setup__computer-update__build") {
        fun shootC(state: String, ui: ComputerUpdateUi) {
            Audit.shoot("setup", "computer-update", state) { ComputerUpdateScreen(ui, CA) }
        }
        shootC("loading", ComputerUpdateUi(loading = true))
        shootC("missing", ComputerUpdateUi(loading = false, missing = true))
        shootC("guide", ComputerUpdateUi(loading = false, server = "Work laptop", port = 4096))
        shootC("guide-long-name", ComputerUpdateUi(loading = false, server = "A computer with a very long server name that keeps going past the edge of the page", port = 4123))
        shootC("checking", ComputerUpdateUi(loading = false, server = "Work laptop", checking = true))
        shootC("still-opencode1", ComputerUpdateUi(loading = false, server = "Work laptop", answer = RecheckAnswer.StillOpenCode1))
        shootC("password-rejected", ComputerUpdateUi(loading = false, server = "Work laptop", answer = RecheckAnswer.PasswordRejected))
        shootC("no-answer", ComputerUpdateUi(loading = false, server = "Work laptop", answer = RecheckAnswer.NoAnswer))
        shootC("other", ComputerUpdateUi(loading = false, server = "Work laptop", answer = RecheckAnswer.Other))
    }
}
