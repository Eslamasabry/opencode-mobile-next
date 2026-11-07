package io.github.eslamasabry.opencode_mobile.graph

import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner decision 2026-11-07: the Termux app is not supported. Built-in Linux is the only engine on the phone and a
 * computer is the other way to run OpenCode.
 *
 * Someone who moved from the Flutter app with a Termux setup is told so once, in the app notice slot, with two ways
 * forward ("Set up built-in Linux", "Connect a computer"). Nothing is imported from Termux and nothing is changed:
 * the saved Termux server stays a saved server like any other, and every other migrated record is untouched.
 *
 * A Termux setup is recognised by either sign the Flutter app leaves behind: its setup file
 * (`files/termux-setup-v2.json`) or a saved OpenCode server at Termux's managed address ([isTermuxAddress]).
 */
class TermuxRetiredNotice(
    /** Whether the Flutter app's Termux setup file is in the app's files folder. */
    private val legacySetupFile: suspend () -> Boolean,
    /** Every saved server address (base URL), after migration. */
    private val savedAddresses: suspend () -> List<String>,
    private val memory: Memory,
) {
    /** The app-wide "already told" flag ([SEEN_KEY]). */
    interface Memory {
        suspend fun seen(): Boolean

        suspend fun markSeen()
    }

    private val mutable = MutableStateFlow(false)

    /** True while the notice should be in the slot. */
    val shown: StateFlow<Boolean> = mutable.asStateFlow()

    private var closed = false

    /** Reads the signs again; call when the app starts or after migration finished. */
    suspend fun check() {
        if (closed) return
        val show =
            !memory.seen() && (legacySetupFile() || savedAddresses().any(::isTermuxAddress))
        if (!closed) mutable.value = show
    }

    /** Either action or Dismiss: the notice does not come back. The caller opens the chosen page. */
    suspend fun close() {
        closed = true
        mutable.value = false
        memory.markSeen()
    }

    companion object {
        /** The notice slot id. */
        const val ID: String = "termux-retired"

        /** App-scoped flag: the person has closed the notice. */
        const val SEEN_KEY: String = "oc.termuxRetiredNoticeSeen"

        /** The Flutter app's Termux setup file, left untouched in `files/`. */
        const val LEGACY_SETUP_FILE: String = "termux-setup-v2.json"

        /** Termux's managed OpenCode port in the Flutter app (its server was `http://127.0.0.1:4096`). */
        private const val TERMUX_PORT = 4096

        /**
         * Whether [url] is the Flutter app's managed Termux server on this phone: Flutter
         * `TermuxBridge.managesServerUrl` (scheme `http`, host exactly `127.0.0.1`, port 4096).
         */
        fun isTermuxAddress(url: String?): Boolean {
            if (url == null) return false
            val uri =
                try {
                    URI(url)
                } catch (_: Exception) {
                    return false
                }
            val port = if (uri.port == -1) 80 else uri.port
            return uri.scheme == "http" && uri.host == "127.0.0.1" && port == TERMUX_PORT
        }
    }
}
