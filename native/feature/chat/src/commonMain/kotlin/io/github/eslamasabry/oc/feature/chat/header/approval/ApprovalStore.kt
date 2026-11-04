package io.github.eslamasabry.oc.feature.chat.header.approval

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.requests.consent.ApprovalMode
import io.github.eslamasabry.oc.engine.requests.consent.ApprovalModes
import io.github.eslamasabry.oc.engine.requests.consent.SessionApproval
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * The per-server approval settings (Flutter `SessionAutoApprovalStore`): one JSON map per saved
 * server under `oc.autoApprove.<profileId>` (format unchanged, so the profile sweep finds it and
 * the Flutter app reads it back). Absent on hosts without durable preferences; the chip is then not
 * offered.
 */
public interface ApprovalStore {
    /** +1 per acknowledged write; readers re-read [settings]. */
    public val revision: StateFlow<Long>

    public fun settings(profile: ProfileId): Map<String, SessionApproval>

    /**
     * Writes one conversation's own setting ([setting] null removes it);
     * [ApprovalModes.SERVER_WIDE] is the "*" entry.
     */
    public suspend fun set(
        profile: ProfileId,
        session: String,
        setting: SessionApproval?,
    ): Outcome<Unit>
}

/**
 * The Flutter wire format, both ways (`SessionAutoApproval.toJson/fromJson`). Unknown or malformed
 * entries are skipped.
 */
public object ApprovalCodec {
    public fun key(profile: ProfileId): String = "oc.autoApprove.${profile.raw}"

    public fun decode(text: String?): Map<String, SessionApproval> {
        if (text.isNullOrBlank()) return emptyMap()
        val root = runCatchingParse(text) as? JsonObject ?: return emptyMap()
        return buildMap {
            for ((session, value) in root) {
                if (session.isEmpty() || value !is JsonObject) continue
                val mode =
                    when ((value["mode"] as? JsonPrimitive)?.contentOrNull) {
                        "ask" -> ApprovalMode.Ask
                        "autoOnce" -> ApprovalMode.AutoOnce
                        else -> null
                    } ?: continue
                put(
                    session,
                    SessionApproval(
                        mode,
                        (value["inheritToChildren"] as? JsonPrimitive)?.booleanOrNull == true,
                    ),
                )
            }
        }
    }

    public fun encode(settings: Map<String, SessionApproval>): String = buildJsonObject {
        for ((session, s) in settings) {
            put(
                session,
                buildJsonObject {
                    put("mode", if (s.mode == ApprovalMode.AutoOnce) "autoOnce" else "ask")
                    put("inheritToChildren", s.inheritToChildren)
                },
            )
        }
    }
        .toString()

    /**
     * Parsing a stored string is not suspend code; a corrupt value reads as no settings (Dart
     * catches the same).
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runCatchingParse(text: String) =
        try {
            Json.parseToJsonElement(text)
        } catch (_: IllegalArgumentException) {
            null
        }
}

/**
 * The permission asks this conversation's connection holds, and the one write that answers one
 * "once".
 */
public interface ApprovalPort {
    /**
     * Waiting permission asks of this conversation's connection (all sessions; the sweep filters).
     */
    public val waiting: StateFlow<List<PermissionRequest>>

    /** Answers [id] "Allow once" through the same write path as a tap on the card. */
    public suspend fun allowOnce(id: RequestId): Outcome<Unit>

    /**
     * Applies a saved choice to tools that may already be running; server policies are read back.
     */
    public suspend fun syncPermissions(): Outcome<Unit> = Outcome.Ok(Unit)

    /** The session's parent, when the connection knows it (sub-agent inheritance). */
    public fun parentOf(session: SessionId): SessionId? = null
}

/**
 * The host's approval store, if it has durable preferences (Android binds one; desktop and tests
 * may not).
 */
@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface ApprovalBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true) public val approvalStores: Set<ApprovalStore>
}

/** No connection: nothing waits, nothing is answered. */
public object NoApprovals : ApprovalPort {
    override val waiting: StateFlow<List<PermissionRequest>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    override suspend fun allowOnce(id: RequestId): Outcome<Unit> = Outcome.Ok(Unit)
}
