package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Show this request" (edge light plan §4): open [ref]'s conversation at the request [requestId] (a permission or a
 * question card). [seq] tells two requests for the same card apart; the page hands it back to
 * [AppNavigator.consumeRequestFocus] once it has scrolled there, so the card is shown once.
 */
public data class RequestFocus(val ref: ConversationRef, val requestId: String, val seq: Long)

/**
 * The one-off signal behind [AppNavigator.requestFocus]. It holds the latest request until it is consumed, replaced by
 * the next switch, or older than [LIFETIME], so a page that subscribes after the emit (a fresh entry, or a kept one
 * that is composed again) still receives it, and a page composed later never jumps to an old card. Main thread only.
 *
 * A navigator fake in a feature's tests delegates to one of these: `override val requestFocus get() = signal.state`
 * and `override fun consumeRequestFocus(seq: Long) = signal.consume(seq)`, then calls [emit] where the light would.
 * With no [clock] the request never ages; it still ends when consumed or replaced.
 */
public class RequestFocusSignal(private val clock: Clock? = null) {
    private val mutable = MutableStateFlow<RequestFocus?>(null)
    private var seq = 0L
    private var emittedAt: Instant? = null

    public val state: StateFlow<RequestFocus?> = mutable.asStateFlow()

    /** Replaces any request still waiting. */
    public fun emit(ref: ConversationRef, requestId: String): RequestFocus {
        seq += 1
        val next = RequestFocus(ref, requestId, seq)
        emittedAt = clock?.now()
        mutable.value = next
        return next
    }

    /** The page showed request [seq]; an older or unknown [seq] leaves a newer request waiting. */
    public fun consume(seq: Long) {
        if (mutable.value?.seq == seq) clear()
    }

    public fun clear() {
        mutable.value = null
        emittedAt = null
    }

    /** Drops the waiting request once it is [LIFETIME] old (the navigator checks on every navigation). */
    public fun expireIfOld() {
        val since = emittedAt ?: return
        val now = clock?.now() ?: return
        if (now - since >= LIFETIME) clear()
    }

    public companion object {
        public val LIFETIME: Duration = 10.seconds
    }
}

/** What a navigator without the signal (the many test fakes) answers: never a request. */
internal val NoRequestFocus: StateFlow<RequestFocus?> = MutableStateFlow<RequestFocus?>(null).asStateFlow()
