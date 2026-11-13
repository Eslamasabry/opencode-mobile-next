package io.github.eslamasabry.opencode_mobile.graph

/**
 * The address the phone server's own profile should carry.
 *
 * The Flutter app (2.x) ran its in-app server on 127.0.0.1:4097 and saved that in the "This phone" profile. This app
 * runs its own server on [nativePort]. A profile that still holds the old in-app address is moved to the new one; any
 * other address (a computer, a different port the person typed) is left exactly as it is.
 *
 * B-239 / B-119: this app's server itself can come up on another of its ports (its own was held for a moment, or it
 * comes back home after it had moved). A profile left on one of those ports asked a server that was not there and the
 * app said "Not answering" until it was restarted; it now follows the port the server runs on.
 */
internal object PhoneProfileAddress {
    /** The Flutter app's in-app server port. */
    const val FLUTTER_PORT: Int = 4097

    /** The ports this app's phone server takes (BuiltinLinux's PhoneServerPorts). */
    private fun ownPort(port: Int): Boolean = port == FLUTTER_PORT || port == 4098 || port in 4100..4199

    /** The address to save instead of [baseUrl], or null when nothing needs to change. */
    fun adopted(baseUrl: String, nativePort: Int): String? {
        if (nativePort == FLUTTER_PORT) return null
        val old = baseUrl.trimEnd('/')
        val port =
            listOf("http://127.0.0.1:", "http://localhost:")
                .firstNotNullOfOrNull { prefix -> old.removePrefix(prefix).takeIf { old.startsWith(prefix) } }
                ?.toIntOrNull()
        return if (port != null && port != nativePort && ownPort(port)) "http://127.0.0.1:$nativePort" else null
    }
}
