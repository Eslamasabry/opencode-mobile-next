package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.profiles.StoredProfileJson

/*
 * The shared vocabulary of the widget runners (port of lib/background/widget_v2_contract.dart and the helpers of
 * widget_action_runner.dart). Pure Kotlin: the runners see only these small ports; production implements them over
 * the protocol-neutral ServerGateway (GatewayRunTransport) and the app's acknowledged store (WidgetRunGuards).
 */

/** One launcher tap: identifiers only, never credentials or drafts (Flutter `WidgetAction`). */
public data class WidgetTap(
    val id: String,
    val action: String,
    val profileId: String,
    val scopeToken: String,
    val sessionId: String = "",
    val requestId: String = "",
    val runId: String = "",
    val gateId: String = "",
    val lastProjectId: String = "",
    val expectedStartedAt: Long = 0,
    /** Exists only for an explicit overlay submission (steer, voice). */
    val text: String = "",
) {
    override fun toString(): String = "WidgetTap($action)"

    public companion object {
        public val ACTIONS: Set<String> =
            setOf("allowOnce", "reject", "stop", "steer", "voice", "approvePlan", "resume")

        /** Flutter `WidgetAction.fromPlatform`: a malformed action never enters background dispatch. */
        public fun from(value: Map<*, *>?): WidgetTap? {
            if (value == null) return null
            fun string(key: String): String = value[key] as? String ?: ""
            val id = string("id")
            val action = string("action")
            val profile = string("profileID")
            val scope = string("scopeToken")
            if (id.isEmpty() || id.length > 200 || action !in ACTIONS) return null
            if (profile.isEmpty() || profile.length > 200 || scope.isEmpty() || scope.length > 200) return null
            if (listOf("sessionID", "requestID", "runID", "gateID", "lastProjectID").any { string(it).length > 512 })
                return null
            if (string("text").length > 8000) return null
            val started = value["expectedStartedAt"]
            if (started != null && (started !is Int && started !is Long || (started as Number).toLong() < 0)) return null
            return WidgetTap(
                id = id,
                action = action,
                profileId = profile,
                scopeToken = scope,
                sessionId = string("sessionID"),
                requestId = string("requestID"),
                runId = string("runID"),
                gateId = string("gateID"),
                lastProjectId = string("lastProjectID"),
                expectedStartedAt = (started as? Number)?.toLong() ?: 0L,
                text = string("text").trim(),
            )
        }
    }
}

/** The Dart-shaped result the copied controller reads (`id, action, status, error?, snapshot?`). */
public data class WidgetRunResult(
    val id: String,
    val action: String,
    val status: String,
    val error: String? = null,
    val snapshot: Map<String, Any?>? = null,
) {
    public fun toMap(): Map<String, Any?> =
        buildMap {
            put("id", id)
            put("action", action)
            put("status", status)
            if (error != null) put("error", error)
            if (snapshot != null) put("snapshot", snapshot)
        }

    override fun toString(): String = "WidgetRunResult($action, $status, $error)"
}

/** A pending permission as the server lists it: only the identities the runners compare. */
public data class WidgetPendingAsk(val requestId: String, val sessionId: String)

/** A saved profile as the runners need it: its id and metadata fingerprint, never a secret. */
public data class WidgetRunProfile(val id: String, val fingerprint: String, val transientTransport: Boolean = false)

/**
 * One cold connection to one server location (Flutter `WidgetActionTransport`). Every read is bounded by the caller;
 * a Failed outcome is never shown. [close] is idempotent and releases the connection.
 */
public interface WidgetRunTransport {
    public suspend fun health(): Outcome<Boolean>

    public suspend fun pendingPermissions(): Outcome<List<WidgetPendingAsk>>

    public suspend fun messages(session: String): Outcome<List<MessageWithParts>>

    /** Wire status per session id (`idle`, `busy`, `retry`, …); a missing session is idle. */
    public suspend fun sessionStatuses(): Outcome<Map<String, String>>

    /** Answers exactly [request] of [session]: allow once (`once`) or reject. */
    public suspend fun answerPermission(session: String, request: String, allowOnce: Boolean): Outcome<Unit>

    public suspend fun stop(session: String): Outcome<Unit>

    public suspend fun close()
}

/** Why a transport could not be built. Both are certain: nothing was sent. */
public enum class WidgetOpenRefusal { Unsupported, Unavailable }

public sealed interface WidgetOpen {
    public data class Opened(val transport: WidgetRunTransport) : WidgetOpen

    public data class Refused(val why: WidgetOpenRefusal) : WidgetOpen
}

/** Durable ownership, read fresh on every guard (Flutter loadProfile / loadTarget / isCurrent / createTransport). */
public interface WidgetActionWorld {
    public suspend fun loadProfile(profileId: String): WidgetRunProfile?

    /** App-private routing (`oc.widgetTarget.<profile>`); never a path from the public snapshot. */
    public suspend fun loadTarget(tap: WidgetTap, fingerprint: String): Map<String, Any?>?

    public suspend fun isCurrent(tap: WidgetTap, fingerprint: String): Boolean

    public suspend fun open(profile: WidgetRunProfile, target: Map<String, Any?>): WidgetOpen
}

/** The status check's view of the same world; [isCurrent] re-reads the durable ticket. */
public interface WidgetStatusWorld {
    public suspend fun loadProfile(profileId: String): WidgetRunProfile?

    public suspend fun loadTarget(tap: WidgetTap, fingerprint: String): Map<String, Any?>?

    public suspend fun isCurrent(ticket: Map<String, Any?>): Boolean

    public suspend fun open(profile: WidgetRunProfile, target: Map<String, Any?>): WidgetOpen
}

/**
 * Metadata only (Flutter `widgetProfileFingerprint`): SHA-256 of the Dart `toJson()` of the saved profile without
 * `name`. An edited endpoint or routing identity invalidates the old launcher target; credentials are never part of it.
 */
public fun widgetProfileFingerprint(profile: StoredProfile): String {
    val row = StoredProfileJson.encode(profile).apply { remove("name") }
    return DartJson.sha256Hex(DartJson.encode(row))
}

/**
 * Stable server user-message identity, never the text of that message (Flutter `widgetRunMarker`). OpenCode 2
 * timeline records (`v2:` parts, including the terminal idle record) are not new prompts.
 */
public fun widgetRunMarker(messages: Iterable<MessageWithParts>): String? {
    val users =
        messages.filter { m -> m.info.role == Role.User && m.parts.none { it.rawType.startsWith("v2:") } }
    val last = users.sortedBy { it.info.time?.createdMs ?: 0L }.lastOrNull() ?: return null
    return last.info.id.ifEmpty { null }
}

internal fun Any?.asLong(): Long? = (this as? Number)?.toLong()

internal fun Map<*, *>.rows(): List<Map<*, *>> = (this["sessions"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

internal fun Map<*, *>.actions(): List<*>? = this["onDemandActions"] as? List<*>
