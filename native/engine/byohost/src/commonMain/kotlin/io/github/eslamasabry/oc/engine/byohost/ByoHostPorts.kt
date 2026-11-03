package io.github.eslamasabry.oc.engine.byohost

// Ports the Android integrator implements. Every expected problem is a thrown [ByoHostError] with a code; any other
// throwable is treated as Transport. No remote stderr, response body or credential may enter an exception message.

/** A local loopback forward to the host's supervisor port. Closing it never stops the remote service. */
public interface ByoHostTunnel {
    public val localPort: Int

    public suspend fun close()
}

/**
 * The SSH/admin transport (Dart `ByoHostSshRunner`, production `BuiltinByoHostSshRunner`). One instance is shared by
 * every machine in a [ByoHostService]; [dispose] is called once, by the service.
 */
public interface ByoHostSshRunner {
    /** Whether SSH can run on this phone at all. */
    public suspend fun available(): Boolean

    /** Keyscan: a candidate host key, not trust. */
    public suspend fun inspectKey(target: ByoHostTarget): ByoHostKey

    /** The host signer: create (or return the existing) Keystore-held ed25519 identity for [profileId]. */
    public suspend fun generateIdentity(profileId: String): ByoHostIdentity

    /** Installs the pinned [bundle] with the one-shot admin [login] and pairs the device tuple. */
    public suspend fun install(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        login: ByoHostLogin,
        identity: ByoHostIdentity,
        deviceToken: String,
        bundle: ByoHostBundle,
    ): ByoHostDescriptor

    /** Opens a forward with the restricted phone key; no admin login. */
    public suspend fun forward(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        identity: ByoHostIdentity,
        remotePort: Int,
    ): ByoHostTunnel

    /** Asks the supervisor behind [tunnel] who it is, authenticated as the device. */
    public suspend fun describe(tunnel: ByoHostTunnel, deviceId: String, deviceToken: String): ByoHostDescriptor

    /** Revokes this device on the host. True only when the host confirmed it. */
    public suspend fun revoke(tunnel: ByoHostTunnel, deviceId: String, deviceToken: String): Boolean

    /** Local cleanup for [profileId] (known_hosts entry, Keystore alias). Never touches the host. */
    public suspend fun cleanup(profileId: String)

    public suspend fun dispose()
}

/**
 * Profile storage for BYO hosts (Dart `ByoHostStore`, production `PersistentByoHostStore`). The record is non-secret
 * preference metadata under `oc.byoHost.<profileId>` (see [ByoHostCodec]); the secrets live in one Keystore envelope.
 */
public interface ByoHostStore {
    public suspend fun listRecords(): List<ByoHostRecord>

    public suspend fun readRecord(profileId: String): ByoHostRecord?

    public suspend fun readSecrets(profileId: String): ByoHostSecrets?

    public suspend fun saveRecord(record: ByoHostRecord)

    public suspend fun saveSecrets(profileId: String, secrets: ByoHostSecrets)

    /** Removes the record and the secrets envelope (including an orphan envelope after a failed save). */
    public suspend fun remove(profileId: String)
}

/** Secure random bytes (Dart `Random.secure()`), for the device token and new machine ids. */
public fun interface ByoHostRandom {
    public fun bytes(count: Int): ByteArray
}
