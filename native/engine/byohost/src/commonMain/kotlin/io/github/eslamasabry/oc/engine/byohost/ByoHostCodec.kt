package io.github.eslamasabry.oc.engine.byohost

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The Dart storage shapes, for the store adapter: the record is `oc.byoHost.<profileId>` metadata (schema 1, no secret
 * ever) and the secrets are one Keystore envelope (schema 2, never a private key). Decode returns null for anything
 * the Dart decoder would refuse ([ByoHostFailureCode.Storage]).
 */
public object ByoHostCodec {
    private val json = Json

    private fun ByoHostPhase.wire(): String = name.replaceFirstChar { it.lowercaseChar() }

    private fun phaseOf(wire: String?): ByoHostPhase? = ByoHostPhase.entries.firstOrNull { it.wire() == wire }

    public fun encodeRecord(record: ByoHostRecord): String =
        buildJsonObject {
            put("schema", 1)
            put("profileId", record.profileId)
            put(
                "target",
                buildJsonObject {
                    put("user", record.target.user)
                    put("host", record.target.host)
                    put("port", record.target.port)
                },
            )
            put(
                "hostKey",
                buildJsonObject {
                    put("publicKey", record.hostKey.publicKey)
                    put("fingerprint", record.hostKey.fingerprint)
                },
            )
            put("phase", record.phase.wire())
            put("hostId", record.hostId)
            put("bundleVersion", record.bundleVersion)
            put("openCodeVersion", record.openCodeVersion)
            put("remotePort", record.remotePort)
            put("revoked", record.revoked)
        }.toString()

    public fun decodeRecord(text: String): ByoHostRecord? {
        val root = parse(text) ?: return null
        if (root.int("schema") != 1) return null
        val profileId = root.string("profileId") ?: return null
        if (!byoHostSafeId(profileId)) return null
        val target = (root["target"] as? JsonObject)?.let { t ->
            val user = t.string("user") ?: return null
            val host = t.string("host") ?: return null
            ByoHostTarget.of(user, host, t.int("port") ?: return null)
        } ?: return null
        val key = (root["hostKey"] as? JsonObject)?.let { k ->
            ByoHostKey.of(k.string("publicKey") ?: return null, k.string("fingerprint") ?: return null)
        } ?: return null
        return ByoHostRecord(
            profileId = profileId,
            target = target,
            hostKey = key,
            phase = phaseOf(root.string("phase")) ?: return null,
            hostId = root.string("hostId"),
            bundleVersion = root.string("bundleVersion"),
            openCodeVersion = root.string("openCodeVersion"),
            remotePort = root.int("remotePort"),
            revoked = (root["revoked"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    public fun encodeSecrets(secrets: ByoHostSecrets): String =
        buildJsonObject {
            put("schema", 2)
            put("keyAlias", secrets.identity.keyAlias)
            put("publicKey", secrets.identity.publicKey)
            put("deviceToken", secrets.deviceToken)
        }.toString()

    public fun decodeSecrets(text: String): ByoHostSecrets? {
        val root = parse(text) ?: return null
        if (root.int("schema") != 2 || root.containsKey("privateKey")) return null
        return ByoHostSecrets(
            identity = ByoHostIdentity(root.string("keyAlias") ?: return null, root.string("publicKey") ?: return null),
            deviceToken = root.string("deviceToken") ?: return null,
        )
    }

    private fun parse(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        this[key]?.takeUnless { it is JsonNull }?.let { it as? JsonPrimitive }

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = primitive(key)?.takeUnless { it.isString }?.intOrNull
}
