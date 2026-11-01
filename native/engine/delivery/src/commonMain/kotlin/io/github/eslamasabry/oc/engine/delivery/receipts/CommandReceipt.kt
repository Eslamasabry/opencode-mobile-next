package io.github.eslamasabry.oc.engine.delivery.receipts

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/** Receipt means admission, never completion of the assistant's work (Dart `CommandReceiptState`). */
public enum class CommandReceiptState(public val wire: String) {
    Sent("sent"),
    Uncertain("uncertain"),
    Confirmed("confirmed"),
    Failed("failed"),
    ;

    public val terminal: Boolean get() = this == Confirmed || this == Failed

    public companion object {
        public fun parse(wire: Any?): CommandReceiptState? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * One journal entry (port of `lib/domain/command_receipts.dart` `CommandReceipt`). Metadata only: never a prompt body,
 * credential or raw error (B-STATE-009). [commandId] is the local identity (`queue:<entryId>`) and never goes on the
 * wire; [receiptId] is the client message id that does (plan 04 §7.13).
 */
public data class CommandReceipt(
    val commandId: String,
    val receiptId: String,
    val sessionId: String,
    val tabId: String,
    val scope: String,
    val createdAt: Long,
    val state: CommandReceiptState,
) {
    public fun withState(state: CommandReceiptState): CommandReceipt = copy(state = state)

    /** Same command, same server and tab: everything but the state. */
    public fun sameIdentity(other: CommandReceipt): Boolean =
        commandId == other.commandId && receiptId == other.receiptId && sessionId == other.sessionId &&
            tabId == other.tabId && scope == other.scope && createdAt == other.createdAt

    internal fun toJson(): LinkedHashMap<String, Any?> =
        linkedMapOf(
            "commandID" to commandId,
            "receiptID" to receiptId,
            "sessionID" to sessionId,
            "tabID" to tabId,
            "scope" to scope,
            "createdAt" to createdAt,
            "state" to state.wire,
        )

    /** True when Dart's `CommandReceipt.fromJson(toJson())` would accept it. */
    internal val valid: Boolean
        get() = OPAQUE.matches(commandId) && OPAQUE.matches(receiptId) && OPAQUE.matches(sessionId) &&
            OPAQUE.matches(tabId) && SCOPE.matches(scope) && createdAt >= 0

    public companion object {
        private val OPAQUE = Regex("^[a-zA-Z0-9_:-]{1,256}$")
        private val SCOPE = Regex("^[a-f0-9]{64}$")

        /** Dart `CommandReceipt.fromJson`: exactly seven keys, opaque ids, a SHA-256 scope, an int ≥ 0, a known state. */
        internal fun fromJson(json: Any?): CommandReceipt? {
            if (json !is Map<*, *> || json.size != 7) return null
            val createdAt = json["createdAt"] as? Long ?: return null
            val state = CommandReceiptState.parse(json["state"]) ?: return null
            fun opaque(key: String): String? = (json[key] as? String)?.takeIf { OPAQUE.matches(it) }
            val receipt =
                CommandReceipt(
                    commandId = opaque("commandID") ?: return null,
                    receiptId = opaque("receiptID") ?: return null,
                    sessionId = opaque("sessionID") ?: return null,
                    tabId = opaque("tabID") ?: return null,
                    scope = (json["scope"] as? String)?.takeIf { SCOPE.matches(it) } ?: return null,
                    createdAt = createdAt,
                    state = state,
                )
            return receipt.takeIf { it.valid }
        }

        /** The offline queue's local command id. Never sent: the wire carries [CommandReceipt.receiptId]. */
        public fun queueCommandId(entryId: String): String = "queue:$entryId"
    }
}

/**
 * Where a send goes: Dart's `(profile.id, profile.baseUrl, directory, workspace)` origin. A flush and a receipt check
 * re-check it after every suspension (B-STATE-037); a changed scope cannot read an old receipt.
 * `toString` never prints the address or the paths.
 */
public class DispatchScope(
    public val profileId: ProfileId,
    public val baseUrl: String,
    public val directory: String?,
    public val workspace: String?,
) {
    /**
     * `sha256(jsonEncode([profileId, baseUrl, directory, workspace]))`, the same bytes Dart writes (compact, `null` for a
     * missing directory or workspace; plan 04 §13.6).
     */
    public val receiptScope: String by lazy { DartJson.sha256Hex(DartJson.encode(listOf(profileId.raw, baseUrl, directory, workspace))) }

    override fun equals(other: Any?): Boolean =
        other is DispatchScope && other.profileId == profileId && other.baseUrl == baseUrl &&
            other.directory == directory && other.workspace == workspace

    override fun hashCode(): Int = profileId.hashCode() * 31 + baseUrl.hashCode()

    override fun toString(): String = "DispatchScope(profile=${profileId.raw})"
}
