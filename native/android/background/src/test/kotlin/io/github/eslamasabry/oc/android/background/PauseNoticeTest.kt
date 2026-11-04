package io.github.eslamasabry.oc.android.background

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/background_pause_notice_test.dart (B-ANDR-018): the presenter and line model behind the status-line
// notice. The pixels are the kit status line's; this proves which line, which words and which actions it gets.
@OptIn(ExperimentalCoroutinesApi::class)
class PauseNoticeTest {
    /** Flutter `FakePauseGateway`: the pause state and a Resume that answers when the test says so. */
    private class FakeGateway(initial: PauseState) : PauseGateway {
        val pauses = MutableStateFlow(initial)
        var resumeCalls = 0
        var refreshCalls = 0
        var gate: CompletableDeferred<ResumeResult>? = null
        var answer: () -> ResumeResult = { ResumeResult(running) }

        override val pause: StateFlow<PauseState> = pauses

        override suspend fun resume(): ResumeResult {
            resumeCalls++
            val result = gate?.await() ?: answer()
            pauses.value = result.state
            return result
        }

        override suspend fun refresh(): PauseState {
            refreshCalls++
            return pauses.value
        }
    }

    private class MemoryDismissed : DismissedPauseStore {
        var value: String? = null

        override fun read(): String? = value

        override suspend fun write(id: String): Boolean {
            value = id
            return true
        }
    }

    private val dismissed = MemoryDismissed()

    private fun TestScope.notice(gateway: PauseGateway) = PauseNotice(gateway, dismissed, backgroundScope, fixedClock)

    @Test
    fun `B-ANDR-018 nothing shows while the connection runs, is off, or the phone cannot tell`() = runTest {
        for (state in listOf(PauseState.Unsupported, running, PauseState.Off)) {
            assertNull(notice(FakeGateway(state)).notice, state.toString())
        }
    }

    @Test
    fun `B-ANDR-018 each reason is one compact line with an inline Resume a screen reader hears with its target`() = runTest {
        val cases =
            mapOf(
                PauseReason.TimeLimit to PauseMessage.TimeLimit,
                PauseReason.BatteryRestricted to PauseMessage.Restricted,
                PauseReason.UserStopped to PauseMessage.UserStopped,
                PauseReason.AppUpdated to PauseMessage.AppUpdated,
                PauseReason.DeviceRestarted to PauseMessage.DeviceRestarted,
                PauseReason.Interrupted to PauseMessage.Interrupted,
            )
        for ((reason, message) in cases) {
            val state = checkNotNull(notice(FakeGateway(pausedFor(reason))).notice)
            val line = PauseNoticeLines.lineFor(state)
            assertEquals(message, line.message, reason.name)
            assertEquals(PauseNoticeLines.ID, line.id)
            assertEquals(PauseTone.Neutral, line.tone)
            assertEquals(PauseAction.Resume, line.action)
            assertEquals(listOf(PauseAction.KeepRunning), line.more)
            assertTrue(line.dismissible)
            // The time is said only where it is the moment it stopped.
            assertEquals(if (message.saysTime) AT else null, line.atMs, reason.name)
            assertEquals(reason == PauseReason.TimeLimit || reason == PauseReason.BatteryRestricted, line.batteryIcon)
        }
        assertEquals(R.string.background_pause_resume_short, BackgroundWords.action(PauseAction.Resume))
        assertEquals(R.string.background_pause_resume, BackgroundWords.actionSemantics(PauseAction.Resume))
    }

    @Test
    fun `B-ANDR-018 Resume goes through the gateway once, shows it is resuming, and the notice goes once Android confirms`() =
        runTest {
            val gateway = FakeGateway(pausedFor()).apply { gate = CompletableDeferred() }
            val notice = notice(gateway)
            val first = async { notice.resume() }
            runCurrent()
            val resuming = checkNotNull(notice.notice)
            assertEquals(PauseNoticePhase.Resuming, resuming.phase)
            assertEquals(fixedClock.now().toEpochMilliseconds(), resuming.resumingSinceMs)
            val line = PauseNoticeLines.lineFor(resuming)
            assertEquals(PauseMessage.Resuming, line.message)
            assertNull(line.action, "only the progress line; no Resume while Android decides")
            assertEquals(PauseTone.Progress, line.tone)
            // A second request joins the first; Android is asked once.
            val second = async { notice.resume() }
            runCurrent()
            assertEquals(1, gateway.resumeCalls)
            gateway.gate!!.complete(ResumeResult(running))
            assertTrue(first.await())
            assertTrue(second.await())
            assertNull(notice.notice)
        }

    @Test
    fun `B-ANDR-018 a resume Android does not confirm keeps the pause and offers Keep running, Resume again under More`() =
        runTest {
            val paused = pausedFor(PauseReason.BatteryRestricted)
            val gateway = FakeGateway(paused).apply { answer = { ResumeResult(paused, ResumeError.ResumeFailed) } }
            val notice = notice(gateway)
            assertFalse(notice.resume())
            val line = PauseNoticeLines.lineFor(checkNotNull(notice.notice))
            assertEquals(PauseMessage.ResumeFailed, line.message)
            assertEquals(PauseTone.Failure, line.tone)
            assertEquals(PauseAction.KeepRunning, line.action)
            assertEquals(listOf(PauseAction.ResumeAgain), line.more)
            assertTrue(gateway.pause.value.paused)
            assertEquals(R.string.background_pause_resume, BackgroundWords.action(PauseAction.ResumeAgain))
        }

    @Test
    fun `B-ANDR-018 busy is not a failure and the plain Resume stays`() = runTest {
        val paused = pausedFor()
        val gateway = FakeGateway(paused).apply { answer = { ResumeResult(paused, ResumeError.Busy) } }
        val notice = notice(gateway)
        assertFalse(notice.resume())
        val line = PauseNoticeLines.lineFor(checkNotNull(notice.notice))
        assertEquals(PauseNoticePhase.Paused, notice.notice?.phase)
        assertEquals(PauseAction.Resume, line.action)
    }

    @Test
    fun `B-ANDR-018 when Android allows no resume the action is Keep running`() = runTest {
        val line =
            PauseNoticeLines.lineFor(
                checkNotNull(notice(FakeGateway(pausedFor(PauseReason.BatteryRestricted, canResume = false))).notice),
            )
        assertEquals(PauseAction.KeepRunning, line.action)
        assertEquals(emptyList(), line.more)
    }

    @Test
    fun `B-ANDR-018 the notice yields to the app-exit recap until the phone server is back`() {
        val exit = Any()
        val paused = Any()
        assertSame(exit, PauseNoticeLines.appStoppedLines(exit, paused, serverBack = false).first())
        assertSame(paused, PauseNoticeLines.appStoppedLines(exit, paused, serverBack = true).first())
        assertSame(paused, PauseNoticeLines.appStoppedLines(null, paused, serverBack = false).firstNotNullOf { it })
    }

    @Test
    fun `B-ANDR-018 a dismissed pause stays hidden after a restart while a new pause shows again`() = runTest {
        val at = 1_791_450_000_000L
        val gateway = FakeGateway(pausedFor(PauseReason.TimeLimit, at = at))
        val first = notice(gateway)
        assertTrue(first.notice != null)
        first.dismiss()
        assertNull(first.notice)
        runCurrent()
        // A cold start reads the same durable pause.
        val restarted = notice(gateway)
        assertNull(restarted.notice)
        gateway.pauses.value = pausedFor(PauseReason.TimeLimit, at = at + 86_400_000L)
        assertEquals(PauseNoticePhase.Paused, restarted.notice?.phase)
    }

    @Test
    fun `B-ANDR-018 a failed resume is remembered only for that pause`() = runTest {
        val paused = pausedFor(PauseReason.Interrupted, at = 1_791_450_000_000L)
        val gateway = FakeGateway(paused).apply { answer = { ResumeResult(paused, ResumeError.ResumeFailed) } }
        val notice = notice(gateway)
        assertFalse(notice.resume())
        assertEquals(PauseNoticePhase.Failed, notice.notice?.phase)
        gateway.pauses.value = pausedFor(PauseReason.TimeLimit, at = 1_791_536_400_000L)
        assertEquals(PauseNoticePhase.Paused, notice.notice?.phase)
    }

    @Test
    fun `B-ANDR-018 returning to the app re-reads the pause receipt`() = runTest {
        val gateway = FakeGateway(running)
        notice(gateway).refresh()
        assertEquals(1, gateway.refreshCalls)
    }

    @Test
    fun `B-ANDR-018 the live-mode gateway feeds the notice and its Resume`() = runTest {
        val port = FakeLivePort().apply { onPause = { ok(pausedFor()) } }
        val controller = liveMode(backgroundScope, port, FakeOptIn(true))
        controller.restore()
        val notice = PauseNotice(controller.pauseGateway(backgroundScope), dismissed, backgroundScope, fixedClock)
        runCurrent()
        assertEquals(PauseReason.TimeLimit, notice.notice?.reason)
        assertTrue(notice.resume())
        assertNull(notice.notice)
    }
}
