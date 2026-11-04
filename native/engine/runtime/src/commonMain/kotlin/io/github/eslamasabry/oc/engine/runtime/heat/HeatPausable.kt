package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Something on this phone the heat guard can pause (port of thermal_guard.dart `ThermalTeam`):
 * today the AI Team reached by the app-owned loopback adapter; the guard knows this generic shape.
 * [id] is the profile it belongs to, so its episode is saved under `oc.thermalPause.<id>` and swept
 * with the profile (native AGENTS rule 9). [builtin]: it runs inside this app, so stopping it stops
 * its service. [url] and [city] are the port's own address of it, kept for the next process.
 */
public data class HeatPausable(
    val id: ProfileId,
    val url: String,
    val city: String = "",
    val builtin: Boolean = false,
)

/**
 * What the guard paused on one [target], so it gives back exactly that (port of `ThermalTeamHold`).
 */
public data class HeatHold(
    val target: HeatPausable,
    val since: Instant,
    /** The running sessions the guard suspended (their state is kept; a wake continues them). */
    val sessions: List<String> = emptyList(),
    /** The built-in service the guard stopped. */
    val serviceStopped: Boolean = false,
    val status: ThermalStatus = ThermalStatus.Unknown,
    /** App-owned service recipe, only for the exact service the guard stopped. Never displayed. */
    val restart: String? = null,
) {
    override fun toString(): String =
        "HeatHold(profile=${target.id}, sessions=${sessions.size}, serviceStopped=$serviceStopped)"
}

/**
 * How the guard reaches what it pauses (Flutter `ThermalTeamPort`; the Android loopback adapter).
 * Nothing here may stop the OpenCode server for heat (plan 11 §8.2 "Never").
 */
public interface HeatPausePort {
    /** What runs on this phone and works now; something the person suspended is not listed. */
    public suspend fun runningHere(): List<HeatPausable>

    /**
     * Suspends [target] with its work kept; null when there was nothing to pause (paused by the
     * person, or gone).
     */
    public suspend fun pause(target: HeatPausable, now: Instant): HeatHold?

    /** Stops [hold]'s service safely where the app can; the hold as it is afterwards. */
    public suspend fun stop(hold: HeatHold): HeatHold

    /**
     * Gives back exactly what [hold] took. False when anything is unconfirmed: the guard keeps the
     * durable hold and tries again after another cool wait. [stillAllowed] is the recovery policy,
     * read again: an implementation asks it before every step that acts (see [RecoveryGate.steps])
     * and answers false as soon as it says no.
     */
    public suspend fun resume(hold: HeatHold, stillAllowed: () -> Boolean): Boolean
}

/**
 * The guard's durable state: the switch (`oc.thermalGuard`, on when missing) and one hold per
 * profile (`oc.thermalPause.<id>`, raw JSON from [HeatHoldCodec]). The binding is the migrated
 * preferences store; Flutter's keys are adopted unchanged, so a pause a Flutter process saved is
 * resumed by the native one (B-ANDR-090).
 */
public interface ThermalGuardStore {
    public suspend fun readEnabled(): Boolean?

    public suspend fun writeEnabled(value: Boolean): Outcome<Unit>

    /** Every saved hold: the key's profile id suffix to the raw value. */
    public suspend fun readHolds(): Map<String, String>

    public suspend fun writeHold(id: ProfileId, raw: String): Outcome<Unit>

    public suspend fun removeHold(id: String): Outcome<Unit>

    public companion object {
        public const val ENABLED_KEY: String = "oc.thermalGuard"
        public const val HOLD_KEY_PREFIX: String = "oc.thermalPause."

        public fun holdKey(id: ProfileId): String = HOLD_KEY_PREFIX + id.raw
    }
}

/**
 * The saved form of a [HeatHold], byte-compatible with Flutter's `ThermalTeamHold.toJson` /
 * `fromJson`.
 */
public object HeatHoldCodec {
    private val json = Json { ignoreUnknownKeys = true }

    public fun encode(hold: HeatHold): String = buildJsonObject {
        put("url", hold.target.url)
        put("city", hold.target.city)
        put("builtin", hold.target.builtin)
        put("since", hold.since.toString())
        putJsonArray("sessions") { hold.sessions.forEach { add(JsonPrimitive(it)) } }
        put("serviceStopped", hold.serviceStopped)
        put("status", hold.status.wire)
        hold.restart?.let { put("restart", it) }
    }
        .toString()

    /**
     * Null when [raw] is not a hold: not an object, no url, or no readable `since`. Bad sessions
     * are dropped.
     */
    public fun decode(id: String, raw: String): HeatHold? {
        if (id.isEmpty()) return null
        val obj = parse(raw) as? JsonObject ?: return null
        val url = obj.string("url")
        if (url.isNullOrEmpty()) return null
        val since = obj.text("since")?.let { parseInstant(it) } ?: return null
        val sessions =
            (obj["sessions"] as? JsonArray).orEmpty().mapNotNull { element ->
                (element as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.takeIf { it.isNotEmpty() }
            }
        return HeatHold(
            target =
                HeatPausable(
                    id = ProfileId(id),
                    url = url,
                    city = obj.text("city") ?: "",
                    builtin = obj.flag("builtin"),
                ),
            since = since,
            sessions = sessions,
            serviceStopped = obj.flag("serviceStopped"),
            status = ThermalStatus.parse(obj.string("status")),
            restart = obj.string("restart"),
        )
    }

    private fun parse(raw: String): JsonElement? =
        try {
            json.parseToJsonElement(raw)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun parseInstant(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Dart's `'${raw[key] ?? ''}'`: any primitive's text. */
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.flag(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true
}
