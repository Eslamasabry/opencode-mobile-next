package io.github.eslamasabry.oc.engine.runtime.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-488: active step names what it is doing, stalls map to StepTimedOut, and Details keeps Linux mode. */
class SetupStuckSilentTest {
    private val components =
        listOf(
            SetupComponent("linux", "Linux base", "Linux base", "true", "", required = true),
            SetupComponent("essentials", "Git and SSH", "Git and SSH", "true", "true", required = true),
        )

    @Test
    fun `B-SETUP-014 stalled step after compatible retry maps to StepTimedOut and keeps Linux mode in Details`() {
        val log =
            "[oc] Step essentials stopped making progress in normal mode; trying compatibility mode\n" +
                "[oc] Step essentials stopped making progress (Linux mode: compatible)\n"
        val record =
            SetupJobRecord(
                jobId = "setup-488-1",
                state = "failed",
                current = "essentials",
                components =
                    listOf(
                        SetupJobComponent("linux", "done"),
                        SetupJobComponent(
                            id = "essentials",
                            state = "failed",
                            error = "[oc] Step stopped making progress (Linux mode: compatible)",
                        ),
                    ),
                startedAt = 1_000L,
                updatedAt = 240_000L,
                error = "[oc] Step stopped making progress (Linux mode: compatible)",
                logTail = log,
            )

        val progress = SetupPlanning.progressFromRecord(record, components, nowMillis = 250_000L, redact = { it })

        assertEquals(SetupError.StepTimedOut("Git and SSH"), progress.error)
        assertEquals(
            SetupError.StepTimedOut("Git and SSH"),
            progress.components.first { it.id == "essentials" }.error,
        )
        assertTrue(progress.logTail.contains("Linux mode: compatible"), "Details must name the Linux mode used")
    }

    @Test
    fun `B-SETUP-015 proot liveness failure in both modes maps to StepTimedOut`() {
        val (row, job) =
            SetupPlanning.describeFailure(
                failed =
                    SetupJobComponent(
                        id = "linux",
                        state = "failed",
                        error = "Linux did not answer in normal or compatibility mode",
                    ),
                logTail = "[oc] Linux probe failed (normal=timeout, compat=timeout)\n",
                title = "Linux base",
            )

        assertEquals(SetupError.StepTimedOut("Linux base"), row)
        assertEquals(SetupError.StepTimedOut("Linux base"), job)
    }

    @Test
    fun `B-SETUP-016 active setup stages map to real activities instead of generic Working`() {
        assertEquals(SetupActivity.Checking, SetupActivity.fromLog("==> Checking Linux on this phone…\n"))
        assertEquals(SetupActivity.Downloading, SetupActivity.fromLog("==> Updating package lists\n"))
        assertEquals(SetupActivity.Downloading, SetupActivity.fromLog("==> The app is downloading this itself\n"))
        assertEquals(SetupActivity.Configuring, SetupActivity.fromLog("==> Installing packages\n"))
        assertEquals(SetupActivity.Configuring, SetupActivity.fromLog("==> Trying again in compatibility mode\n"))
        assertEquals(
            SetupActivity.Configuring,
            SetupActivity.fromLog("==> Repairing interrupted package installation\n"),
        )

        val record =
            SetupJobRecord(
                jobId = "setup-488-2",
                state = "running",
                current = "linux",
                components =
                    listOf(
                        SetupJobComponent("linux", "running", stage = "Downloading Linux base", startedAt = 1_000L),
                    ),
                startedAt = 1_000L,
                updatedAt = 2_000L,
                logTail = "",
            )
        val progress = SetupPlanning.progressFromRecord(record, components, nowMillis = 5_000L, redact = { it })
        assertEquals(SetupActivity.Downloading, progress.activity)
    }

    @Test
    fun `B-SETUP-038 activity stays anchored to active step stage despite stale prior log or unrecognized lines`() {
        val stalePriorLog =
            SetupJobRecord(
                jobId = "setup-488-3",
                state = "running",
                current = "essentials",
                components =
                    listOf(
                        SetupJobComponent("linux", "done"),
                        SetupJobComponent(
                            id = "essentials",
                            state = "running",
                            stage = "Updating package lists",
                            startedAt = 5_000L,
                        ),
                    ),
                startedAt = 1_000L,
                updatedAt = 6_000L,
                logTail = "==> Unpacking Linux base\nExtracting ./usr/bin/sh\n",
            )
        val staleProgress =
            SetupPlanning.progressFromRecord(stalePriorLog, components, nowMillis = 7_000L, redact = { it })
        assertEquals(SetupActivity.Downloading, staleProgress.activity)

        val unrecognizedLine =
            stalePriorLog.copy(
                components =
                    listOf(
                        SetupJobComponent("linux", "done"),
                        SetupJobComponent(
                            id = "essentials",
                            state = "running",
                            stage = "Installing packages",
                            startedAt = 5_000L,
                        ),
                    ),
                logTail = "==> Installing packages\ndebconf: delaying package configuration\n",
            )
        val unrecognizedProgress =
            SetupPlanning.progressFromRecord(unrecognizedLine, components, nowMillis = 8_000L, redact = { it })
        assertEquals(SetupActivity.Configuring, unrecognizedProgress.activity)

        val refinedLine =
            unrecognizedLine.copy(
                logTail = "==> Installing packages\nUnpacking git (1:2.43) ...\n",
            )
        val refinedProgress =
            SetupPlanning.progressFromRecord(refinedLine, components, nowMillis = 9_000L, redact = { it })
        assertEquals(SetupActivity.Unpacking, refinedProgress.activity)
    }
}
