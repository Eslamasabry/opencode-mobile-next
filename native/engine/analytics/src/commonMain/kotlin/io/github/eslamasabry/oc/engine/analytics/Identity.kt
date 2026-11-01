package io.github.eslamasabry.oc.engine.analytics

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Source of random ids: 32 lowercase hex characters (128 bits). K-SEC-12 bans `kotlin.random` here; the default uses
 * `Uuid.random()`, which draws from the platform's secure random generator.
 * TODO(core-bytes): use `secureRandomBytes` from `:core:common` (plan 03 §10.1 byte primitives, not built yet).
 */
public fun interface IdSource {
    public fun newId(): String
}

public object SecureIdSource : IdSource {
    @OptIn(ExperimentalUuidApi::class)
    override fun newId(): String = Uuid.random().toHexString()
}

/** The stored install id. Random, unrelated to any device or account id, replaced every [InstallIdPolicy.ROTATION_DAYS]. */
@Serializable
internal data class InstallIdRecord(val id: String, val createdAtMs: Long)

internal object InstallIdPolicy {
    const val ROTATION_DAYS: Int = 30

    /** Keeps [record] while it is younger than the rotation period; otherwise draws a new, unlinked id. */
    fun current(record: InstallIdRecord?, nowMs: Long, ids: IdSource, rotationDays: Int = ROTATION_DAYS): InstallIdRecord {
        val age = record?.let { nowMs - it.createdAtMs }
        // A clock that moved backwards also rotates: an id never outlives its period by a clock change.
        return if (record == null || age == null || age < 0 || age >= rotationDays * DAY_MS) {
            InstallIdRecord(ids.newId(), nowMs)
        } else {
            record
        }
    }
}

/** Per-process session id, rotated after [idleMs] without an event. Never written to storage (plan 16 §5.2). */
internal class SessionTracker(private val ids: IdSource, private val idleMs: Long = SESSION_IDLE_MS) {
    private var id: String? = null
    private var lastAtMs: Long = 0

    fun sessionAt(nowMs: Long): String {
        val current = id
        val fresh = current == null || nowMs - lastAtMs >= idleMs || nowMs < lastAtMs
        val result = if (fresh) ids.newId() else current!!
        id = result
        lastAtMs = nowMs
        return result
    }

    fun forget() {
        id = null
    }

    companion object {
        const val SESSION_IDLE_MS: Long = 60 * 60_000L
    }
}
