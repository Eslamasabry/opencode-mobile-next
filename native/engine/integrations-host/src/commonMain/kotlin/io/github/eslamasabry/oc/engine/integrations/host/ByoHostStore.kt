package io.github.eslamasabry.oc.engine.integrations.host

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Flutter `ByoHostStore`. */
public interface ByoHostStore {
    public suspend fun listRecords(): HostResult<ImmutableList<ByoHostRecord>>

    public suspend fun readRecord(profileId: String): HostResult<ByoHostRecord?>

    public suspend fun readSecrets(profileId: String): HostResult<ByoHostSecrets?>

    public suspend fun saveRecord(record: ByoHostRecord): HostResult<Unit>

    public suspend fun saveSecrets(profileId: String, secrets: ByoHostSecrets): HostResult<Unit>

    public suspend fun remove(profileId: String): HostResult<Unit>
}

/**
 * Profile-scoped metadata (`oc.byoHost.<profileId>` in preferences) and one separately encrypted secret envelope
 * (`oc.byoHostSecrets.<profileId>` in the [SecretStore]) — Flutter `PersistentByoHostStore`. Every failure is the fixed
 * `Storage` code: plugin errors and malformed JSON may contain credentials, so none of their text is kept. Operations
 * run one at a time so an overlapping save cannot resurrect a secret between the two deletion steps. The profile
 * deletion coordinator must call [remove]: the ordinary preference sweep does not delete secure entries.
 */
public class PersistentByoHostStore(
    private val prefs: KeyValueStore,
    private val secure: SecretStore,
    private val lane: WriterLane,
    private val signer: ByoHostSigner? = null,
) : ByoHostStore {
    private val order = Mutex()

    private suspend fun <T> ordered(block: suspend () -> T): HostResult<T> =
        order.withLock { hostResult(ByoHostFailureCode.Storage) { block() } }

    override suspend fun listRecords(): HostResult<ImmutableList<ByoHostRecord>> =
        ordered {
            prefs.keys(RECORD_PREFIX).sorted().map { key ->
                val id = key.removePrefix(RECORD_PREFIX)
                validateId(id)
                decodeRecord(id, prefs.string(key) ?: abort(ByoHostFailureCode.Storage))
            }.toImmutableList()
        }

    override suspend fun readRecord(profileId: String): HostResult<ByoHostRecord?> =
        ordered {
            validateId(profileId)
            prefs.string(recordKey(profileId))?.let { decodeRecord(profileId, it) }
        }

    override suspend fun readSecrets(profileId: String): HostResult<ByoHostSecrets?> =
        ordered {
            validateId(profileId)
            val raw = secure.read(secretKey(profileId)).ok() ?: return@ordered null
            val secrets = decodeSecrets(raw)
            if (secrets.identity.keyAlias != "oc.byoHostSsh.$profileId") abort(ByoHostFailureCode.Storage)
            secrets
        }

    override suspend fun saveRecord(record: ByoHostRecord): HostResult<Unit> =
        ordered {
            validateId(record.profileId)
            val text = encodeRecord(record)
            prefs.commit(lane) { putString(recordKey(record.profileId), text) }.ok()
        }

    override suspend fun saveSecrets(profileId: String, secrets: ByoHostSecrets): HostResult<Unit> =
        ordered {
            validateId(profileId)
            if (secrets.identity.keyAlias != "oc.byoHostSsh.$profileId") abort(ByoHostFailureCode.Storage)
            secure.write(secretKey(profileId), encodeSecrets(secrets)).ok()
        }

    override suspend fun remove(profileId: String): HostResult<Unit> =
        ordered {
            validateId(profileId)
            val key = secretKey(profileId)
            signer?.deleteIdentity(profileId)
            secure.delete(key).ok()
            // Refuse to discard the cleanup record if the platform reports success but keeps the vault entry. Either
            // partial deletion is safe to retry.
            if (secure.read(key).ok() != null) abort(ByoHostFailureCode.Storage)
            prefs.commit(lane) { remove(recordKey(profileId)) }.ok()
        }

    public companion object {
        public const val RECORD_PREFIX: String = "oc.byoHost."
        public const val SECRET_PREFIX: String = "oc.byoHostSecrets."

        public fun recordKey(profileId: String): String = "$RECORD_PREFIX$profileId"

        public fun secretKey(profileId: String): String = "$SECRET_PREFIX$profileId"

        private fun validateId(id: String) {
            if (!byoHostSafeId(id)) abort(ByoHostFailureCode.Storage)
        }

        private fun <T> Outcome<T>.ok(): T =
            when (this) {
                is Outcome.Ok -> value
                is Outcome.Failed -> abort(ByoHostFailureCode.Storage)
            }

        internal fun encodeSecrets(secrets: ByoHostSecrets): String =
            JsonObject(
                mapOf(
                    "schema" to JsonPrimitive(2),
                    "keyAlias" to JsonPrimitive(secrets.identity.keyAlias),
                    "publicKey" to JsonPrimitive(secrets.identity.publicKey),
                    "deviceToken" to JsonPrimitive(secrets.deviceToken),
                ),
            ).toString()

        /** Legacy exportable identities (schema 1 with a private key) are refused, never returned. */
        internal fun decodeSecrets(raw: String): ByoHostSecrets {
            val json = Json.parseToJsonElement(raw) as? JsonObject ?: abort(ByoHostFailureCode.Storage)
            if (json["schema"].int() != 2 || json.containsKey("privateKey")) abort(ByoHostFailureCode.Storage)
            return ByoHostSecrets(
                ByoHostIdentity(json["keyAlias"].str() ?: abort(ByoHostFailureCode.Storage), json["publicKey"].str() ?: abort(ByoHostFailureCode.Storage)),
                json["deviceToken"].str() ?: abort(ByoHostFailureCode.Storage),
            )
        }

        internal fun encodeRecord(record: ByoHostRecord): String {
            fun opt(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
            return JsonObject(
                mapOf(
                    "schema" to JsonPrimitive(1),
                    "profileId" to JsonPrimitive(record.profileId),
                    "target" to JsonObject(
                        mapOf(
                            "user" to JsonPrimitive(record.target.user),
                            "host" to JsonPrimitive(record.target.host),
                            "port" to JsonPrimitive(record.target.port),
                        ),
                    ),
                    "hostKey" to JsonObject(
                        mapOf(
                            "publicKey" to JsonPrimitive(record.hostKey.publicKey),
                            "fingerprint" to JsonPrimitive(record.hostKey.fingerprint),
                        ),
                    ),
                    "phase" to JsonPrimitive(record.phase.wire()),
                    "hostId" to opt(record.hostId),
                    "bundleVersion" to opt(record.bundleVersion),
                    "openCodeVersion" to opt(record.openCodeVersion),
                    "remotePort" to (record.remotePort?.let(::JsonPrimitive) ?: JsonNull),
                    "revoked" to JsonPrimitive(record.revoked),
                ),
            ).toString()
        }

        /** Rejects corrupt, other-schema and cross-profile metadata. */
        internal fun decodeRecord(profileId: String, raw: String): ByoHostRecord {
            val json = Json.parseToJsonElement(raw) as? JsonObject ?: abort(ByoHostFailureCode.Storage)
            val id = json["profileId"].str()
            if (json["schema"].int() != 1 || id == null || !byoHostSafeId(id) || id != profileId) abort(ByoHostFailureCode.Storage)
            val target = json["target"] as? JsonObject ?: abort(ByoHostFailureCode.Storage)
            val key = json["hostKey"] as? JsonObject ?: abort(ByoHostFailureCode.Storage)
            val phaseName = json["phase"].str()
            return ByoHostRecord(
                profileId = id,
                target = ByoHostTarget.of(
                    target["user"].str() ?: abort(ByoHostFailureCode.Storage),
                    target["host"].str() ?: abort(ByoHostFailureCode.Storage),
                    target["port"].int() ?: abort(ByoHostFailureCode.Storage),
                ) ?: abort(ByoHostFailureCode.Storage),
                hostKey = ByoHostKey.parse(
                    key["publicKey"].str() ?: abort(ByoHostFailureCode.Storage),
                    key["fingerprint"].str() ?: abort(ByoHostFailureCode.Storage),
                ) ?: abort(ByoHostFailureCode.Storage),
                phase = ByoHostPhase.entries.firstOrNull { it.wire() == phaseName } ?: abort(ByoHostFailureCode.Storage),
                hostId = json["hostId"].str(),
                bundleVersion = json["bundleVersion"].str(),
                openCodeVersion = json["openCodeVersion"].str(),
                remotePort = json["remotePort"].int(),
                revoked = (json["revoked"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }

        /** Dart enum `.name`: lower camel case. */
        private fun ByoHostPhase.wire(): String = name.replaceFirstChar { it.lowercaseChar() }

        private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    }
}
