package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.dart.DartJson

/**
 * The Flutter-era schedule stamp is a SHA-256 of the whole saved profile row (without its name), and that row carries
 * `serverVersion`, which the app rewrites each time the phone's own server is refreshed (Flutter `builtin_server`
 * does the same). A schedule made before such a refresh therefore carries a stamp that no longer matches the row of
 * the very same server, only because the version moved. Native identity leaves the version out.
 *
 * [matches] tests the stamps this row would have had under another server version, every other field unchanged. A
 * stamp that equals one of them names this server (same address, account and flavour); a stamp for an edited address
 * or account is in none of them and stays refused (B-AGENT-218).
 */
internal object LegacyFingerprints {
    /** True when [stamp] is this row's stamp under no version, the row's own, or any plausible OpenCode version. */
    fun matches(row: Map<String, Any?>, stamp: String): Boolean {
        if (stamp == stamp(row) || stamp == stamp(withVersion(row, null))) return true
        for (major in 1..3) for (minor in 0..40) for (patch in 0..99) {
            val v = "$major.$minor.$patch"
            if (stamp == stamp(withVersion(row, v)) || stamp == stamp(withVersion(row, "v$v"))) return true
        }
        return false
    }

    fun stamp(row: Map<String, Any?>): String = DartJson.sha256Hex(DartJson.encode(row))

    /** [row] with `serverVersion` set to [version] (or removed) at the position Dart's `toJson` writes it. */
    fun withVersion(row: Map<String, Any?>, version: String?): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        var placed = false
        fun put() {
            if (!placed) {
                placed = true
                if (version != null) out["serverVersion"] = version
            }
        }
        for ((key, value) in row) {
            if (key == "serverVersion") {
                put()
                continue
            }
            // After openCode2HistoryPending, before orchestration and anything newer.
            if (!placed && key == "orchestration") put()
            out[key] = value
            if (!placed && key == "openCode2HistoryPending") put()
        }
        put()
        return out
    }
}
