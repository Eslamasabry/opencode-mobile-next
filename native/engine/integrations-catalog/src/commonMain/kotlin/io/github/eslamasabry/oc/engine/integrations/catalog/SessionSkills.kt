package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.Volatile

// Flutter lib/domain/session_skill.dart, Api2OperationsGateway.listSkills/activateSessionSkill (ops_extras.dart) and
// ConnectionController._activateSessionSkill (state/connection/session_notes.dart).

/** Why a skill was not added; each has one plain sentence in :ui:words. */
public enum class SessionSkillFailure { Unsupported, Changed, Staged, Busy, Uncertain }

/** The answer to an activation: done, refused for a fixed reason, or an answered failure (e.g. a known-missing skill). */
public sealed interface SkillActivation {
    public data object Done : SkillActivation

    public data class Refused(val reason: SessionSkillFailure) : SkillActivation

    public data class Failed(val failure: Failure) : SkillActivation
}

/** A catalog skill's identity: the wire [id] is what activation sends, never the display [name]. */
public data class CatalogSkill(val id: String, val name: String)

/** The session skill routes on one connection. */
public class SessionSkillGateway(
    private val transport: CatalogTransport,
    private val location: () -> CatalogLocation,
) {
    @Volatile private var supported: Boolean = true

    /** False once the server said the route does not exist (405/501, or a 404 that is not a known missing thing). */
    public val sessionSkillsSupported: Boolean get() = supported

    /** `GET /skill` at the current location; rows without an id or name are skipped. */
    public suspend fun listSkills(): Outcome<List<CatalogSkill>> {
        val reply =
            when (val sent = transport.send(CatalogRequest("GET", "/skill", location().query()))) {
                is Outcome.Failed -> return sent
                is Outcome.Ok -> sent.value
            }
        if (!reply.isSuccess) return statusFailure(reply.status).failed()
        val body = reply.body
        val rows = ((body as? JsonObject)?.get("data") ?: body) as? JsonArray ?: return Failure.UnreadableReply().failed()
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CatalogSkill(id, obj.string("name") ?: id)
        }.ok()
    }

    /**
     * `POST /session/{id}/skill` with `{skill, resume}`. The session identifies its location, so the route has no
     * location query. 405/501 or an unknown 404 latch unsupported; a missing session or skill stays a plain failure and
     * keeps the feature; a 5xx or an unanswered request is uncertain (it may have been added).
     */
    public suspend fun activateSessionSkill(sessionId: String, skillId: String, resume: Boolean): SkillActivation {
        if (!supported) return SkillActivation.Refused(SessionSkillFailure.Unsupported)
        val request =
            CatalogRequest(
                "POST",
                "/session/${CatalogUrl.encodeSegment(sessionId)}/skill",
                body = JsonObject(mapOf("skill" to JsonPrimitive(skillId), "resume" to JsonPrimitive(resume))),
            )
        val reply =
            when (val sent = transport.send(request)) {
                is Outcome.Failed -> return SkillActivation.Refused(SessionSkillFailure.Uncertain)
                is Outcome.Ok -> sent.value
            }
        if (reply.isSuccess) return SkillActivation.Done
        val tag = reply.tag
        if (reply.status == 405 || reply.status == 501 ||
            (reply.status == 404 && tag != "SessionNotFoundError" && tag != "SkillNotFoundError")
        ) {
            supported = false
            return SkillActivation.Refused(SessionSkillFailure.Unsupported)
        }
        if (reply.status >= 500) return SkillActivation.Refused(SessionSkillFailure.Uncertain)
        return SkillActivation.Failed(statusFailure(reply.status))
    }
}

/** What the guard reads about the session just before sending. */
public data class SkillSessionFacts(val reverted: Boolean, val stagedRevert: Boolean)

/** The connection-side ports the guard needs. */
public interface SkillActivationPorts {
    /** Wakes the connection; null when there is none. */
    public suspend fun prepare(): SessionSkillGateway?

    /** A fresh read of the session (revert state). */
    public suspend fun sessionFacts(gateway: SessionSkillGateway, sessionId: String): Outcome<SkillSessionFacts>

    /** True while this session has a revert being saved locally. */
    public fun revertSaving(sessionId: String): Boolean = false

    /** Publishes `session.skill.changed` after a current activation. */
    public fun skillChanged(sessionId: String) {}
}

/**
 * One activation at a time per (location, session), bound to the location revision the person saw. A location change
 * while waking or reading the session prevents dispatch; a staged revert refuses; a second tap while one is in flight
 * is [SessionSkillFailure.Busy].
 */
public class SkillActivationGuard(private val ports: SkillActivationPorts) {
    /** Bumped by the owner whenever the project location changes. */
    public var locationRevision: Int = 0

    private val inFlight = mutableSetOf<Pair<Int, String>>()

    public suspend fun activate(sessionId: String, skillId: String, resume: Boolean, expectedLocation: Int): SkillActivation {
        fun current() = locationRevision == expectedLocation
        if (!current()) return SkillActivation.Refused(SessionSkillFailure.Changed)
        val key = expectedLocation to sessionId
        if (!inFlight.add(key)) return SkillActivation.Refused(SessionSkillFailure.Busy)
        try {
            val gateway = ports.prepare()
            if (!current()) return SkillActivation.Refused(SessionSkillFailure.Changed)
            if (gateway == null || !gateway.sessionSkillsSupported) {
                return SkillActivation.Refused(SessionSkillFailure.Unsupported)
            }
            val facts =
                when (val read = ports.sessionFacts(gateway, sessionId)) {
                    is Outcome.Failed -> return SkillActivation.Failed(read.failure)
                    is Outcome.Ok -> read.value
                }
            if (!current()) return SkillActivation.Refused(SessionSkillFailure.Changed)
            if (facts.reverted || facts.stagedRevert || ports.revertSaving(sessionId)) {
                return SkillActivation.Refused(SessionSkillFailure.Staged)
            }
            val result = gateway.activateSessionSkill(sessionId, skillId, resume)
            if (result == SkillActivation.Done && current()) ports.skillChanged(sessionId)
            return result
        } finally {
            inFlight.remove(key)
        }
    }
}

/**
 * `session.skill.*` events only request a refresh of that session; the skill name and instruction text never cross.
 */
public fun skillEventHint(type: String, data: JsonElement?): CatalogHint? {
    if (!type.startsWith("session.skill.")) return null
    val sessionId = (data as? JsonObject)?.string("sessionID") ?: return null
    return CatalogHint("session.skill.changed", mapOf("sessionID" to sessionId))
}
