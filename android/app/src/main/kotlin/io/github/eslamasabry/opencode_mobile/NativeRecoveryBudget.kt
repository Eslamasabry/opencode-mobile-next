package io.github.eslamasabry.opencode_mobile

/** The sole restart count, migrated from Dart's v1 budget. No unused permit is spent. */
internal data class NativeRecoveryBudget(
    val attempts: Int = 0,
    val nextAt: Long? = null,
    val pending: Boolean = false,
    val eventId: String? = null,
    val recoveryGeneration: Long? = null,
    val confirmedAt: Long? = null,
    val revision: Long = 0L,
) {
    fun reserve(at: Long, generation: Long, id: String): NativeRecoveryBudget {
        check(attempts < MAX_ATTEMPTS && !pending) { "recovery_unavailable" }
        return copy(attempts = attempts + 1, pending = true, eventId = id,
            recoveryGeneration = generation, confirmedAt = null, nextAt = null,
            revision = revision + 1)
    }
    fun map(): Map<String, Any?> = mapOf("version" to 1, "attempts" to attempts,
        "nextAt" to nextAt, "pending" to pending, "eventId" to eventId,
        "recoveryGeneration" to recoveryGeneration, "confirmedAt" to confirmedAt,
        "revision" to revision)

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val MAX_ARCHIVE_ENTRIES = 16
        fun read(value: Map<*, *>): NativeRecoveryBudget {
            fun integer(key: String): Long? {
                val v = value[key] ?: return null
                check(v is Int || v is Long) { "recovery_unavailable" }
                return (v as Number).toLong()
            }
            check(integer("version") == 1L) { "recovery_unavailable" }
            val attempts = integer("attempts")
            val pending = value["pending"]
            check(attempts != null && attempts in 0L..MAX_ATTEMPTS.toLong() && pending is Boolean)
            val event = value["eventId"]
            val generation = integer("recoveryGeneration")
            val confirmed = integer("confirmedAt")
            check(event == null || event is String)
            check(!pending || (event is String && event.isNotEmpty() && generation != null))
            check(confirmed == null || pending)
            val revision = integer("revision") ?: 0L
            check(revision >= 0L)
            return NativeRecoveryBudget(attempts.toInt(), integer("nextAt"), pending,
                event as String?, generation, confirmed, revision)
        }

        fun migrationAllows(value: Map<*, *>?): Boolean =
            value?.get("version") == 2 && value["nativeAuthority"] == true

        fun archiveConfirmed(
            existing: List<NativeRecoveryBudget>, receipt: NativeRecoveryBudget,
        ): List<NativeRecoveryBudget> {
            check(existing.size <= MAX_ARCHIVE_ENTRIES) { "recovery_unavailable" }
            if (receipt.confirmedAt == null || existing.any { it.eventId == receipt.eventId }) return existing
            check(existing.size < MAX_ARCHIVE_ENTRIES) { "recovery_unavailable" }
            return existing + receipt
        }

        fun manualResetAllowed(resumed: Boolean, wanted: Boolean, userStopped: Boolean,
            boundProfile: String?, requestedProfile: String, manualGeneration: Long?,
            currentGeneration: Long, running: Boolean, nativeOwned: Boolean): Boolean =
            resumed && wanted && !userStopped && boundProfile == requestedProfile &&
            manualGeneration == currentGeneration && running && !nativeOwned

        fun shouldRemoveBeforeLaunch(nativeOwned: Boolean, tracked: Boolean): Boolean = !nativeOwned || tracked

        fun keepsForeground(running: Boolean, scheduled: Boolean, admitted: Boolean,
            attempts: Int): Boolean = running || (scheduled && admitted && attempts in 0..2)

        fun admitted(enabled: Boolean, wanted: Boolean, userStopped: Boolean,
            generation: Long, expectedGeneration: Long, markerValid: Boolean,
            policyAllowed: Boolean): Boolean = enabled && wanted && !userStopped &&
            generation == expectedGeneration && markerValid && policyAllowed

        /** A stored policy must match the pinned Dart schema; unknown data grants nothing. */
        fun policyAllows(value: Map<*, *>?): Boolean {
            return if (value == null || value["version"] != 1 ||
                value["supervision"] !in listOf("high", "balanced", "autonomous")) {
                false
            } else {
                val behaviors = value["behaviors"] as? Map<*, *>
                behaviors != null && behaviors["restartPhoneServer"] == true &&
                    behaviors["pollRestartHealth"] == true
            }
        }
    }
}
