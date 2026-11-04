package io.github.eslamasabry.oc.android.notifications

/**
 * The finished alert's line from a transcript page (B-ANDR-048): port of the Flutter `firstReplyLine(messages)`
 * filtering. Only the person-visible text of assistant messages counts; the person's own words and synthetic parts
 * never reach the shade. Protocol-neutral so the app graph maps its message parts into it.
 */
public object ReplyExcerpt {
    /** One message's text parts, oldest message first. */
    public data class Message(val fromAssistant: Boolean, val parts: List<Part>) {
        override fun toString(): String = "Message(assistant=$fromAssistant, parts=${parts.size})"
    }

    public data class Part(val text: String, val synthetic: Boolean = false) {
        override fun toString(): String = "Part(<${text.length}>, synthetic=$synthetic)"
    }

    public fun firstLine(messages: List<Message>, maxLength: Int = NotificationText.MAX_LINE): String =
        NotificationText.firstReplyLine(
            messages
                .filter { it.fromAssistant }
                .map { m -> m.parts.filter { !it.synthetic }.joinToString("\n") { it.text } }
                .filter { it.isNotBlank() },
            maxLength,
        )
}
