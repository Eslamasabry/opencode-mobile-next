package io.github.eslamasabry.oc.engine.voice.setup

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/** The voice-pack download notification's phases: numbers and a phase only, never arbitrary text. */
public enum class VoiceSetupNoticePhase { Downloading, Verifying, Complete, Failed }

/** The platform side (Android: one notification id for the download; desktop has none). */
public interface VoiceSetupNoticeSink {
    /** True when the system accepted the notification. */
    public suspend fun show(phase: VoiceSetupNoticePhase, receivedBytes: Long, totalBytes: Long): Outcome<Boolean>

    public suspend fun dismiss(): Outcome<Unit>

    /** POST_NOTIFICATIONS: the actual grant, never a guess. */
    public suspend fun requestPermission(): Outcome<Boolean>
}

/**
 * Port of Dart `AndroidVoiceSetupPlatform`'s notification half (B-ANDR-101 permission, B-ANDR-102 notices). Progress
 * is clamped into a safe range (total ≥ 0, 0 ≤ received ≤ total); denial, a malformed answer, an error, a missing sink
 * or an unsupported platform read false and never reach the platform; every mutation runs one at a time so a queued
 * progress update cannot overtake completion or dismissal, and an error never poisons the next update. A stalled
 * permission prompt gives up after [permissionTimeoutMs] without blocking the notices.
 */
public class VoiceSetupNotices(
    private val sink: VoiceSetupNoticeSink?,
    private val supportsVoice: Boolean,
    private val permissionTimeoutMs: Long = 60_000,
    private val noticeTimeoutMs: Long = 5_000,
) {
    private val lane = Mutex()

    public suspend fun show(phase: VoiceSetupNoticePhase, receivedBytes: Long, totalBytes: Long): Boolean = lane.withLock {
        val platform = sink?.takeIf { supportsVoice } ?: return@withLock false
        val total = totalBytes.coerceAtLeast(0)
        val received = receivedBytes.coerceIn(0, total)
        bounded(noticeTimeoutMs) { platform.show(phase, received, total) } == true
    }

    public suspend fun dismiss() {
        lane.withLock {
            val platform = sink?.takeIf { supportsVoice } ?: return@withLock
            bounded(noticeTimeoutMs) { platform.dismiss() }
        }
    }

    /** Not serialized with the notices: a stalled system prompt never blocks progress. */
    public suspend fun requestPermission(): Boolean {
        val platform = sink?.takeIf { supportsVoice } ?: return false
        return bounded(permissionTimeoutMs) { platform.requestPermission() } == true
    }

    private suspend fun <T> bounded(timeoutMs: Long, call: suspend () -> Outcome<T>): T? =
        try {
            when (val out = withTimeout(timeoutMs) { call() }) {
                is Outcome.Ok -> out.value
                is Outcome.Failed -> null
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
