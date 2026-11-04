package io.github.eslamasabry.oc.engine.voice.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ports test/voice_automatic_setup_platform_test.dart (notification and permission half). */
class VoiceSetupNoticesTest {
    private class Sink : VoiceSetupNoticeSink {
        val calls = mutableListOf<String>()
        var showAnswer: suspend (VoiceSetupNoticePhase, Long, Long) -> Outcome<Boolean> = { _, _, _ -> true.ok() }
        var permissionAnswer: suspend () -> Outcome<Boolean> = { true.ok() }
        var dismissAnswer: suspend () -> Outcome<Unit> = { Unit.ok() }

        override suspend fun show(phase: VoiceSetupNoticePhase, receivedBytes: Long, totalBytes: Long): Outcome<Boolean> {
            calls += "show ${phase.name} $receivedBytes/$totalBytes"
            return showAnswer(phase, receivedBytes, totalBytes)
        }

        override suspend fun dismiss(): Outcome<Unit> {
            calls += "dismiss"
            return dismissAnswer()
        }

        override suspend fun requestPermission(): Outcome<Boolean> {
            calls += "permission"
            return permissionAnswer()
        }
    }

    @Test
    fun `B-ANDR-101 permission reports the actual grant, and failures or a missing bridge report false`() = runTest {
        val sink = Sink()
        val notices = VoiceSetupNotices(sink, supportsVoice = true)
        assertTrue(notices.requestPermission())
        sink.permissionAnswer = { false.ok() }
        assertFalse(notices.requestPermission())
        sink.permissionAnswer = { Failure.Unexpected().failed() }
        assertFalse(notices.requestPermission())
        sink.permissionAnswer = { error("bridge threw") }
        assertFalse(notices.requestPermission())
        assertEquals(List(4) { "permission" }, sink.calls)
        assertFalse(VoiceSetupNotices(null, supportsVoice = true).requestPermission())
    }

    @Test
    fun `B-ANDR-101 a stalled permission prompt times out without blocking notices`() = runTest {
        val sink = Sink()
        val reply = CompletableDeferred<Boolean>()
        sink.permissionAnswer = { reply.await().ok() }
        val notices = VoiceSetupNotices(sink, supportsVoice = true)
        val permission = async { notices.requestPermission() }
        runCurrent()
        assertTrue(notices.show(VoiceSetupNoticePhase.Downloading, 10, 100))
        advanceTimeBy(61_000)
        assertFalse(permission.await())
        reply.complete(true) // a late grant cannot authorize the timed-out call
    }

    @Test
    fun `B-ANDR-101 an unsupported platform never calls the native side`() = runTest {
        val sink = Sink()
        val notices = VoiceSetupNotices(sink, supportsVoice = false)
        assertFalse(notices.requestPermission())
        assertFalse(notices.show(VoiceSetupNoticePhase.Downloading, 0, 100))
        notices.dismiss()
        assertEquals(emptyList(), sink.calls)
    }

    @Test
    fun `B-ANDR-102 every phase posts numeric progress only`() = runTest {
        val sink = Sink()
        val notices = VoiceSetupNotices(sink, supportsVoice = true)
        for (phase in VoiceSetupNoticePhase.entries) assertTrue(notices.show(phase, 20, 100))
        assertEquals(VoiceSetupNoticePhase.entries.map { "show ${it.name} 20/100" }, sink.calls)
    }

    @Test
    fun `B-ANDR-102 invalid and overrun progress is clamped to a safe range`() = runTest {
        val sink = Sink()
        val notices = VoiceSetupNotices(sink, supportsVoice = true)
        notices.show(VoiceSetupNoticePhase.Downloading, 200, 100)
        notices.show(VoiceSetupNoticePhase.Downloading, -1, 100)
        notices.show(VoiceSetupNoticePhase.Downloading, 3, -1)
        assertEquals(listOf("show Downloading 100/100", "show Downloading 0/100", "show Downloading 0/0"), sink.calls)
    }

    @Test
    fun `B-ANDR-102 denial and malformed answers stay false`() = runTest {
        val sink = Sink()
        val notices = VoiceSetupNotices(sink, supportsVoice = true)
        sink.showAnswer = { _, _, _ -> false.ok() }
        assertFalse(notices.show(VoiceSetupNoticePhase.Complete, 100, 100))
        sink.showAnswer = { _, _, _ -> Failure.UnreadableReply().failed() }
        assertFalse(notices.show(VoiceSetupNoticePhase.Complete, 100, 100))
    }

    @Test
    fun `B-ANDR-102 updates serialize so progress cannot overtake completion or dismissal`() = runTest {
        val sink = Sink()
        val visible = mutableListOf<String>()
        val progressReply = CompletableDeferred<Unit>()
        sink.showAnswer = { phase, _, _ ->
            if (phase == VoiceSetupNoticePhase.Downloading) progressReply.await()
            visible += phase.name
            true.ok()
        }
        sink.dismissAnswer = { visible += "dismissed"; Unit.ok() }
        val notices = VoiceSetupNotices(sink, supportsVoice = true)
        val progress = async { notices.show(VoiceSetupNoticePhase.Downloading, 20, 100) }
        val complete = async { notices.show(VoiceSetupNoticePhase.Complete, 100, 100) }
        val dismiss = async { notices.dismiss() }
        runCurrent()
        assertEquals(emptyList(), visible)
        progressReply.complete(Unit)
        progress.await(); complete.await(); dismiss.await()
        assertEquals(listOf("Downloading", "Complete", "dismissed"), visible)
    }

    @Test
    fun `B-ANDR-102 an error does not poison later updates or dismissal`() = runTest {
        val sink = Sink()
        sink.showAnswer = { _, _, _ -> error("unavailable") }
        sink.dismissAnswer = { error("unavailable") }
        val notices = VoiceSetupNotices(sink, supportsVoice = true)
        assertFalse(notices.show(VoiceSetupNoticePhase.Downloading, 0, 100))
        notices.dismiss()
        sink.showAnswer = { _, _, _ -> true.ok() }
        assertTrue(notices.show(VoiceSetupNoticePhase.Complete, 100, 100))
    }
}
