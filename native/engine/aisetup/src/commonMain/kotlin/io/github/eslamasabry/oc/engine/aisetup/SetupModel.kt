package io.github.eslamasabry.oc.engine.aisetup

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * What a setup adapter can do, independent of transport flavor (Dart `SetupSupport`). False means the UI shows
 * [reason]. [writeConfig] requires the atomic transaction facet; [assistant] is a server-enforced proposal-only
 * assistant, not ordinary chat access.
 */
public data class SetupSupport(
    val readConfig: Boolean = false,
    val writeConfig: Boolean = false,
    val mcpInventory: Boolean = false,
    val assistant: Boolean = false,
    val reason: SetupReason = SetupReason.NoSetupApis,
) {
    public companion object {
        /** Every setup flag defaults off: an unknown server never receives a write (B-AGENT-231). */
        public fun from(capabilities: ServerCapabilities): SetupSupport =
            SetupSupport(
                readConfig = capabilities.setupConfigRead,
                writeConfig = capabilities.setupConfigWrite,
                mcpInventory = capabilities.setupMcpInventory,
                assistant = capabilities.setupAssistantSession,
            )
    }
}

/** Dart `SetupFailureCode`: the coarse category of a failed setup command. */
public enum class SetupFailureCode {
    Offline, Unsupported, NeedsSignIn, Invalid, Conflict, Storage, Transport, Busy, Uncertain,
}

/**
 * Every app-authored setup sentence, as a value (the words live in the UI layer; the English is the Dart copy, noted
 * on each entry). Never server text.
 */
public enum class SetupReason {
    /** "This server does not expose setup configuration APIs." */
    NoSetupApis,

    /** "This server has no verified atomic configuration restore endpoint. Apply and Undo are unavailable." */
    NoAtomicRestore,

    /** "Reconnect to your server to refresh or apply changes." (the offline notice) */
    ReconnectToRefresh,

    /** "Read the current configuration first." */
    ReadFirst,

    /** "This server returns layered sources. Effective-config diffs are unavailable." */
    LayeredSources,

    /** "Choose between one and 32 changes." */
    ChangeCount,

    /** "Choose a supported configuration field." */
    UnsupportedField,

    /** "Choose a valid model or agent identifier." */
    InvalidIdentifier,

    /** "Choose an allow, ask, or deny permission rule." */
    InvalidPermission,

    /** "A new MCP proposal must specify its type and start disabled." */
    McpMustStartDisabled,

    /** "Choose a valid command for the local MCP server." */
    InvalidLocalCommand,

    /** "Choose an HTTPS or local loopback MCP URL without credentials." */
    InvalidMcpUrl,

    /** "Use the existing sign-in or secret entry flow for credentials." */
    UseSecretFlow,

    /** "A configuration value is required." */
    ValueRequired,

    /** "Review overlapping fields as separate proposals." */
    OverlappingFields,

    /** "This change would replace a containing setting. Review that setting separately before continuing." */
    ReplacesContainer,

    /**
     * "Only disabled remote MCP definitions can be changed here. Runtime connections and sign-in state are not
     * restored."
     */
    OnlyDisabledRemoteMcp,

    /** "The MCP definition is absent. Refresh and review a current definition." */
    McpAbsent,

    /** "Local MCP commands require a separate command review. They cannot be applied here." */
    LocalMcpNeedsReview,

    /**
     * "This setting has no reviewed reversible setup action. Use the existing server configuration or secure sign-in
     * flow."
     */
    NoReversibleAction,

    /** "Reconnect to your server before applying." */
    ReconnectBeforeApplying,

    /** "Review and confirm the current proposal first." */
    ReviewAndConfirm,

    /** "Configuration changed on the server. Refresh and review a new proposal." (also a host's CAS rejection) */
    ConfigChanged,

    /** "There is no confirmed change to undo in this setup session." */
    NothingToUndo,

    /** "Configuration changed after Apply. Refresh before making another change." */
    ChangedAfterApply,

    /** "The configuration change could not be verified." */
    NotVerified,

    /** "The change outcome is unknown. Refresh before trying again." */
    OutcomeUnknown,

    /** "The server returned an unsupported configuration snapshot." */
    UnsupportedSnapshot,

    /** "Setup is busy or closed." */
    BusyOrClosed,

    /** "Could not contact the setup service." */
    Transport,

    /** "Setup is closed or the selected server changed." */
    ScopeClosed,

    /** "Setup history is unavailable. Refresh before trying again." */
    HistoryUnavailable,

    /** "Reconnect to your server before continuing." */
    ReconnectToContinue,

    /** "Setup history could not be saved. Refresh before trying again." */
    HistoryNotSaved,

    /** "Sign in to this server to inspect setup." */
    SignInNeeded,
}

/**
 * The core [Failure] each setup category travels as (native rule: expected failures are Failure values). The exact
 * sentence is the snapshot's [SetupSnapshot.reason]; [setupCode] gives the Dart category back.
 */
internal fun SetupFailureCode.failure(): Failure =
    when (this) {
        SetupFailureCode.Offline -> Failure.Network(NetworkWhy.Offline)
        SetupFailureCode.Unsupported -> Failure.Unavailable(FeatureId("setupConfigWrite"))
        SetupFailureCode.NeedsSignIn -> Failure.SignIn(io.github.eslamasabry.oc.core.common.SignInWhy.PasswordRejected)
        SetupFailureCode.Invalid -> Failure.Refused(RefusalReason.ConsentMissing)
        SetupFailureCode.Conflict -> Failure.Conflict()
        SetupFailureCode.Storage -> Failure.StorageRefused()
        SetupFailureCode.Transport -> Failure.Unknown()
        SetupFailureCode.Busy -> Failure.Busy(BusyKind.SessionBusy)
        SetupFailureCode.Uncertain -> Failure.Refused(RefusalReason.DispatchUncertain)
    }

/** The Dart setup category of a failure a setup command returned. */
public fun Failure.setupCode(): SetupFailureCode =
    when (this) {
        is Failure.Network -> if (why == NetworkWhy.Offline) SetupFailureCode.Offline else SetupFailureCode.Transport
        is Failure.Refused ->
            when (reason) {
                RefusalReason.StaleScope, RefusalReason.ProfileClosing -> SetupFailureCode.Offline
                RefusalReason.DispatchUncertain -> SetupFailureCode.Uncertain
                else -> SetupFailureCode.Invalid
            }
        is Failure.Unavailable -> SetupFailureCode.Unsupported
        is Failure.SignIn -> SetupFailureCode.NeedsSignIn
        is Failure.Conflict -> SetupFailureCode.Conflict
        is Failure.StorageRefused -> SetupFailureCode.Storage
        is Failure.Busy -> SetupFailureCode.Busy
        else -> SetupFailureCode.Transport
    }

/**
 * Raw controller-only source snapshot (never UI or persistence). [config] is a detached deep copy: a caller's later
 * change to the maps or lists it was built from never reaches it, and JSON trees are read-only.
 */
public class SetupConfigRevision(
    public val targetId: String,
    public val revision: String,
    config: JsonObject,
) {
    public val config: JsonObject = freezeSetup(config) as JsonObject
}

/** Raw memory-only receipt. The opaque restore [undoHandle] stays on this boundary. */
public class SetupConfigCommit(
    public val before: SetupConfigRevision,
    public val after: SetupConfigRevision,
    public val undoHandle: String,
)

internal fun freezeSetup(value: JsonElement): JsonElement =
    when (value) {
        is JsonObject ->
            JsonObject(LinkedHashMap<String, JsonElement>().apply { for ((k, v) in value) put(k, freezeSetup(v)) })
        is JsonArray -> JsonArray(value.map(::freezeSetup))
        else -> value
    }

/** One MCP server as the gateway reports it; [status] is the raw wire word, normalised by the controller. */
public data class SetupMcpStatus(val name: String, val status: String)

/** A tool server's state in plain categories (Dart keeps the wire word; unknown words become [Unknown]). */
public enum class SetupToolStatus(public val wire: String) {
    Connected("connected"),
    Pending("pending"),
    Disabled("disabled"),
    Failed("failed"),
    NeedsAuth("needs_auth"),
    NeedsClientRegistration("needs_client_registration"),
    Unknown("unknown"),
    ;

    public companion object {
        public fun fromWire(raw: String): SetupToolStatus = entries.firstOrNull { it.wire == raw } ?: Unknown
    }
}

/** A tool server in a redacted snapshot. */
public data class SetupTool(val name: String, val status: SetupToolStatus)

/** Input only. Never render it; render [SetupDiff]. [value] is null for a removal. */
public data class SetupEdit(val path: List<String>, val value: JsonElement? = null, val remove: Boolean = false)

/** One reviewable change, already redacted. Absence and a present null are told apart by the *Present flags. */
public data class SetupDiff(
    val path: List<String>,
    val before: JsonElement?,
    val after: JsonElement?,
    val remove: Boolean,
    val beforePresent: Boolean = true,
    val afterPresent: Boolean = true,
)

/**
 * A reviewable proposal. Any change may affect future sessions; the UI says "Runtime activation is not verified.
 * Recheck server status after a change." and never claims an automatic restart.
 */
public data class SetupProposal(
    val id: String,
    val changes: List<SetupDiff>,
    val canApply: Boolean,
    val reason: SetupReason?,
)

public enum class SetupPhase {
    Idle, Loading, Applying, Verifying, Undoing, Uncertain, Ready, Empty, Offline, Unsupported, NeedsSignIn, Error,
}

/** What the controller publishes: redacted configuration and tools only. */
public data class SetupSnapshot(
    val phase: SetupPhase = SetupPhase.Idle,
    val config: JsonObject = JsonObject(emptyMap()),
    val servers: List<SetupTool> = emptyList(),
    val proposal: SetupProposal? = null,
    val reason: SetupReason? = null,
    val canUndo: Boolean = false,
)
