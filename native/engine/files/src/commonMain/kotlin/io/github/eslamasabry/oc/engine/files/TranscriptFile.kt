package io.github.eslamasabry.oc.engine.files

/**
 * A conversation saved as a readable Markdown file (Flutter chat_session_menu.dart `_exportTranscript`): the file a
 * [FileSaver] writes where the person picks. Named `opencode-<first 8 of the session id>.md`, UTF-8, `text/markdown`.
 * [markdown] is the transcript the chat already builds for Copy transcript, masked the same way.
 */
public object TranscriptFile {
    public const val MIME: String = "text/markdown"

    public fun name(sessionId: String): String {
        val safe = sessionId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(8)
        return if (safe.isEmpty()) "opencode.md" else "opencode-$safe.md"
    }

    public fun markdown(sessionId: String, markdown: String): FileData =
        FileData(name(sessionId), MIME, markdown.encodeToByteArray())
}
