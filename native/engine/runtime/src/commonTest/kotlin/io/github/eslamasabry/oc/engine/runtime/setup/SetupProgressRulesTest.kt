package io.github.eslamasabry.oc.engine.runtime.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The progress screen's pure rules (port of test/phone_setup_progress_screen_test.dart:182 and :796). */
class SetupProgressRulesTest {
    private val ids = listOf("linux", "essentials", "python", "node", "opencode", "start")

    private fun job(
        state: SetupState = SetupState.Running,
        error: SetupError? = null,
        log: String = "",
        current: ComponentProgress? = null,
    ) = SetupProgress(
        state = state,
        overall = 0.4,
        error = error,
        logTail = log,
        current = current?.id,
        components = ids.map { id -> if (id == current?.id) current else ComponentProgress(id, ComponentState.Pending) },
    )

    @Test
    fun `B-LINUX-157 ownership recovery ignores running jobs and untrusted log lines`() {
        assertFalse(SetupProgressRules.needsOwnershipRecovery(job(SetupState.Running, SetupError.OwnershipUnknown)))
        assertFalse(
            SetupProgressRules.needsOwnershipRecovery(
                job(SetupState.Failed, SetupError.Install("download failed"), log = "ownershipUnknown"),
            ),
        )
        assertTrue(SetupProgressRules.needsOwnershipRecovery(job(SetupState.Failed, SetupError.OwnershipUnknown)))
        // A failed row carrying the code asks for it too.
        assertTrue(
            SetupProgressRules.needsOwnershipRecovery(
                job(SetupState.Failed, current = ComponentProgress("opencode", ComponentState.Failed, error = SetupError.OwnershipUnknown)),
            ),
        )
    }

    @Test
    fun `B-LINUX-157 a persisted row error naming ownershipUnknown becomes the recovery, a log line does not`() {
        val record = SetupJobRecord(
            jobId = "j",
            state = "failed",
            current = "opencode",
            components = listOf(SetupJobComponent("opencode", "failed", error = "runtime_ownership_unknown: ownershipUnknown")),
        )
        val progress = SetupPlanning.progressFromRecord(record, emptyList(), 0, { it })
        assertEquals(SetupError.OwnershipUnknown, progress.components.single().error)
        assertTrue(SetupProgressRules.needsOwnershipRecovery(progress))

        val logged = record.copy(
            components = listOf(SetupJobComponent("opencode", "failed", error = "download failed")),
            logTail = "ownershipUnknown\n",
        )
        val plain = SetupPlanning.progressFromRecord(logged, emptyList(), 0, { it })
        assertFalse(SetupProgressRules.needsOwnershipRecovery(plain))
    }

    @Test
    fun `B-LINUX-157 byte formatting rounds done down and keeps the unit`() {
        assertEquals("18" to "30 MB", SetupProgressRules.formatBytePair(18_999_999, 30_000_000))
        assertEquals("0.9" to "2.5 MB", SetupProgressRules.formatBytePair(950_000, 2_500_000))
        assertEquals("500" to "800 B", SetupProgressRules.formatBytePair(500, 800))
        assertEquals("20" to "20 B", SetupProgressRules.formatBytePair(40, 20))
    }
}
