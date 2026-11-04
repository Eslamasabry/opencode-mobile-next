package io.github.eslamasabry.oc.ui.kit.content.terminal

import kotlin.io.encoding.Base64

/**
 * The clipboard-set escape (OSC 52) a program sends to copy text (B-XPLAT-009). Port of Dart `KitTerminalOsc52`
 * (`lib/ui/kit/kit_terminal_links.dart`).
 *
 * A `?` payload asks to READ the clipboard: never answered, so a program on the other end cannot learn what the person
 * copied. Payloads that are empty (a clear), not base64, not UTF-8 or past [MAX_BYTES] are ignored.
 */
public object TerminalOsc52 {
    /** The most decoded text one OSC 52 write may put on the clipboard. */
    public const val MAX_BYTES: Int = 64 * 1024

    /** The text to copy, or null. [args] are what follows `52;` split at `;`: the selection letters, then the payload. */
    public fun parse(args: List<String>): String? {
        if (args.size < 2) return null
        val payload = args.drop(1).joinToString(";").trim()
        if (payload.isEmpty() || payload == "?") return null
        // Base64 is 4/3 the size: refuse before decoding something huge.
        if (payload.length > (MAX_BYTES * 4 / 3) + 8) return null
        val bytes = decode(payload) ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun decode(payload: String): ByteArray? {
        // Dart's base64.normalize: URL-safe letters and missing padding are accepted.
        val normal = payload.replace('-', '+').replace('_', '/')
        val padded = if (normal.length % 4 == 0) normal else normal + "=".repeat(4 - normal.length % 4)
        return try {
            Base64.Default.decode(padded)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
