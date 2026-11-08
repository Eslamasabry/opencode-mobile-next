package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.history.HistoryCopyScripts
import io.github.eslamasabry.oc.engine.runtime.history.HistoryShellPort
import io.github.eslamasabry.oc.engine.runtime.history.LinuxRunResult
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneImportStatus
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMovePorts
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMoveProfile
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMoveResult
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMoveStop
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneOpenCode2Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The OpenCode 1 → 2 move on this phone (root.dart `_moveAttempt`, setup_finish.dart) over a fake Ubuntu shell. */
class PhoneOpenCode2MoveTest {
    /** A fake Ubuntu: OpenCode 1 history in place; OpenCode 2 may already have its own. */
    private class FakeUbuntu(var oc2HasOwnHistory: Boolean = false, var answers: Boolean = true) : HistoryShellPort {
        val scripts = mutableListOf<String>()

        override suspend fun run(script: String, timeout: kotlin.time.Duration): Outcome<LinuxRunResult> {
            scripts += script
            if (!answers) return Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
            return Outcome.Ok(
                if (script == HistoryCopyScripts.admissionScript && oc2HasOwnHistory)
                    LinuxRunResult(
                        HistoryCopyScripts.FAILED_EXIT,
                        "${HistoryCopyScripts.SWITCH_BLOCKED_MARKER} ${HistoryCopyScripts.SWITCH_BLOCKED_TEXT}",
                    )
                else LinuxRunResult(0, "")
            )
        }
    }

    /** The saved phone profile, the setup job and OpenCode 2's import answers. */
    private class FakePorts(
        var profile: PhoneMoveProfile? =
            PhoneMoveProfile("phone", requiresOpenCode2Migration = true, openCode2HistoryPending = false),
        val imports: ArrayDeque<PhoneImportStatus> = ArrayDeque(listOf(PhoneImportStatus.Done)),
    ) : PhoneMovePorts {
        var installs = 0
        var confirms = 0

        override suspend fun pending(activeOnly: Boolean): Outcome<PhoneMoveProfile?> = Outcome.Ok(profile)

        override suspend fun reread(id: String): Outcome<PhoneMoveProfile?> = Outcome.Ok(profile?.takeIf { it.id == id })

        override suspend fun installOpenCode2(): Outcome<Unit> {
            installs++
            // The finish: history copied, OpenCode 2 started, pending until it confirms.
            profile = profile?.copy(requiresOpenCode2Migration = false, openCode2HistoryPending = true)
            return Outcome.Ok(Unit)
        }

        override suspend fun importStatus(id: String): Outcome<PhoneImportStatus> =
            Outcome.Ok(imports.removeFirstOrNull() ?: PhoneImportStatus.Waiting)

        override suspend fun confirmed(id: String): Outcome<Unit> {
            confirms++
            profile = profile?.copy(openCode2HistoryPending = false)
            return Outcome.Ok(Unit)
        }
    }

    @Test
    fun `B-LINUX-107 the move checks the history first, installs OpenCode 2 and clears the mark once confirmed`() =
        runTest {
            val ubuntu = FakeUbuntu()
            val ports =
                FakePorts(imports = ArrayDeque(listOf(PhoneImportStatus.Waiting, PhoneImportStatus.Waiting, PhoneImportStatus.Done)))
            val result = PhoneOpenCode2Move(ports, ubuntu).move()
            assertEquals(PhoneMoveResult.Done, result)
            assertEquals(listOf(HistoryCopyScripts.admissionScript), ubuntu.scripts)
            assertEquals(1, ports.installs)
            assertEquals(1, ports.confirms)
            assertEquals(PhoneMoveProfile("phone", false, false), ports.profile)
        }

    @Test
    fun `B-LINUX-108 a separate OpenCode 2 history stops the move before anything is installed`() = runTest {
        val ubuntu = FakeUbuntu(oc2HasOwnHistory = true)
        val ports = FakePorts()
        val result = PhoneOpenCode2Move(ports, ubuntu).move()
        val stopped = assertIs<PhoneMoveResult.Stopped>(result)
        assertEquals(PhoneMoveStop.SeparateHistory, stopped.reason)
        assertFalse(stopped.reason.retryable)
        // Nothing was changed: no install, no start, the OpenCode 1 marker stays.
        assertEquals(0, ports.installs)
        assertEquals(0, ports.confirms)
        assertTrue(ports.profile!!.requiresOpenCode2Migration)
    }

    @Test
    fun `B-LINUX-142 a history check that cannot answer never installs and can be tried again`() = runTest {
        val ports = FakePorts()
        val stopped = assertIs<PhoneMoveResult.Stopped>(PhoneOpenCode2Move(ports, FakeUbuntu(answers = false)).move())
        assertEquals(PhoneMoveStop.PhoneNotReady, stopped.reason)
        assertTrue(stopped.reason.retryable)
        assertEquals(0, ports.installs)
    }

    @Test
    fun `B-MIGR-002 an import OpenCode 2 never confirms keeps the pending mark and the OpenCode 1 data`() = runTest {
        val ports = FakePorts(imports = ArrayDeque())
        val result = PhoneOpenCode2Move(ports, FakeUbuntu()).move()
        val stopped = assertIs<PhoneMoveResult.Stopped>(result)
        assertEquals(PhoneMoveStop.NotConfirmed, stopped.reason)
        assertEquals(0, ports.confirms)
        assertTrue(ports.profile!!.openCode2HistoryPending)
        assertFalse(ports.profile!!.requiresOpenCode2Migration)
    }

    @Test
    fun `B-MIGR-002 an import OpenCode 2 reports as failed is not confirmed`() = runTest {
        val ports = FakePorts(imports = ArrayDeque(listOf(PhoneImportStatus.Failed)))
        val stopped = assertIs<PhoneMoveResult.Stopped>(PhoneOpenCode2Move(ports, FakeUbuntu()).move())
        assertEquals(PhoneMoveStop.NotConfirmed, stopped.reason)
        assertEquals(0, ports.confirms)
    }

    @Test
    fun `B-MIGR-002 a copied but unconfirmed history is confirmed again without a second check or install`() = runTest {
        val ubuntu = FakeUbuntu()
        val ports =
            FakePorts(
                profile = PhoneMoveProfile("phone", requiresOpenCode2Migration = false, openCode2HistoryPending = true)
            )
        assertEquals(PhoneMoveResult.Done, PhoneOpenCode2Move(ports, ubuntu).move())
        assertTrue(ubuntu.scripts.isEmpty())
        assertEquals(0, ports.installs)
        assertEquals(1, ports.confirms)
    }

    @Test
    fun `B-LINUX-105 a busy phone during install reads as not ready, so the quiet retry runs`() = runTest {
        val ports =
            object : PhoneMovePorts by FakePorts() {
                override suspend fun installOpenCode2(): Outcome<Unit> =
                    Outcome.Failed(Failure.Runtime(RuntimeProblem.SlotBusy))
            }
        val stopped = assertIs<PhoneMoveResult.Stopped>(PhoneOpenCode2Move(ports, FakeUbuntu()).move())
        assertEquals(PhoneMoveStop.PhoneNotReady, stopped.reason)
    }
}
