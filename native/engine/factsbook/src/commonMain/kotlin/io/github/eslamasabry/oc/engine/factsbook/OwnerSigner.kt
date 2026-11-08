package io.github.eslamasabry.oc.engine.factsbook

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 8785 JSON Canonicalization Scheme, for the bytes an owner signature covers (F: jcs.dart). Object keys sort by
 * UTF-16 code units, strings escape only what JSON requires, and numbers must be integers (the contract uses no others).
 */
internal object Jcs {
    fun bytes(value: JsonElement): ByteArray = string(value).encodeToByteArray()

    /** The bytes, or null when [value] holds a number JCS here does not carry. */
    fun bytesOrNull(value: JsonElement): ByteArray? = if (integersOnly(value)) bytes(value) else null

    private fun integersOnly(value: JsonElement): Boolean =
        when (value) {
            JsonNull -> true
            is JsonPrimitive -> value.isString || value.content == "true" || value.content == "false" || isInteger(value.content)
            is JsonArray -> value.all(::integersOnly)
            is JsonObject -> value.values.all(::integersOnly)
        }

    private fun isInteger(raw: String): Boolean =
        raw.toLongOrNull() != null || raw.toDoubleOrNull()?.let { it.isFinite() && it == kotlin.math.truncate(it) } == true

    fun string(value: JsonElement): String = buildString { write(this, value) }

    private fun write(out: StringBuilder, value: JsonElement) {
        when (value) {
            JsonNull -> out.append("null")
            is JsonPrimitive ->
                when {
                    value.isString -> string(out, value.content)
                    value.content == "true" || value.content == "false" -> out.append(value.content)
                    else -> out.append(integer(value.content))
                }
            is JsonArray -> {
                out.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            is JsonObject -> {
                out.append('{')
                value.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) out.append(',')
                    string(out, key)
                    out.append(':')
                    write(out, value.getValue(key))
                }
                out.append('}')
            }
        }
    }

    private fun integer(raw: String): String {
        raw.toLongOrNull()?.let { return it.toString() }
        val d = raw.toDoubleOrNull() ?: throw IllegalArgumentException("Not a JSON number")
        require(d.isFinite() && d == kotlin.math.truncate(d)) { "JCS here carries integers only" }
        return d.toLong().toString()
    }

    private fun string(out: StringBuilder, text: String) {
        out.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\t' -> out.append("\\t")
                '\n' -> out.append("\\n")
                '\u000c' -> out.append("\\f")
                '\r' -> out.append("\\r")
                else ->
                    if (ch.code < 0x20) out.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    else out.append(ch)
            }
        }
        out.append('"')
    }
}

/**
 * Asked right before an owner signature: the person's screen lock (F: device_authenticator.dart). The answer is the
 * time it succeeded, or a [FactsBookError] with `device_auth_unavailable`, `device_auth_cancelled`,
 * `device_auth_locked` or `device_auth_failed`.
 */
public fun interface FactsBookDeviceCheck {
    public suspend fun confirm(): FbResult<Instant>
}

/**
 * What owner signing needs from the platform: random bytes from a secure source (the app: `SecureRandom`) for the
 * owner key and each proof's nonce, and the screen lock asked before each signature (null signs with the clock's time:
 * tests only).
 */
public class FactsBookSigning(
    public val secureBytes: (Int) -> ByteArray,
    public val deviceCheck: FactsBookDeviceCheck? = null,
)

/**
 * Signs owner-only changes (review decisions, area sharing, settings, rules) the way the node verifies them
 * (F: owner_signer.dart): the Ed25519 signature of the canonical `{vault, client, method, path, body, nonce,
 * authenticated_at}`, with the body as sent but without `proof`, an upper-case method, the path without its query, a
 * fresh nonce and the time the person confirmed with their screen lock. A stolen bearer token alone cannot make this.
 */
public class OwnerSigner(
    private val seed: ByteArray,
    public val deviceId: String,
    /** The vault and client the proof names. */
    private val node: FactsBookLink,
    private val signing: FactsBookSigning,
    private val now: () -> Instant,
) {
    private val vault: String get() = node.vault
    private val clientId: String get() = node.clientId

    override fun toString(): String = "OwnerSigner(device=$deviceId)"

    /** The public key as hex: what the console's `--owner-key` takes. */
    public val publicKeyHex: String by lazy { hexOf(Ed25519.publicKey(seed)) }

    /** The `proof` object for [body] sent with [method] to [path]. */
    public suspend fun proof(method: String, path: String, body: JsonObject): FbResult<JsonObject> {
        val nonce = hexOf(signing.secureBytes(16))
        val at =
            when (val check = signing.deviceCheck?.confirm() ?: FbResult.Ok(now())) {
                is FbResult.Err -> return check
                is FbResult.Ok -> stamp(check.value)
            }
        val envelope =
            JsonObject(
                mapOf(
                    "vault" to JsonPrimitive(vault),
                    "client" to JsonPrimitive(clientId),
                    "method" to JsonPrimitive(method.uppercase()),
                    "path" to JsonPrimitive(path),
                    "body" to JsonObject(body - "proof"),
                    "nonce" to JsonPrimitive(nonce),
                    "authenticated_at" to JsonPrimitive(at),
                )
            )
        val sig = hexOf(Ed25519.sign(seed, Jcs.bytes(envelope)))
        return FbResult.Ok(
            JsonObject(
                mapOf(
                    "signer" to JsonPrimitive(deviceId),
                    "nonce" to JsonPrimitive(nonce),
                    "authenticated_at" to JsonPrimitive(at),
                    "sig" to JsonPrimitive(sig),
                )
            )
        )
    }

    internal companion object {
        /** `yyyy-MM-ddTHH:mm:ssZ` in UTC, whole seconds. */
        fun stamp(instant: Instant): String {
            val t = instant.toLocalDateTime(TimeZone.UTC)
            fun two(v: Int) = v.toString().padStart(2, '0')
            return "${t.year.toString().padStart(4, '0')}-${two(t.month.ordinal + 1)}-${two(t.day)}T" +
                "${two(t.hour)}:${two(t.minute)}:${two(t.second)}Z"
        }
    }
}

/** What checking the node's signed control history found (F: control_verifier.dart). */
public data class ControlVerification(
    val fingerprintMatches: Boolean,
    val pinMatches: Boolean,
    val signaturesValid: Boolean,
    val chainLinked: Boolean,
    val forked: Boolean,
) {
    val trusted: Boolean get() = fingerprintMatches && pinMatches && signaturesValid && chainLinked && !forked
}

/** `GET /v1/control`: the signed control chain and the root's verification material. */
public data class ControlChain(
    val rootFingerprint: String,
    val rootPublicKey: String,
    val records: List<JsonObject>,
    val forked: Boolean,
) {
    internal companion object {
        fun read(json: JsonObject): ControlChain =
            ControlChain(
                rootFingerprint = json.need("root_fingerprint"),
                rootPublicKey = json.need("root_public_key"),
                records = json.arr("records").mapNotNull { it as? JsonObject },
                forked = json.isTrue("forked"),
            )
    }
}

/** Checks a control chain against the fingerprint pinned at pairing; a fingerprint the node offers is never taken. */
public object ControlVerifier {
    public fun verify(chain: ControlChain, pinnedFingerprint: String): ControlVerification {
        val key = bytesOfHex(chain.rootPublicKey)
        if (key == null || key.size != 32) return ControlVerification(false, false, false, false, chain.forked)
        val fingerprint = io.github.eslamasabry.oc.core.common.dart.DartJson.sha256Hex(key)
        var signatures = chain.records.isNotEmpty()
        var linked = chain.records.isNotEmpty()
        var previous: String? = null
        chain.records.forEachIndexed { i, record ->
            val sig = record.str("sig")?.let(::bytesOfHex)
            val signed = Jcs.bytesOrNull(JsonObject(record - "sig"))
            if (sig == null || signed == null || !Ed25519.verify(key, signed, sig)) signatures = false
            val prev = record.str("prev")
            if (if (i == 0) prev != null else prev != previous) linked = false
            previous = Jcs.bytesOrNull(record)?.let { io.github.eslamasabry.oc.core.common.dart.DartJson.sha256Hex(it) }
        }
        return ControlVerification(
            fingerprintMatches = fingerprint == chain.rootFingerprint,
            pinMatches = fingerprint == pinnedFingerprint,
            signaturesValid = signatures,
            chainLinked = linked,
            forked = chain.forked,
        )
    }
}
