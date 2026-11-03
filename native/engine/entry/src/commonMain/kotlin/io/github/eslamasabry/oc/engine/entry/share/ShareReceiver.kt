package io.github.eslamasabry.oc.engine.entry.share

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Text shared into the app (Flutter `ShareIntent`, lib/platform/share_intent.dart): the cold-start share and live
 * shares land in [pending] trimmed; blank shares and a missing platform are ignored. A newer live share, or a [take],
 * wins over a cold-start read still in flight. After [dispose] nothing changes [pending] again and [take] answers
 * null; disposing a receiver that never started leaves an active one alone (B-ANDR-076).
 *
 * Main thread only (the platform delivers shares there). The text stays in memory; it is never logged.
 */
public class ShareReceiver(
    private val inbox: ShareInbox?,
) {
    private val mutablePending = MutableStateFlow<String?>(null)

    /** The share waiting to be used, or null. */
    public val pending: StateFlow<String?> = mutablePending.asStateFlow()

    private var registration: ShareRegistration? = null
    private var disposed = false
    private var started = false

    /** Bumped by every live share and every take: a cold-start answer from before them is stale. */
    private var generation = 0

    public val isDisposed: Boolean get() = disposed

    /** Listens for live shares, then drains the share that launched the app. Once; inert without a platform. */
    public suspend fun start() {
        val inbox = inbox ?: return
        if (disposed || started) return
        started = true
        registration = inbox.listen(::onShared)
        val before = generation
        val read = attempt({ Failure.Unexpected() }) { inbox.consumeSharedText() }
        if (disposed || generation != before) return
        val text = ((read as? Outcome.Ok)?.value as? Outcome.Ok)?.value?.trim()
        if (!text.isNullOrEmpty()) mutablePending.value = text
    }

    private fun onShared(raw: String) {
        if (disposed) return
        val text = raw.trim()
        if (text.isEmpty()) return
        generation++
        mutablePending.value = text
    }

    /** Hands the pending share over once and clears it. */
    public fun take(): String? {
        if (disposed) return null
        val text = mutablePending.value ?: return null
        generation++
        mutablePending.value = null
        return text
    }

    /** Stops every callback. Only this receiver's own registration is cancelled. */
    public fun dispose() {
        if (disposed) return
        disposed = true
        registration?.cancel()
        registration = null
    }
}
