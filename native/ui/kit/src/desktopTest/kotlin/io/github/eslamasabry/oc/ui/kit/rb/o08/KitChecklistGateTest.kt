package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupSceneHalt
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupSceneStage
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupSteps
import io.github.eslamasabry.oc.ui.kit.state.KitChecklist
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.state.KitStep
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The scenes of test/goldens/kit/kit_checklist_golden_test.dart. Flutter hosts each checklist in an inline
 * `KitStateView` with the setup-steps illustration; the native KitStateView has no inline size, illustration or content
 * slot yet (part-kit_state_view), so [host] lays out the same inline anatomy from kit parts
 * (lib/ui/kit/kit_state_view.dart `_build`, inline: gutter padding, the 88 dp drawing, space3 gaps, the headline title).
 */
class KitChecklistGateTest {
    private val logText = "Get:1 http://ports.ubuntu.com noble InRelease [256 kB]\nFetched 256 kB in 1s (212 kB/s)\n"
    private fun action(label: String, destructive: Boolean = false) = KitAction(label, {}, destructive = destructive)
    private fun s(
        title: String,
        state: KitStepState,
        supporting: String? = null,
        paused: Boolean = false,
        personAction: KitAction? = null,
        retry: KitAction? = null,
        value: Float? = null,
    ) = KitStep(title, state, paused, supporting, personAction, retry, value = value)

    private fun host(
        title: String,
        body: String? = null,
        done: Int = 1,
        halted: KitSetupSceneHalt? = null,
        progress: KitProgress? = null,
        checklist: @Composable () -> Unit,
    ): @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.State).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth()) {
                KitSetupSteps(
                    if (done >= 4) KitSetupSceneStage.Start else KitSetupSceneStage.Download,
                    done = done,
                    total = 4,
                    halted = halted,
                    width = 88.dp,
                )
                Spacer(Modifier.height(12.dp))
                KitText(title, role = TypeRole.Headline)
                if (body != null) {
                    Spacer(Modifier.height(4.dp))
                    KitText(body, role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
                if (progress != null) {
                    Spacer(Modifier.height(12.dp))
                    KitProgressView(progress)
                }
                Spacer(Modifier.height(12.dp))
                checklist()
            }
        }
    }

    private val beforeStart = host("Set up OpenCode on this phone", done = 0) {
        KitChecklist(
            listOf(
                s("Linux base", KitStepState.Waiting),
                s("Git and SSH", KitStepState.Waiting),
                s("Node.js", KitStepState.Waiting),
                s("OpenCode", KitStepState.Waiting),
            ),
            estimate = "About 8 minutes the first time",
            cost = listOf("About 208 MB", "uses battery while it installs"),
        )
    }

    private val working = host("Setting up OpenCode on this phone", progress = KitProgress.Known(.42f, caption = "~4 min left")) {
        KitChecklist(
            listOf(
                s("Linux base", KitStepState.Done, supporting = "24.04.5"),
                s("Git and SSH", KitStepState.Working, supporting = "Downloading · 18 of 30 MB", value = .6f),
                s("Node.js", KitStepState.Waiting),
                s("OpenCode", KitStepState.Waiting),
            ),
            stop = action("Cancel", destructive = true),
            log = { KitLogPanel(logText, { it }, {}) },
        )
    }

    private val personStep = host("Setting up Termux") {
        KitChecklist(
            listOf(
                s("Termux", KitStepState.Done, supporting = "Already installed"),
                s("Allow Termux to run commands", KitStepState.Waiting, supporting = "Opens Termux settings", personAction = action("Allow")),
                s("Install OpenCode", KitStepState.Waiting),
            ),
        )
    }

    private val slow = host("Setting up OpenCode on this phone") {
        KitChecklist(
            listOf(
                s("Linux base", KitStepState.Done, supporting = "24.04.5"),
                s("Git and SSH", KitStepState.Working, supporting = "Installing"),
                s("Node.js", KitStepState.Waiting),
            ),
            since = TimeSource.Monotonic.markNow() - 20.seconds,
            onSlow = listOf(action("Run in background")),
            stop = action("Cancel", destructive = true),
        )
    }

    private val failed = host("Setup didn't finish", halted = KitSetupSceneHalt.Failed, done = 2) {
        KitChecklist(
            listOf(
                s("Linux base", KitStepState.Done, supporting = "24.04.5"),
                s("Git and SSH", KitStepState.Done),
                s("Node.js", KitStepState.Failed, supporting = "Checking the download: The file was damaged", retry = action("Try again")),
                s("OpenCode", KitStepState.Waiting),
            ),
            resume = action("Continue setup"),
            log = { KitLogPanel(logText, { it }, {}) },
        )
    }

    private val paused = host("Setup paused", body = "Resumes when the phone cools.", halted = KitSetupSceneHalt.Paused) {
        KitChecklist(
            listOf(
                s("Linux base", KitStepState.Done, supporting = "24.04.5"),
                s("Git and SSH", KitStepState.Working, paused = true, supporting = "Resumes when the phone cools"),
                s("Node.js", KitStepState.Waiting),
            ),
            resume = action("Continue setup"),
        )
    }

    private val done = host("OpenCode is ready", done = 4) {
        KitChecklist(
            listOf(
                s("Linux base", KitStepState.Done, supporting = "24.04.5"),
                s("Git and SSH", KitStepState.Done),
                s("Node.js", KitStepState.Done, supporting = "Already installed"),
                s("OpenCode", KitStepState.Done, supporting = "1.4.2"),
            ),
        )
    }

    private val compact = host("Fix the login redirect") {
        KitChecklist(
            listOf(
                s("Plan", KitStepState.Done),
                s("Build", KitStepState.Done),
                s("Reviewing", KitStepState.Working),
                s("Merge", KitStepState.Waiting),
                s("Check", KitStepState.Waiting),
                s("Deploy", KitStepState.Waiting),
                s("Report", KitStepState.Waiting),
            ),
            compact = true,
            next = "merge",
        )
    }

    @Test fun kit_checklist_before_start() { FlutterGoldenGate.check("kit_checklist_before_start", wrap = { FlutterCapture(it) }, content = beforeStart) }
    @Test fun kit_checklist_working() { FlutterGoldenGate.check("kit_checklist_working", wrap = { FlutterCapture(it) }, content = working) }
    @Test fun kit_checklist_person_step() { FlutterGoldenGate.check("kit_checklist_person_step", wrap = { FlutterCapture(it) }, content = personStep) }
    @Test fun kit_checklist_slow() { FlutterGoldenGate.check("kit_checklist_slow", wrap = { FlutterCapture(it) }, content = slow) }
    @Test fun kit_checklist_failed() { FlutterGoldenGate.check("kit_checklist_failed", wrap = { FlutterCapture(it) }, content = failed) }
    @Test fun kit_checklist_paused() { FlutterGoldenGate.check("kit_checklist_paused", wrap = { FlutterCapture(it) }, content = paused) }
    @Test fun kit_checklist_done() { FlutterGoldenGate.check("kit_checklist_done", wrap = { FlutterCapture(it) }, content = done) }
    @Test fun kit_checklist_compact() { FlutterGoldenGate.check("kit_checklist_compact", wrap = { FlutterCapture(it) }, content = compact) }
    @Test fun kit_checklist_working_1280x800() { FlutterGoldenGate.check("kit_checklist_working_1280x800", wrap = { FlutterCapture(it) }, content = working) }
    @Test fun kit_checklist_before_start_text2() { FlutterGoldenGate.check("kit_checklist_before_start_text2", wrap = { FlutterCapture(it) }, content = beforeStart) }
    @Test fun kit_checklist_failed_text2() { FlutterGoldenGate.check("kit_checklist_failed_text2", wrap = { FlutterCapture(it) }, content = failed) }
    @Test fun kit_checklist_done_text2() { FlutterGoldenGate.check("kit_checklist_done_text2", wrap = { FlutterCapture(it) }, content = done) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_checklist", caps = mapOf("KitChecklist" to "reading"), content = working) }
}
