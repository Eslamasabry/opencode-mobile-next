package io.github.eslamasabry.oc.ui.kit.foundation

import io.github.eslamasabry.oc.ui.kit.text.KitRedact

/** The copy sequence from kit_copy.dart: redact, tick, write, announce once.
 * The platform owner supplies clipboard and announcement operations; words are localized by the caller.
 */
public object KitCopy {
    public suspend fun copy(
        text: String,
        announcement: String,
        writeClipboard: suspend (String) -> Unit,
        announce: suspend (String) -> Unit,
        sendHaptic: () -> Unit,
        redact: Boolean = true,
    ) {
        val safe = if (redact) KitRedact.text(text) else text
        sendHaptic()
        writeClipboard(safe)
        announce(announcement)
    }
}
