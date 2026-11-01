package io.github.eslamasabry.oc.core.common

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.jvm.JvmInline

/**
 * Expected failures as values (ADR §9.6). A closed hierarchy, frozen by S0-CORE (contracts/s1.md §Failure).
 *
 * Two families, one tree:
 * - app-level categories (plan 03 §10.1): refusals the app authors, feature absence, device, storage, folders;
 * - [Net], every transport/status outcome (plan 04 §3.8), translated once in :core:net / :core:net-okhttp.
 *
 * Plan 03's flat network categories (Network, TimedOut, Certificate, SignIn, NotFound, Conflict, Busy, Server,
 * Rejected, UnreadableReply) are replaced by [Net] so one input has exactly one value (S0-CORE deviation D-C1).
 * `:ui:words` maps every leaf with an exhaustive `when`; adding a leaf is a contract change.
 */
public sealed interface Failure {
    /** Technical text for the Details fold ONLY (ADR §9.6). Null for app-authored refusals. Never the main message. */
    public val details: Redacted?

    /** The app refused on purpose; [reason] has exactly one sentence in :ui:words. */
    public data class Refused(val reason: RefusalReason, override val details: Redacted? = null) : Failure

    /** The capability is absent on this server or platform (the Dart ProductRepository default). Never latched. */
    public data class Unavailable(val feature: FeatureId, override val details: Redacted? = null) : Failure

    /** A staged revert blocks the action (Flutter `stagedRevert`). */
    public data class StagedRevert(override val details: Redacted? = null) : Failure

    /** The four Flutter folder* categories. */
    public data class Folder(val problem: FolderProblem, override val details: Redacted? = null) : Failure

    /** The Paseo host computer did not answer (Flutter `computer`). */
    public data class ComputerDidNotAnswer(override val details: Redacted? = null) : Failure

    /** A platform API refused (camera, picker, notification, service start…). */
    public data class Device(override val details: Redacted? = null) : Failure

    /** Disk, preferences commit, Keystore (audit 03 BLOCKER 4: storage needs its own kinds). */
    public data class Storage(val problem: StorageProblem, override val details: Redacted? = null) : Failure

    public data class ProjectGit(override val details: Redacted? = null) : Failure

    /** Kept while D7 = migrate-out. */
    public data class Termux(override val details: Redacted? = null) : Failure

    /** A bug path; still shown in plain words. */
    public data class Unexpected(override val details: Redacted? = null) : Failure

    /** The generic connectivity line, when nothing more is known. */
    public data class Unknown(override val details: Redacted? = null) : Failure

    /**
     * Transport and status failures (plan 04 §3.8). Server prose only ever lands in [details]; the server's symbolic
     * tag is kept for logic as [WireTag] and never shown.
     */
    public sealed interface Net : Failure {
        /** No default network and the host is not loopback. */
        public data class Offline(override val details: Redacted? = null) : Net

        public data class Unreachable(val why: Why, override val details: Redacted? = null) : Net {
            public enum class Why { Dns, Refused, Timeout, Reset, NoRoute }
        }

        public data class TrustRefused(val why: Why, override val details: Redacted? = null) : Net {
            public enum class Why {
                CleartextNotConfirmed, OriginChanged, CredentialsInUrl, PinNeedsHttps,
                CertificateChanged, CertificateExpired, CertificateUntrusted,
            }
        }

        public data class SignInNeeded(val why: Why, override val details: Redacted? = null) : Net {
            public enum class Why { PasswordMissing, PasswordRejected, TokenMissing, TokenRejected, HostNotAllowed }
        }

        public data class ServerBusy(val kind: Kind, val tag: WireTag? = null, override val details: Redacted? = null) : Net {
            public enum class Kind { Starting, Stopping, Failed, Overloaded, RateLimited }
        }

        public data class NotFound(val tag: WireTag? = null, val requestId: String? = null, override val details: Redacted? = null) : Net

        public data class Conflict(val tag: WireTag? = null, val requestId: String? = null, override val details: Redacted? = null) : Net

        /** Any other 4xx. */
        public data class Rejected(
            val status: Int,
            val tag: WireTag?,
            val extras: WireExtras,
            override val details: Redacted? = null,
        ) : Net

        /** ≥ 500. */
        public data class ServerFailed(val status: Int, val tag: WireTag? = null, override val details: Redacted? = null) : Net

        /** Undecodable or over-cap body. */
        public data class UnreadableReply(override val details: Redacted? = null) : Net

        /** A mutation may or may not have landed (WS timeout, link lost after send). Never retried automatically. */
        public data class DeliveryUnknown(override val details: Redacted? = null) : Net

        /** The answer belongs to an older connection epoch. */
        public data class StaleRequest(override val details: Redacted? = null) : Net

        /** 404 (non-session) / 405 / 501 on an optional feature: latched off for this graph (plan 04 §11.5). */
        public data class NotSupported(val feature: FeatureId, override val details: Redacted? = null) : Net

        public data class Incompatible(val kind: Kind, override val details: Redacted? = null) : Net {
            public enum class Kind { LegacyOpenCode1, UnsupportedServerVersion, ProtocolMismatch }
        }
    }
}

public enum class FolderProblem { NotInstalled, Missing, Denied, Linked }

/** Storage kinds the data layer needs (plan 05 §2: refused commits and unreadable values are distinct). */
public enum class StorageProblem { WriteRefused, InvalidValue, Unreadable, Full, KeystoreUnavailable, Unknown }

/** App-authored refusal reasons. Append-only; each has exactly one sentence in :ui:words (exhaustive when). */
public enum class RefusalReason {
    ProfileClosing, ProfileDeleted, StaleScope, ConsentMissing, CleartextNotConfirmed, PasswordMissing,
    AlreadyAnswered, DispatchUncertain, OfflineQueueFull, MigrationPending, NotOnThisPhone,
    // Added by S0-CORE (append-only from here):
    InvalidAddress, CredentialsInAddress, PinNeedsHttps,
}

/** Names a ServerCapabilities/PlatformCapabilities flag exactly as spelled in the Kotlin data class. */
@JvmInline public value class FeatureId(public val name: String)

/** The server's symbolic error tag (`_tag` or `code`), for logic only; never shown. */
@JvmInline public value class WireTag(public val raw: String)

/** Extra symbolic fields of a rejection (e.g. a form field name). Never server prose. */
public data class WireExtras(val fields: ImmutableMap<String, String>) {
    public companion object {
        public val None: WireExtras = WireExtras(persistentMapOf())
    }
}
