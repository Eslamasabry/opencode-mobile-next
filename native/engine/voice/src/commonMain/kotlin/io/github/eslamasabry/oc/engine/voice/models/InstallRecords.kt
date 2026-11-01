package io.github.eslamasabry.oc.engine.voice.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `.installed.json`, written in Flutter's exact shape (plan 10 §7.2; Dart `jsonEncode` key order, no spaces):
 * `{"pack":id,"revision":rev,"files":[{"name","length","sha256"}]}`. Present only after a full SHA-256 check.
 */
@Serializable
internal data class InstalledMarker(val pack: String, val revision: String, val files: List<MarkerFile>) {
    @Serializable
    internal data class MarkerFile(val name: String, val length: Long, val sha256: String)

    companion object {
        const val NAME = ".installed.json"

        fun of(spec: ModelPackSpec) =
            InstalledMarker(spec.id.raw, spec.revision, spec.files.map { MarkerFile(it.name, it.bytes, it.sha256) })
    }
}

/**
 * `.native-verified.json` (M4, D10-12): each file's length and the SHA-256 of its first and last 1 MiB, so start-up
 * reads about 2 MiB per file instead of re-hashing up to 540 MB.
 */
@Serializable
internal data class VerifiedStamp(val v: Int = 1, val files: List<StampFile>, val verifiedAtMs: Long) {
    @Serializable
    internal data class StampFile(val name: String, val length: Long, val headSha256: String, val tailSha256: String)

    companion object {
        const val NAME = ".native-verified.json"
        const val EDGE: Long = 1024L * 1024L
    }
}

internal val recordJson = Json { ignoreUnknownKeys = true }
