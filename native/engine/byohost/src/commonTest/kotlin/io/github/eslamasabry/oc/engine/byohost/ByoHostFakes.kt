package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.io.encoding.Base64
import kotlin.test.assertIs

fun key(value: Int = 1): ByoHostKey {
    val blob =
        byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".encodeToByteArray() + byteArrayOf(0, 0, 0, 32) +
            ByteArray(32) { value.toByte() }
    val digest = DartJson.sha256Hex(blob).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return ByoHostKey.of("ssh-ed25519 ${Base64.encode(blob)}", "SHA256:${Base64.encode(digest).trimEnd('=')}")!!
}

fun <T> Outcome<T>.value(): T = assertIs<Outcome.Ok<T>>(this).value

class CountingRandom : ByoHostRandom {
    private var next = 0

    override fun bytes(count: Int): ByteArray = ByteArray(count) { (next++).toByte() }
}

class MemoryStore : ByoHostStore {
    var record: ByoHostRecord? = null
    var secrets: ByoHostSecrets? = null
    var failRemove = false

    override suspend fun listRecords(): List<ByoHostRecord> = listOfNotNull(record)

    override suspend fun readRecord(profileId: String): ByoHostRecord? = record

    override suspend fun readSecrets(profileId: String): ByoHostSecrets? = secrets

    override suspend fun saveRecord(record: ByoHostRecord) {
        this.record = record
    }

    override suspend fun saveSecrets(profileId: String, secrets: ByoHostSecrets) {
        this.secrets = secrets
    }

    override suspend fun remove(profileId: String) {
        if (failRemove) error("sensitive raw failure")
        record = null
        secrets = null
    }
}

class Tunnel : ByoHostTunnel {
    var closed = false
    override val localPort: Int = 19001

    override suspend fun close() {
        closed = true
    }
}

open class FakeRunner : ByoHostSshRunner {
    var hostKey = key()
    var hostId = "host-a"
    var installs = 0
    var forwards = 0
    var revokes = 0
    val installTokens = mutableListOf<String>()
    var dieAfterInstall = false
    var rejectRevoke = false
    var tunnel: Tunnel? = null
    var bundleVersion = "1.1.0"
    var openCodeVersion = "2.1.1"

    val descriptor: ByoHostDescriptor get() = ByoHostDescriptor(hostId, bundleVersion, openCodeVersion, 4096)

    override suspend fun available(): Boolean = true

    override suspend fun inspectKey(target: ByoHostTarget): ByoHostKey = hostKey

    override suspend fun generateIdentity(profileId: String): ByoHostIdentity =
        ByoHostIdentity("oc.byoHostSsh.phone-a", "fake-public-key")

    override suspend fun install(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        login: ByoHostLogin,
        identity: ByoHostIdentity,
        deviceToken: String,
        bundle: ByoHostBundle,
    ): ByoHostDescriptor {
        installs++
        installTokens += deviceToken
        if (dieAfterInstall) error("password in remote error")
        return descriptor
    }

    override suspend fun forward(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        identity: ByoHostIdentity,
        remotePort: Int,
    ): ByoHostTunnel {
        forwards++
        return Tunnel().also { tunnel = it }
    }

    override suspend fun describe(tunnel: ByoHostTunnel, deviceId: String, deviceToken: String): ByoHostDescriptor =
        descriptor

    override suspend fun revoke(tunnel: ByoHostTunnel, deviceId: String, deviceToken: String): Boolean {
        revokes++
        return !rejectRevoke
    }

    override suspend fun cleanup(profileId: String) {}

    override suspend fun dispose() {}
}

val testBundle: ByoHostBundle =
    ByoHostBundle.of(
        "1.1.0",
        "2.1.1",
        mapOf("x64" to ByoHostArtifact.of("https://example.invalid/bundle.tar.gz", "a".repeat(64))!!),
    )!!
