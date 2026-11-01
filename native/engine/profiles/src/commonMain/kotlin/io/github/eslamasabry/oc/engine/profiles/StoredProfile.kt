package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.security.AddressRules

/**
 * A JSON object the app keeps but does not interpret (the AI Team `orchestration` config, ADR D6). It is written back
 * exactly as Dart `jsonEncode` writes it, so a stored value survives a native write byte for byte.
 */
public class OpaqueJson internal constructor(internal val value: Map<String, Any?>) {
    /** The Dart `jsonEncode` text of the object. */
    public val encoded: String = DartJson.encode(value)

    override fun equals(other: Any?): Boolean = other is OpaqueJson && other.encoded == encoded

    override fun hashCode(): Int = encoded.hashCode()

    override fun toString(): String = "OpaqueJson(len=${encoded.length})"

    public companion object {
        /** Null unless [text] is one JSON object. */
        public fun parse(text: String): OpaqueJson? = (DartJson.decodeOrNull(text) as? Map<*, *>)?.let(::fromDecoded)

        @Suppress("UNCHECKED_CAST")
        internal fun fromDecoded(value: Map<*, *>): OpaqueJson? =
            if (value.keys.all { it is String }) OpaqueJson(LinkedHashMap(value as Map<String, Any?>)) else null
    }
}

/**
 * One row of `oc.profiles`, every field Flutter 2.1.0+57 persists (`lib/state/profiles/models.dart`), plus the
 * in-memory [transientTransport] flag that is never stored. Secrets are never part of it (plan 03 §10.3).
 *
 * [flavor] is not a field: Dart reads every profile as `v2` and writes `"flavor":"v2"` (plan 05 §7.6 L4).
 */
public data class StoredProfile(
    val id: ProfileId,
    val name: String,
    val baseUrl: String,
    val backend: BackendKind = BackendKind.OpenCode2,
    val username: String = "",
    /** Wire name `codexDirectory`; emitted only for Paseo/Codex. */
    val codexDirectory: String = "",
    val requiresOpenCode2Migration: Boolean = false,
    val openCode2HistoryPending: Boolean = false,
    val serverVersion: String? = null,
    /** AI Team config, opaque and kept byte for byte; null = plugin off. */
    val orchestration: OpaqueJson? = null,
    /** A private transport lease (BYO host): never persisted (B-SEC-136). */
    val transientTransport: Boolean = false,
) {
    /** Codex and Paseo: one WebSocket endpoint, one secret (`oc.codexToken.<id>`), one project folder. */
    val usesAgentSocket: Boolean get() = backend != BackendKind.OpenCode2

    /** A Codex token is mandatory; a Paseo daemon on a private network may run without a password. */
    val agentSocketSecretRequired: Boolean get() = backend == BackendKind.Codex

    /** The secure-storage name of this profile's connection secret. */
    val secretName: String get() = if (usesAgentSocket) ProfileKeys.codexToken(id) else ProfileKeys.password(id)

    override fun toString(): String = "StoredProfile(id=${id.raw}, backend=$backend)"
}

/**
 * The lenient port of `ServerProfile.fromJson` / `toJson` (plan 05 §2.5 `ServerProfileJson`).
 *
 * - Read: Dart's rules, including L4 (`requiresOpenCode2Migration` when a non-agent row's `flavor` is not `v2`) and
 *   `plainServerName`. A row without a string `id` cannot be read.
 * - Write: the v57 key order (`id, name, baseUrl, backend, username, [codexDirectory], flavor,
 *   requiresOpenCode2Migration, openCode2HistoryPending, [serverVersion], [orchestration]`), then every key of the
 *   stored row this codec does not know, in its stored order and with its stored value (plan 03 §10.3: writes merge
 *   over the raw stored object, so a newer build's field is never dropped).
 */
public object StoredProfileJson {
    public val KNOWN_KEYS: List<String> = listOf(
        "id", "name", "baseUrl", "backend", "username", "codexDirectory", "flavor",
        "requiresOpenCode2Migration", "openCode2HistoryPending", "serverVersion", "orchestration",
    )

    /** Dart `ServerProfile.fromJson`. */
    public fun decode(row: Map<*, *>): StoredProfile? {
        val id = row["id"] as? String ?: return null
        val backendWire = row["backend"]
        val backend = when (backendWire) {
            "codex" -> BackendKind.Codex
            "paseo" -> BackendKind.Paseo
            else -> BackendKind.OpenCode2
        }
        return StoredProfile(
            id = ProfileId(id),
            name = plainServerName(dartToString(row["name"]) ?: ""),
            baseUrl = dartToString(row["baseUrl"]) ?: "",
            backend = backend,
            username = dartToString(row["username"]) ?: "",
            codexDirectory = dartToString(row["codexDirectory"]) ?: "",
            requiresOpenCode2Migration = row["requiresOpenCode2Migration"] == true ||
                (backendWire != "codex" && backendWire != "paseo" && row["flavor"] != "v2"),
            openCode2HistoryPending = row["openCode2HistoryPending"] == true,
            serverVersion = row["serverVersion"]?.let(::dartToString),
            orchestration = orchestrationOf(row["orchestration"]),
        )
    }

    /** The row to store for [profile], merged over [previous] (the stored row with the same id, if any). */
    public fun encode(profile: StoredProfile, previous: Map<*, *>? = null): LinkedHashMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["id"] = profile.id.raw
        out["name"] = profile.name
        out["baseUrl"] = profile.baseUrl
        out["backend"] = when (profile.backend) {
            BackendKind.OpenCode2 -> "openCode"
            BackendKind.Codex -> "codex"
            BackendKind.Paseo -> "paseo"
        }
        out["username"] = profile.username
        if (profile.usesAgentSocket) out["codexDirectory"] = profile.codexDirectory
        out["flavor"] = "v2"
        out["requiresOpenCode2Migration"] = profile.requiresOpenCode2Migration
        out["openCode2HistoryPending"] = profile.openCode2HistoryPending
        profile.serverVersion?.let { out["serverVersion"] = it }
        profile.orchestration?.let { out["orchestration"] = it.value }
        previous?.forEach { (key, value) -> if (key is String && key !in KNOWN_KEYS) out[key] = value }
        return out
    }

    /**
     * The stored rows of `oc.profiles`: null when the key is absent, an empty list when the blob is not a JSON list
     * (Dart loads no profiles then). Rows are returned raw so a write can merge over them.
     */
    public fun rows(raw: String?): List<Any?>? {
        if (raw == null) return null
        return DartJson.decodeOrNull(raw) as? List<*> ?: emptyList()
    }

    /** Every readable profile of [raw], in stored order (Dart `load`). */
    public fun decodeList(raw: String?): List<StoredProfile> =
        rows(raw).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::decode) }

    public fun encodeRows(rows: List<Any?>): String = DartJson.encode(rows)

    /** Dart `plainServerName`: this device is "This phone"; a bare IP is "Computer at <ip>". */
    public fun plainServerName(name: String): String {
        val value = name.trim()
        val unbracketed = value.replace("[", "").replace("]", "")
        if (AddressRules.isLoopback(unbracketed)) return "This phone"
        val ipv4 = IPV4.matches(value)
        val ipv6 = ':' in value && IPV6ISH.matches(value)
        if (ipv4 || ipv6) return "Computer at $unbracketed"
        return name
    }

    /** Dart `OrchestrationConfig.fromJson` gate: a map with a non-empty string `url`; anything else is "plugin off". */
    private fun orchestrationOf(value: Any?): OpaqueJson? {
        val map = value as? Map<*, *> ?: return null
        val url = map["url"] as? String
        if (url.isNullOrEmpty()) return null
        return OpaqueJson.fromDecoded(map)
    }

    /** Dart `toString()` of a decoded JSON scalar (the `(j[x] ?? '').toString()` reads). */
    private fun dartToString(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is Double -> DartJson.dartDouble(value)
        is Map<*, *>, is List<*> -> DartJson.encode(value)
        else -> value.toString()
    }

    private val IPV4 = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
    private val IPV6ISH = Regex("""^[0-9a-fA-F:.\[\]]+$""")
}
