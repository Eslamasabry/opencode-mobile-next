package io.github.eslamasabry.oc.core.common

import io.github.eslamasabry.oc.core.common.ids.RequestId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.jvm.JvmInline

/**
 * Expected failures as values (ADR §9.6). ONE flat, closed hierarchy (plan 03 §10.1, owner 03; plan 04 §3.8 adds the
 * transport detail fields and [Incompatible]; plan 05 adds [StorageRefused] and [SecretUnavailable]; plan 10 C10-1
 * adds [Media]; plan 11 CR11-1 adds [Runtime]). No nested sub-hierarchy, so a `when` over Failure stays one flat
 * exhaustive list, and `:ui:words` has exactly one sentence per subtype. Adding a subtype is a contract change.
 *
 * INTEGRATE-W1 replaced S0-CORE's `Failure.Net` family and `Storage(StorageProblem)` with this shape, because plan 03
 * §10.1 and plan 04 §3.8 ("this file does not add a Net sub-hierarchy") both say so. The plan 04 §3.8 table maps every
 * transport input to exactly one subtype below.
 */
public sealed interface Failure {
    /** Technical text for the Details fold ONLY (ADR §9.6). Null for app-authored refusals. Never the main message. */
    public val details: Redacted?

    // ---- App-authored

    /** The app refused on purpose; [reason] has exactly one sentence in :ui:words. */
    public data class Refused(val reason: RefusalReason, override val details: Redacted? = null) : Failure

    /**
     * The capability is absent on this server or platform (the Dart ProductRepository default), or a server answered
     * 404 (non-session) / 405 / 501 for an optional feature, which the connection graph latches (plan 04 §11.5).
     */
    public data class Unavailable(val feature: FeatureId, override val details: Redacted? = null) : Failure

    // ---- Transport and server (detail fields and the input table: plan 04 §3.8)

    /** No default network for a Public/Tailnet host, DNS, refused, reset, no route. */
    public data class Network(val why: NetworkWhy = NetworkWhy.Unknown, override val details: Redacted? = null) : Failure

    /** Network timeouts, 408, and local waits (FGS acknowledgement, the migration gate). */
    public data class TimedOut(override val details: Redacted? = null) : Failure

    public data class Certificate(val why: CertificateWhy, override val details: Redacted? = null) : Failure

    public data class SignIn(val why: SignInWhy, override val details: Redacted? = null) : Failure

    public data class NotFound(
        val tag: WireTag? = null,
        val requestId: RequestId? = null,
        override val details: Redacted? = null,
    ) : Failure

    public data class Conflict(
        val tag: WireTag? = null,
        val requestId: RequestId? = null,
        override val details: Redacted? = null,
    ) : Failure

    public data class Busy(
        val kind: BusyKind = BusyKind.Overloaded,
        val tag: WireTag? = null,
        override val details: Redacted? = null,
    ) : Failure

    /** ≥ 500. */
    public data class Server(val status: Int?, val tag: WireTag? = null, override val details: Redacted? = null) : Failure

    /** Any other 4xx (and 3xx: redirects are never followed). */
    public data class Rejected(
        val status: Int?,
        val tag: WireTag? = null,
        val extras: WireExtras = WireExtras.None,
        override val details: Redacted? = null,
    ) : Failure

    public data class Incompatible(val kind: IncompatibleKind, override val details: Redacted? = null) : Failure

    /** The Paseo host computer did not answer (Flutter `computer`). */
    public data class ComputerDidNotAnswer(override val details: Redacted? = null) : Failure

    /** Undecodable or over-cap body. */
    public data class UnreadableReply(override val details: Redacted? = null) : Failure

    // ---- Storage (plan 05)

    /** A preferences commit, disk write or file I/O was refused, or a value the store must not write was offered. */
    public data class StorageRefused(override val details: Redacted? = null) : Failure

    /** The Keystore (or the secret file) could not give the secret back: "enter the password again". */
    public data class SecretUnavailable(override val details: Redacted? = null) : Failure

    // ---- Media (plan 10 §4.1) and the built-in Linux (plan 11 §5.5)

    public data class Media(val problem: MediaProblem, override val details: Redacted? = null) : Failure

    public data class Runtime(val problem: RuntimeProblem, override val details: Redacted? = null) : Failure

    // ---- Platform and project

    /** A platform API refused (camera, picker, notification, service start…). */
    public data class Device(override val details: Redacted? = null) : Failure

    /** The four Flutter folder* categories. */
    public data class Folder(val problem: FolderProblem, override val details: Redacted? = null) : Failure

    public data class ProjectGit(override val details: Redacted? = null) : Failure

    /** A staged revert blocks the action (Flutter `stagedRevert`). */
    public data class StagedRevert(override val details: Redacted? = null) : Failure

    /** Kept while D7 = migrate-out. */
    public data class Termux(override val details: Redacted? = null) : Failure

    /** A bug path; still shown in plain words. */
    public data class Unexpected(override val details: Redacted? = null) : Failure

    /** The generic connectivity line, when nothing more is known. */
    public data class Unknown(override val details: Redacted? = null) : Failure
}

/** Plan 04 §3.8. */
public enum class NetworkWhy { Offline, Dns, Refused, Reset, NoRoute, Unknown }

/** Plan 04 §3.8: pin mismatch, matching pin outside validity, PKI failure without a pin. */
public enum class CertificateWhy { Changed, Expired, Untrusted }

/** Plan 04 §3.8. Only PasswordRejected / TokenRejected set `passwordRejected` (Flutter `_noteAuthFailure`). */
public enum class SignInWhy { PasswordMissing, PasswordRejected, TokenMissing, TokenRejected, HostNotAllowed, Forbidden }

/** Plan 04 §3.8. */
public enum class BusyKind { Starting, Stopping, Failed, Overloaded, RateLimited, SessionBusy }

/** Plan 04 §3.8. */
public enum class IncompatibleKind { LegacyOpenCode1, UnsupportedServerVersion, ProtocolMismatch }

public enum class FolderProblem { NotInstalled, Missing, Denied, Linked }

/** Plan 10 §4.1 (C10-1). Append-only; each value has one sentence in :ui:words `strings_media_failures.xml`. */
public enum class MediaProblem {
    // microphone and capture
    MicDenied, MicDeniedForever, MicBusy, MicSilenced, NoMicrophone, NoAudioCaptured, RecordingInterrupted, LeftTheApp,
    // recognizer
    SpeechModelMissing, SpeechModelBroken, ChunkNotTranscribed,
    // packs and downloads
    PackAbiUnsupported, PackMemoryTooSmall, PackStorageTooSmall, MemoryUnknown, DownloadOffline, DownloadBusy,
    DownloadHttpsRequired, DownloadClosed, DownloadRedirect, DownloadUnsafeRedirect, DownloadNoResponse, DownloadTimedOut,
    DownloadChecksum, DownloadVerification, DownloadHttp, DownloadLength, DownloadIncomplete, DownloadNoSpace,
    // read aloud
    ReadAloudUnsupported, NoOfflineVoice, SpeechEngineUnavailable, ReadAloudTooLong, ReadAloudBusy,
    // photos and attachments
    PhotoTooLarge, PhotoUnsupported, PhotoStorage, PhotoPending, PhotoUnavailable, CameraDenied, CameraDeniedForever,
    AttachmentTooLarge, AttachmentsTotalTooLarge, DraftFull, AttachmentUnsupported, OfficeUnreadable, OfficeEmpty,
    ClipboardImageTooLarge, AttachmentContentMissing, RemoteAttachmentPreview,
    // previews
    PdfEncrypted, PdfInvalid, PdfTooLarge, PdfNeedsAndroid10, PdfTimedOut, PdfFailed, SvgNotStatic, FileNotShowable,
    // save, share, open
    SaveFailed, NoAppToOpen, ShareUnavailable, WifiShareNoWifi, WifiSharePrivateFile, WifiShareUnavailable, WifiShareEnded,
    // camera
    NoCamera, CameraFailed,
}

/** Plan 11 §5.5 (CR11-1). Append-only. */
public enum class RuntimeProblem {
    OwnershipUnknown, SlotBusy, RecoveryUnavailable, IdleResumeUnavailable, IdleResumeStale, SetupPersistence,
    InvalidProfile, ConfirmationRequired, StorageUnavailable, RemovalFailed, WorkLeaseInvalid, ServerStartUnavailable,
    NotInstalled, Installing, ComponentRecoveryRequired, ProtectionUnavailable, Generic,
}

/** App-authored refusal reasons (plan 03 §10.1). Append-only; each has exactly one sentence in :ui:words. */
public enum class RefusalReason {
    ProfileClosing, ProfileDeleted, StaleScope, ConsentMissing, CleartextNotConfirmed, PasswordMissing,
    AlreadyAnswered, DispatchUncertain, OfflineQueueFull, MigrationPending, NotOnThisPhone,
    OriginChanged, CredentialsInUrl, PinNeedsHttps, HostNotAllowedForBackend, // plan 04 §3.8
    BackgroundLimit, NotificationsBlocked, PhoneLocked, // plan 11 CR11-2
    UndoUnresolved, // plan 09 §4
    /** Not in plan 03's list: AddressRules.normalize/normalizePin refuse an unparseable address (S0-CORE). */
    InvalidAddress,
}

/** Names a ServerCapabilities/PlatformCapabilities flag exactly as spelled in the Kotlin data class. */
@JvmInline public value class FeatureId(public val name: String)

/** The server's symbolic error tag (`_tag` or `code`), for logic only; never shown. */
@JvmInline public value class WireTag(public val raw: String)

/** Extra symbolic fields of a rejection (e.g. a form field name or a size limit). Never server prose. */
public data class WireExtras(val fields: ImmutableMap<String, String>) {
    public companion object {
        public val None: WireExtras = WireExtras(persistentMapOf())
    }
}
