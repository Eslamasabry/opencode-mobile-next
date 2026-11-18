package io.github.eslamasabry.opencode_mobile

import java.util.concurrent.TimeUnit

/**
 * When a Claude Code sign-in check is held back beside a Claude Code CLI of the same profile (`agentBusy`).
 *
 * Why the hold-back exists (signin-truth, 6f401d96bb): a check is a second CLI on the same sign-in, and a CLI whose
 * stored token has run out refreshes it. Two refreshes at the same moment can lose the new token, and only Claude's
 * own refresh lock guards that. So no check starts while a conversation's CLI runs.
 *
 * Sign-in page (owner, 2026-11-18): the chat that offers "Sign in again" keeps its Claude Code program loaded, so the
 * check right after "Login successful." was always held back, and the page said "isn't signed in yet". That check
 * cannot start a refresh: the person's own sign-in has just written a new token, valid for hours. So the page's check
 * ([afterSignIn]) runs beside a loaded CLI when the stored sign-in was written within [FRESH_MILLIS]. Any other
 * check, and the page's own check of an older sign-in, is still held back.
 */
internal object PhoneAgentAuthHoldBack {
    /** A sign-in written this recently holds a token far from running out (Claude's last for hours). */
    val FRESH_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)

    /**
     * Whether the check for [agentId] is held back. [writtenMillisAgo] is how long ago the profile's stored Claude
     * sign-in was written, or null when there is none (or it could not be read as a plain file).
     */
    fun holdsBack(agentId: String, cliRunning: () -> Boolean, afterSignIn: Boolean, writtenMillisAgo: () -> Long?): Boolean {
        if (agentId != "claude") return false
        if (afterSignIn && fresh(writtenMillisAgo())) return false
        return cliRunning()
    }

    /** Written within [FRESH_MILLIS] and not in the future (a clock that moved back proves nothing). */
    fun fresh(writtenMillisAgo: Long?): Boolean = writtenMillisAgo != null && writtenMillisAgo in 0..FRESH_MILLIS
}
