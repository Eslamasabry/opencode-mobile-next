package io.github.eslamasabry.oc.ui.kit.action

/** What one copy puts on the clipboard, and whether a password or key was masked in it on the way. */
public data class KitCopyText(val text: String, val masked: Boolean)

/**
 * How a kit copy part prepares its text just before the clipboard write (Flutter `KitCopy.copy(redact:)`). The kit has
 * no Redactor, so its copy parts default to [KitCopyVerbatim]; server text copies through `:ui:kit-chat`'s redacting
 * mask (`KitCopy`), which every copy of commands, tool output, reasoning and skill text must use.
 */
public fun interface KitCopyMask {
    public fun apply(text: String): KitCopyText
}

/** Copies the text exactly as given: the person's own content, or text that is already masked. */
public val KitCopyVerbatim: KitCopyMask = KitCopyMask { KitCopyText(it, masked = false) }
