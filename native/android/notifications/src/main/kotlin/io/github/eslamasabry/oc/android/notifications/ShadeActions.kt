package io.github.eslamasabry.oc.android.notifications

import android.app.RemoteInput
import android.content.Intent
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId

/** A button's decision. Wire names are the Flutter ones (`allow`, `deny`, `open`, `reply`, `option<N>`). */
public enum class ShadeDecision(public val wire: String) {
    Allow("allow"),
    Deny("deny"),
    Open("open"),

    /** Legacy RemoteInput reply: accepted only from Flutter-era alerts still in the shade (plan 11 §10.4). */
    Reply("reply"),

    /** Legacy option button (`option0`…): answers with its own label, exactly like a typed reply. */
    Option("option"),
    ;

    /** Android asks for the unlocked phone first (CR16-7, D16-3, B-SEC-184). Reject stays without (harmless). */
    public val needsUnlock: Boolean get() = this == Allow || this == Reply || this == Option

    public companion object {
        public fun fromWire(value: String): ShadeDecision? =
            if (value.startsWith("option")) Option else entries.firstOrNull { it.wire == value }
    }
}

/** A tap on an alert button, read from the frozen extras only (never arbitrary extras; map T3). */
public data class ShadeTap(
    val kind: AlertKind,
    val sessionId: String,
    val key: String,
    val decision: ShadeDecision,
    val requestId: String,
    val profileId: String,
    /** Legacy reply text or the legacy option's label. Never logged. */
    val replyText: String?,
) {
    override fun toString(): String = "ShadeTap(kind=$kind, decision=$decision, request=${requestId.isNotEmpty()})"

    public companion object {
        /** Null when the tap is malformed (no kind, session or decision): nothing happens (Flutter parity). */
        public fun read(intent: Intent): ShadeTap? {
            val kind = AlertKind.fromWire(intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND)) ?: return null
            val session = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID).orEmpty()
            val rawDecision = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_DECISION).orEmpty()
            if (session.isBlank() || rawDecision.isBlank()) return null
            val decision = ShadeDecision.fromWire(rawDecision) ?: return null
            val reply =
                when (decision) {
                    ShadeDecision.Option -> intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_OPTION_TEXT)
                    ShadeDecision.Reply ->
                        RemoteInput.getResultsFromIntent(intent)?.getCharSequence(FrozenExtras.REMOTE_INPUT_REPLY)?.toString()
                    else -> null
                }
            return ShadeTap(
                kind = kind,
                sessionId = session,
                key = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_KEY).orEmpty(),
                decision = decision,
                requestId = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID).orEmpty(),
                profileId = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID).orEmpty(),
                replyText = reply,
            )
        }
    }
}

/** What the shade answers. [Text] exists only for the legacy reply and option paths. */
public sealed interface ShadeReply {
    public data object AllowOnce : ShadeReply

    public data object Reject : ShadeReply

    public data class Text(val text: String) : ShadeReply {
        override fun toString(): String = "Text(<${text.length}>)"
    }
}

/** One answer bound to the **exact** pending request (B-AGENT-031, B-SEC-183). */
public data class ShadeAnswer(
    val profile: ProfileId,
    val session: SessionId,
    val request: RequestId,
    val kind: AlertKind,
    val reply: ShadeReply,
)

/**
 * The seam to the connection engine (plan 11 §10.6, CR11-6): answer through the live connection of that profile,
 * else `ConnectionRegistry.oneShot(profile, NotificationAction)` — `health()` first, then that one pending request,
 * once/reject only. Implemented by the connection/requests engines and bound in the app graph; never by this module.
 *
 * Expected results: `Ok` = answered; `NotFound` = the request is gone (answered elsewhere or timed out on the server,
 * B-CHAT-076); `Refused(AlreadyAnswered)` = the in-app tap won the race (B-CHAT-074); `Refused(StaleScope)` = the id no
 * longer matches the pending request. Anything else is a failure to answer.
 */
public fun interface ShadeAnswerer {
    public suspend fun answer(answer: ShadeAnswer): Outcome<Unit>
}

/** The seam for **Stop the reply** (live connection, else oneShot; plan 11 §10.6). */
public fun interface ReplyStopper {
    public suspend fun stop(conversation: ConversationRef): Outcome<Unit>
}

/** Whether the phone is locked now (`KeyguardManager.isDeviceLocked`) and the SDK level, for CR16-7. */
public interface DeviceLock {
    public val sdkInt: Int

    public fun isLocked(): Boolean
}

/** What the body did with a tap (for tests and the receiver's log line; carries no words). */
public enum class ShadeOutcome {
    Answered,
    /** The request was already gone or answered elsewhere: the alert was dismissed quietly. */
    DismissedGone,
    /** Not answered; the alert was reposted with Open only (stale id, locked phone, failure, timeout). */
    RepostedOpen,
    /** A second tap for a request already being answered. */
    IgnoredDuplicate,
    /** Malformed tap, an Open decision (activities handle those), or an empty legacy reply. */
    Ignored,
    StopSent,
    StopFailed,
    RowDismissed,
}

internal fun Failure.meansGone(): Boolean =
    this is Failure.NotFound || (this is Failure.Refused && reason == RefusalReason.AlreadyAnswered)

internal fun Failure.meansStale(): Boolean = this is Failure.Refused && reason == RefusalReason.StaleScope

/** Null for legacy status intents which cannot unambiguously select a connection. */
internal fun Intent.statusConversation(): ConversationRef? {
    val profile = getStringExtra(FrozenExtras.EXTRA_STATUS_PROFILE)?.takeIf { it.isNotBlank() } ?: return null
    val session = getStringExtra(FrozenExtras.EXTRA_STATUS_SESSION)?.takeIf { it.isNotBlank() } ?: return null
    return ConversationRef(ProfileId(profile), SessionId(session))
}

internal fun Intent.putStatusConversation(conversation: ConversationRef?) {
    if (conversation == null) return
    putExtra(FrozenExtras.EXTRA_STATUS_PROFILE, conversation.profileId.raw)
    putExtra(FrozenExtras.EXTRA_STATUS_SESSION, conversation.sessionId.raw)
}
