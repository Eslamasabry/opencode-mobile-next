package io.github.eslamasabry.opencode_mobile.graph

/**
 * The address the phone server's own profile should carry.
 *
 * The Flutter app (2.x) ran its in-app server on 127.0.0.1:4097 and saved that in the "This phone" profile. This app
 * runs its own server on [nativePort]. A profile that still holds the old in-app address is moved to the new one; any
 * other address (a computer, a different port the person typed) is left exactly as it is.
 */
internal object PhoneProfileAddress {
    /** The Flutter app's in-app server port. */
    const val FLUTTER_PORT: Int = 4097

    /** The address to save instead of [baseUrl], or null when nothing needs to change. */
    fun adopted(baseUrl: String, nativePort: Int): String? {
        if (nativePort == FLUTTER_PORT) return null
        val old = baseUrl.trimEnd('/')
        return if (old == "http://127.0.0.1:$FLUTTER_PORT" || old == "http://localhost:$FLUTTER_PORT") {
            "http://127.0.0.1:$nativePort"
        } else {
            null
        }
    }
}
