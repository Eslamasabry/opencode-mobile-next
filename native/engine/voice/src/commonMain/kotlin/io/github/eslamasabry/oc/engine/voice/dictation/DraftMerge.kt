package io.github.eslamasabry.oc.engine.voice.dictation

/**
 * Port of Dart `mergeVoiceDraft` (`lib/voice/controller.dart`, B-MEDIA-024): the dictated words replace the selection
 * (or land at the end when the selection is invalid), with a space before only when the left neighbour is not
 * whitespace and after only when the right neighbour is not whitespace. Empty words leave [existing] unchanged.
 * Selection offsets are UTF-16 indices, as the text field reports them; a negative start means "no selection".
 */
public object DraftMerge {
    public fun merge(existing: String, selectionStart: Int, selectionEnd: Int, words: String): String {
        val trimmed = words.trim()
        if (trimmed.isEmpty()) return existing
        val valid = selectionStart >= 0 && selectionEnd >= 0
        val start = if (valid) selectionStart.coerceIn(0, existing.length) else existing.length
        val end = if (valid) selectionEnd.coerceIn(start, existing.length) else existing.length
        val before = existing.substring(0, start)
        val after = existing.substring(end)
        val leading = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val trailing = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
        return "$before$leading$trimmed$trailing$after"
    }
}
