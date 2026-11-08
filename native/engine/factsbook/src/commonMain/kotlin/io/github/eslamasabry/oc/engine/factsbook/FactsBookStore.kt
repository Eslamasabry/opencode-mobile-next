package io.github.eslamasabry.oc.engine.factsbook

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the app remembers about the FactsBook node a server profile is paired with (F: factsbook_store.dart
 * `FactsBookLink`). Not secret: it lives in preferences under `oc.factsbookNode.<profileId>`, in exactly Flutter's JSON
 * (`url`, `client`, `vault`, `fingerprint`, `node`, `scopes`, `owner_device`), so a pairing made by the Flutter app
 * reads here unchanged and one made here reads there.
 */
public data class FactsBookLink(
    val baseUrl: String,
    val clientId: String,
    val vault: String,
    val rootFingerprint: String,
    val node: String,
    val scopes: List<String>,
    /** The owner device id the node's console returned; without it the phone cannot approve changes. */
    val ownerDeviceId: String? = null,
) {
    public fun encode(): String =
        JsonObject(
            buildMap {
                put("url", JsonPrimitive(baseUrl))
                put("client", JsonPrimitive(clientId))
                put("vault", JsonPrimitive(vault))
                put("fingerprint", JsonPrimitive(rootFingerprint))
                put("node", JsonPrimitive(node))
                put("scopes", JsonArray(scopes.map(::JsonPrimitive)))
                ownerDeviceId?.let { put("owner_device", JsonPrimitive(it)) }
            }
        ).toString()

    /** The host part of [baseUrl] ("127.0.0.1"), empty when it cannot be read. */
    val host: String
        get() = baseUrl.substringAfter("://", "").substringBefore('/').substringBeforeLast(':').removeSurrounding("[", "]")

    /** "host:port", as the person typed it, for the node's name. */
    val authority: String get() = baseUrl.substringAfter("://", "").substringBefore('/')

    public companion object {
        /** Null for a missing or damaged record (it reads as unpaired; setup can run again). */
        public fun decode(raw: String?): FactsBookLink? {
            if (raw == null) return null
            return try {
                val json = Json.parseToJsonElement(raw) as? JsonObject ?: return null
                FactsBookLink(
                    baseUrl = json.need("url"),
                    clientId = json.need("client"),
                    vault = json.need("vault"),
                    rootFingerprint = json.need("fingerprint"),
                    node = json.need("node"),
                    scopes = (json["scopes"] as? JsonArray ?: return null).map { (it as JsonPrimitive).content },
                    ownerDeviceId = json.str("owner_device"),
                )
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: IllegalStateException) {
                null
            } catch (e: ClassCastException) {
                null
            }
        }
    }
}

/**
 * Where FactsBook keeps things on this phone, per server profile, under the Flutter names (`oc.<what>.<profileId>`, so
 * the profile deletion sweep finds them; `DeferredFeatureData` and `ProfileKeys.SECRET_PREFIXES` register them):
 * - the pairing record in preferences (`oc.factsbookNode.<id>`);
 * - the bearer token, the owner key's seed and the outbox of unsent captures in the secret store
 *   (`oc.factsbook.<id>`, `oc.factsbookOwner.<id>`, `oc.factsbookOutbox.<id>`). The day-1 migration copies Flutter's
 *   secure-storage entries there under the same names (M2Secrets, `Deferred`), so nothing is lost at the switch.
 *
 * Nothing here is logged.
 */
public class FactsBookStore(
    private val prefs: KeyValueStore,
    private val secrets: SecretStore,
    /** The device-wide lane: the developer switch. */
    private val appLane: WriterLane,
    /** Each profile's own lane, so a profile that is being deleted admits no new pairing. */
    private val profileLane: (String) -> WriterLane = { appLane },
) {
    public fun link(profileId: String): FactsBookLink? = FactsBookLink.decode(prefs.string(linkKey(profileId)))

    public suspend fun token(profileId: String): String? = secrets.read(tokenKey(profileId)).orNull()

    public suspend fun hasOwnerSeed(profileId: String): Boolean = secrets.read(ownerKey(profileId)).orNull() != null

    /**
     * The owner key's seed, made on first use from [secureBytes] (F: FactsBookStore.ownerSeed). A damaged seed is
     * replaced and the console enrols the new key. Null when the secret store cannot keep it.
     */
    public suspend fun ownerSeed(profileId: String, secureBytes: (Int) -> ByteArray): ByteArray? {
        secrets.read(ownerKey(profileId)).orNull()?.let(::bytesOfHex)?.takeIf { it.size == 32 }?.let { return it }
        val seed = secureBytes(32)
        return if (secrets.write(ownerKey(profileId), hexOf(seed)) is Outcome.Ok) seed else null
    }

    public suspend fun readOutbox(profileId: String): String? = secrets.read(outboxKey(profileId)).orNull()

    public suspend fun writeOutbox(profileId: String, value: String?): Boolean =
        (if (value == null) secrets.delete(outboxKey(profileId)) else secrets.write(outboxKey(profileId), value)) is Outcome.Ok

    /** Saves a pairing: the token first, so a record never exists without it. */
    public suspend fun savePairing(profileId: String, link: FactsBookLink, token: String): Boolean {
        if (secrets.write(tokenKey(profileId), token) !is Outcome.Ok) return false
        if (prefs.commit(profileLane(profileId)) { putString(linkKey(profileId), link.encode()) } !is Outcome.Ok) {
            secrets.delete(tokenKey(profileId))
            return false
        }
        return true
    }

    /** Forgets the node: the pairing, the token and the owner key. The outbox stays until the person discards it. */
    public suspend fun forget(profileId: String): Boolean {
        val token = secrets.delete(tokenKey(profileId))
        val owner = secrets.delete(ownerKey(profileId))
        val link = prefs.commit(profileLane(profileId)) { remove(linkKey(profileId)) }
        return token is Outcome.Ok && owner is Outcome.Ok && link is Outcome.Ok
    }

    /** The hidden developer switch (Flutter About › Developer). App-wide, not per profile. */
    public fun flagOn(): Boolean = prefs.bool(FLAG_KEY) == true

    public suspend fun setFlag(on: Boolean): Boolean = prefs.commit(appLane) { putBool(FLAG_KEY, on) } is Outcome.Ok

    /** The person stopped the memory on this phone and has not started it since (app-wide, F: `personStoppedKey`). */
    public fun nodeStopped(): Boolean = prefs.bool(NODE_STOPPED_KEY) == true

    public suspend fun setNodeStopped(stopped: Boolean): Boolean =
        prefs.commit(appLane) { if (stopped) putBool(NODE_STOPPED_KEY, true) else remove(NODE_STOPPED_KEY) } is Outcome.Ok

    public companion object {
        /** F: factsbook_flag.dart `FactsBookFlag.preferenceKey`. */
        public const val FLAG_KEY: String = "oc.dev.factsbook"

        /** F: factsbook_phone_node.dart `personStoppedKey`. */
        public const val NODE_STOPPED_KEY: String = "oc.factsbook-node.stopped"

        public fun linkKey(profileId: String): String = "oc.factsbookNode.$profileId"

        public fun tokenKey(profileId: String): String = "oc.factsbook.$profileId"

        public fun ownerKey(profileId: String): String = "oc.factsbookOwner.$profileId"

        public fun outboxKey(profileId: String): String = "oc.factsbookOutbox.$profileId"

        private fun <T> Outcome<T?>.orNull(): T? = (this as? Outcome.Ok)?.value
    }
}

/**
 * Where a node may be reached (F: factsbook_address.dart): loopback, the local network or a private overlay. Anything
 * else is refused: the bearer token travels in clear text, so only a private path is acceptable.
 */
public object FactsBookAddress {
    /** "127.0.0.1:8787", "http://192.168.1.20:8787", "phone.local:8787" → a base URL, or null. */
    public fun parse(input: String): String? {
        var text = input.trim()
        if (text.isEmpty() || text.length > 200) return null
        if (!text.contains("://")) text = "http://$text"
        val scheme = text.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        val rest = text.substringAfter("://")
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val tail = rest.substring(authority.length)
        if (tail.contains('?') || tail.contains('#') || authority.contains('@')) return null
        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            host = authority.substringBefore(']') + "]"
            portText = authority.substringAfter("]", "").removePrefix(":").ifEmpty { null }
        } else {
            host = authority.substringBefore(':')
            portText = if (authority.contains(':')) authority.substringAfter(':') else null
        }
        if (host.isEmpty() || !isPrivateHost(host)) return null
        val port =
            if (portText == null) (if (scheme == "https") 443 else 8787)
            else portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return "$scheme://${host.lowercase()}:$port"
    }

    /** Loopback, RFC 1918, link-local, the 100.64.0.0/10 overlay range, and the names `.local` and `.ts.net`. */
    public fun isPrivateHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "::1" || lower == "[::1]") return true
        if (lower.endsWith(".local") || lower.endsWith(".ts.net")) return true
        val parts = lower.split('.')
        if (parts.size != 4) return false
        val o = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        val a = o[0]
        val b = o[1]
        return a == 127 || a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) || (a == 100 && b in 64..127)
    }
}
