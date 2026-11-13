package io.github.eslamasabry.oc.protocol.oc2.adapter

/**
 * The note the person writes with Deny, as OpenCode 2 hands it to the model.
 *
 * OpenCode 2 (2.2.6) takes a refusal as `{reply: "reject", message}` and has no other field for the person's words:
 * the server turns `message` into `Permission.CorrectedError`, whose text is the message alone, and the model reads it
 * as the failed tool call's error. A bare "use rc2.txt" there read to the model as text from the tool, "not the user"
 * (RC1 device pass, 2026-11-13). So the message says who wrote the note and that it is how to go on; the note itself
 * follows on its own line, and [note] reads it back for the declined step's "Your note".
 */
public object Oc2DenyNote {
    private const val LEAD: String =
        "The user declined this tool call, so it did not run. To tell you how to proceed, the user said:\n"

    /** What is sent as the reject reply's `message` for [note]. */
    public fun message(note: String): String = LEAD + note.trim()

    /** The person's own words from a refusal's error [text], when it is a [message]; null otherwise. */
    public fun note(text: String?): String? =
        text?.trim()?.takeIf { it.startsWith(LEAD.trim()) }?.substringAfter(LEAD.trim())?.trim()?.takeIf { it.isNotEmpty() }
}
