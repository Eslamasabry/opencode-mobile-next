package io.github.eslamasabry.oc.engine.profiles

/**
 * "This saved server is the OpenCode the app runs on this phone": Flutter `looksLikeInAppServer` (an OpenCode profile at
 * `BuiltinLinux.managesServerUrl`). Keyed by the app's own runtime fact, the port this build runs its server on, plus
 * plain HTTP on the phone's own address; never by "the address is loopback" alone, because a tunnel to a computer is
 * loopback too (on another port, since the app's server holds this one). Setup also adopts a `localhost` row at that
 * port as the phone's server (BuiltinRuntime.finishSetup), so that spelling counts the same.
 *
 * The flag is not stored: [ProfileStore] applies this rule each time it publishes a row, so rows saved before the rule
 * (by setup, the Flutter-data migration, an edit or an older build) carry the right answer on their next load.
 */
public object PhoneServerProfile {
    private val hosts = setOf("127.0.0.1", "localhost")

    public fun managedOnPhone(profile: StoredProfile, port: Int): Boolean {
        if (profile.usesAgentSocket) return false
        val origin = ProfileOrigins.serverOrigin(profile.baseUrl) ?: return false
        return origin.scheme == "http" && origin.host.lowercase() in hosts && origin.port == port
    }
}
