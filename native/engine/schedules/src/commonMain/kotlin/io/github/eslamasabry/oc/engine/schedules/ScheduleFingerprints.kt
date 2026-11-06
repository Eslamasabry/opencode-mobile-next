package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.coroutines.cancellation.CancellationException

/**
 * Which server fingerprints a profile's schedules may carry (E00-S04).
 *
 * Flutter-era schedules carry the Flutter fingerprint (a bare SHA-256 of the Dart profile JSON). While the Flutter
 * build can still be restored (`oc.native.flutterReadable`), native never rewrites those stamps in
 * `oc.scheduledPrompts.<id>`: the Flutter runner compares them with the profile and would refuse a re-stamped
 * schedule after a rollback. Instead native records, in its own key, the native fingerprint the Flutter ones were
 * accepted for. A Flutter stamp runs on native only while the profile still has that native fingerprint, so a
 * schedule made for another server stays refused (B-AGENT-218). Once the Flutter build can no longer be restored,
 * the stamps are re-stamped to the native fingerprint and the alias is removed.
 */
internal class ScheduleFingerprints(private val backend: ScheduleBackend) {
    /** [native] is the native fingerprint the [legacy] Flutter fingerprints were accepted for. */
    data class Alias(val native: String, val legacy: List<String>)

    /** Null when there is none or it cannot be read: an unreadable alias accepts nothing. */
    suspend fun alias(profileId: String): Alias? {
        val raw =
            try {
                backend.read(ScheduleKeys.nativeFingerprint(profileId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return null
        return decode(raw)
    }

    /** True when a schedule stamped [stamp] belongs to the profile whose native fingerprint is now [current]. */
    suspend fun accepts(profileId: String, stamp: String, current: String?): Boolean {
        if (current == null) return false
        if (stamp == current) return true
        val alias = alias(profileId) ?: return false
        return alias.native == current && stamp in alias.legacy
    }

    /**
     * Records that [legacy] names the server whose native fingerprint is [native]. A changed native fingerprint (an
     * edited address, account or trust choice) starts a new list, so older Flutter stamps stop being accepted.
     */
    suspend fun remember(profileId: String, native: String, legacy: String) {
        val key = ScheduleKeys.nativeFingerprint(profileId)
        repeat(ATTEMPTS) {
            val raw = backend.read(key)
            val current = raw?.let(::decode)?.takeIf { it.native == native }
            if (current != null && legacy in current.legacy) return
            val kept = (current?.legacy.orEmpty() + legacy).takeLast(MAX_LEGACY)
            if (backend.compareAndSet(key, raw, encode(Alias(native, kept)))) return
        }
        throw ScheduleStoreException(ScheduleCode.StorageBusy)
    }

    suspend fun forget(profileId: String) {
        val key = ScheduleKeys.nativeFingerprint(profileId)
        repeat(ATTEMPTS) {
            val raw = backend.read(key) ?: return
            if (backend.compareAndSet(key, raw, null)) return
        }
        throw ScheduleStoreException(ScheduleCode.StorageBusy)
    }

    private fun encode(alias: Alias): String =
        DartJson.encode(linkedMapOf("native" to alias.native, "legacy" to alias.legacy))

    private fun decode(raw: String): Alias? {
        val map = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        val native = map["native"] as? String ?: return null
        val legacy = (map["legacy"] as? List<*>)?.filterIsInstance<String>()?.filter { LEGACY.matches(it) } ?: return null
        return Alias(native, legacy)
    }

    companion object {
        /** A bare 64-hex value is a Flutter-era fingerprint. */
        val LEGACY: Regex = Regex("^[0-9a-f]{64}$")
        private const val MAX_LEGACY = 8
        private const val ATTEMPTS = 20
    }
}
