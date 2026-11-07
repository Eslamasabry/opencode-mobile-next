package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.dart.DartJson
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
            name = plainServerName(dartToString(row["name"]) ?: "", dartToString(row["baseUrl"]) ?: ""),
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
        distinctNames(rows(raw).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::decode) })

    public fun encodeRows(rows: List<Any?>): String = DartJson.encode(rows)

    /**
     * Dart `plainServerName` (`lib/state/profiles/validation.dart`, 2.1.4): a bare IP address (what the name defaulted
     * to before) reads "Computer at 192.168.1.5", this device by any of its names reads "This phone", and a host name
     * or a name the person typed is kept as is (B-149: 2.1.4 servers keep the label they had). A blank name, or one
     * that is a whole URL, takes the same rule from the server's address.
     */
    public fun plainServerName(name: String, address: String = ""): String {
        val value = name.trim()
        val bare = value.replace(BRACKETS, "")
        if (value.isNotEmpty() && AddressRules.isLoopback(bare)) return "This phone"
        if (IPV4.matches(value) || (':' in value && IPV6ISH.matches(value))) return "Computer at $bare"
        if (value.isNotEmpty() && value != "Computer" && "://" !in value) return name
        val host = (ProfileOrigins.serverOrigin(address)?.host ?: ProfileOrigins.serverOrigin(value)?.host ?: "")
            .removeSurrounding("[", "]")
        if (host.isEmpty()) return "Computer"
        if (AddressRules.isLoopback(host)) return "This phone"
        if (IPV4.matches(host) || ':' in host) return "Computer at $host"
        return if (HOSTNAME.matches(host)) host.removeSuffix(".") else "Computer"
    }

    /**
     * Dart `_distinctServerName` (`lib/ui/widgets/phone_server_names.dart`): when two saved servers would read the same
     * ("Computer at 10.0.2.2" three times) the port is added, a secure address that shares its name with a plain one
     * says "(secure)", and when the port is the same too the server's number among them. "This phone" is never
     * changed. Only the shown name changes; [profiles] keep their order.
     */
    public fun distinctNames(profiles: List<StoredProfile>): List<StoredProfile> {
        val groups = profiles.filter { it.name != "This phone" }.groupBy { it.name }
        return profiles.map { profile ->
            val same = groups[profile.name]
            if (profile.name == "This phone" || same == null || same.size < 2) return@map profile
            fun portOf(p: StoredProfile): String = explicitPort(p.baseUrl)?.let { ":$it" }.orEmpty()
            fun secure(p: StoredProfile): Boolean = p.baseUrl.trim().lowercase().startsWith("https://")
            val port = portOf(profile)
            val tag = if (secure(profile) && same.any { !secure(it) }) " (secure)" else ""
            val withPort = profile.name + port + tag
            val samePort = same.filter { portOf(it) == port }
            val name = if (samePort.size < 2) withPort else "$withPort (${samePort.indexOf(profile) + 1})"
            profile.copy(name = name)
        }
    }

    /** Dart `Uri.hasPort ? uri.port`: only a port written in the address counts. */
    private fun explicitPort(url: String): Int? {
        val authority = url.trim().substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringBefore('#').substringAfterLast('@')
        val tail = if (authority.startsWith("[")) authority.substringAfter("]", "") else authority
        val port = tail.substringAfter(':', "")
        return if (':' in tail && port.isNotEmpty() && port.all(Char::isDigit)) port.toIntOrNull() else null
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

    private val HOSTNAME = Regex("""^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9.])?$""")
    private val IPV4 = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
    private val IPV6ISH = Regex("""^[0-9a-fA-F:.\[\]]+$""")
    private val BRACKETS = Regex("""[\[\]]""")
}
