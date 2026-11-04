package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** `oc.firstRun.notifyAsk` (Flutter `FirstRun.notifyAskKey`): `pending` until the question is answered. */
public interface NotifyAskStore {
    public fun pending(): Boolean

    public suspend fun answer(): Boolean

    public companion object {
        public const val KEY: String = "oc.firstRun.notifyAsk"
        public const val PENDING: String = "pending"
        public const val ANSWERED: String = "answered"
    }
}

/**
 * The per-server "needs you" consent (`:engine:settings` `InFlowConsentStore.requestNeedsYouPreset` / `answer(…,
 * NeedsYouNotifications, …)`). [claim] is Ok(true) when the question is owed on that server and was claimed for it.
 */
public interface NeedsYouConsent {
    public suspend fun claim(profileId: String): Outcome<Boolean>

    public suspend fun answer(profileId: String, allow: Boolean): Outcome<Unit>
}

/** What the offer reads about the app around it. */
public interface NotifyOfferHost {
    /** An isolated (phone-account-only or preview) connection never asks. */
    public val isolated: Boolean

    /** The saved server the conversation is on, or null without one. */
    public val profileId: String?

    /** The platform can post notifications at all (Flutter `supportsNotifications`). */
    public fun supportsNotifications(): Boolean

    /** Turns on notifications for requests (`oc.notify.requests`), the first half of "Notify me". */
    public suspend fun enableRequestNotifications()
}

/** Why "Notify me" did not turn notifications on. */
public enum class NotifyOfferFailure {
    /** The answer could not be kept, so nothing was turned on. */
    SaveFailed,

    /** The background connection did not start (for example Android's prompt was refused). */
    NotEnabled,
}

public data class NotifyOfferState(
    val working: Boolean = false,
    val failure: NotifyOfferFailure? = null,
    /** Live mode's own reason, for [NotifyOfferFailure.NotEnabled]. */
    val failureError: LiveModeError? = null,
    /** Bumped when [FirstReplyNotifyOffer.showFor] may answer differently. */
    val revision: Int = 0,
)

/**
 * "Notify you when the agent needs you?" (port of `FirstReplyNotifyOffer`): asked once, only after the first reply
 * of a new person's first run has completed. Not asked when live mode is already on (that answers it), nor where the
 * device cannot notify (still owed). "Notify me" turns on request notifications and live mode, which raises Android's
 * notification prompt; a refusal there is an answer too. Confined with its [scope].
 */
public class FirstReplyNotifyOffer(
    private val host: NotifyOfferHost,
    private val liveMode: LiveModeController,
    private val ask: NotifyAskStore,
    private val consent: NeedsYouConsent,
    private val scope: CoroutineScope,
) {
    private var claimed: String? = null
    private var claiming = false
    private var settling = false
    private var answered = false
    private var disposed = false
    private val _state = MutableStateFlow(NotifyOfferState())
    public val state: StateFlow<NotifyOfferState> = _state.asStateFlow()

    /** Still owed to the person on this device; the one-time tips stay quiet until it is answered. */
    public fun pending(): Boolean =
        !host.isolated && host.supportsNotifications() && liveMode.support.supported() && ask.pending()

    /** Whether the question shows now. Pure for the caller: the claim and the quiet answer happen after it. */
    public fun showFor(replyCompleted: Boolean): Boolean {
        if (answered || !replyCompleted || !pending()) return false
        // Already on (turned on in Settings between the reply and now): answered elsewhere, so not asked.
        if (liveMode.state.value.enabled) {
            if (!settling) {
                settling = true
                scope.launch { decline() }
            }
            return false
        }
        // Without a saved server the question stays the device's one-time question.
        if (claimed == null && host.profileId != null) {
            if (!claiming) scope.launch { claim() }
            return false
        }
        return true
    }

    private suspend fun claim() {
        val profileId = host.profileId
        if (claiming || profileId == null) return
        claiming = true
        when (val owed = consent.claim(profileId)) {
            is Outcome.Ok ->
                if (owed.value) {
                    if (disposed) return
                    claimed = profileId
                    changed()
                } else {
                    // Already asked on this server (or answered in Settings): the first-run question is settled.
                    answered = true
                    changed()
                    ask.answer()
                }
            // Storage that cannot be read shows nothing: no answer could be kept.
            is Outcome.Failed -> {
                answered = true
                changed()
            }
        }
    }

    /** "Notify me". */
    public suspend fun accept() {
        if (_state.value.working) return
        _state.value = _state.value.copy(working = true)
        // Saved first: nothing is turned on without a kept yes.
        val server = claimed
        if (server != null && consent.answer(server, allow = true) is Outcome.Failed) {
            answered = true
            _state.value = _state.value.copy(working = false, failure = NotifyOfferFailure.SaveFailed)
            changed()
            return
        }
        host.enableRequestNotifications()
        val enabled = liveMode.setEnabled(true)
        // Asked once: a refusal at Android's own prompt is an answer too.
        ask.answer()
        answered = true
        _state.value =
            _state.value.copy(
                working = false,
                failure = if (enabled) _state.value.failure else NotifyOfferFailure.NotEnabled,
                failureError = if (enabled) _state.value.failureError else liveMode.state.value.lastError,
            )
        changed()
    }

    /** "Not now": remembered, and nothing is turned on. */
    public suspend fun decline() {
        answered = true
        changed()
        // Unsaved, it stays "Not answered" on What runs by itself.
        claimed?.let { consent.answer(it, allow = false) }
        ask.answer()
    }

    public fun dismissFailure() {
        _state.value = _state.value.copy(failure = null, failureError = null)
        changed()
    }

    public fun dispose() {
        disposed = true
    }

    private fun changed() {
        if (!disposed) _state.value = _state.value.copy(revision = _state.value.revision + 1)
    }
}
