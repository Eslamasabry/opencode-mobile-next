package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind

/**
 * Which agent conversations already failed to reopen on this connection (Flutter `_paResumeFailed`,
 * `phone_agents_routes.dart:335`). Kept in memory for the app's run only, as Flutter does: nothing is stored, so
 * no per-profile key and nothing for the profile deletion sweep. The next tap then asks "Start a new
 * conversation?" at once instead of failing again.
 */
internal class PaseoResumeMemory {
    private val failed = HashSet<String>()

    /**
     * A send to [sessionId] that failed. Only a conversation the helper no longer holds ([live] false) and a failure
     * that means "the runtime cannot continue it" is remembered; a dropped connection, a timeout or a stale request
     * says nothing about whether it can be reopened.
     */
    fun note(sessionId: String, kind: PaseoFailureKind, live: Boolean) {
        if (!live && kind in CANNOT_CONTINUE) failed += sessionId
    }

    /** Whether reopening [sessionId] already failed here and the helper does not hold it now. */
    fun failedBefore(sessionId: String, live: Boolean): Boolean = !live && sessionId in failed

    fun forget(sessionId: String) {
        failed -= sessionId
    }

    fun clear() = failed.clear()

    private companion object {
        val CANNOT_CONTINUE =
            setOf(PaseoFailureKind.NewChatRequired, PaseoFailureKind.Unavailable, PaseoFailureKind.NotFound)
    }
}
