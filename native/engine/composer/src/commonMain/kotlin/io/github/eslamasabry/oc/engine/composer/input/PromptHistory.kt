package io.github.eslamasabry.oc.engine.composer.input

/**
 * The field's value as the UI holds it: text, selection and the IME composing range (UTF-16 offsets; -1 = none).
 * A plain mirror of `TextFieldState` so the engine stays free of Compose.
 */
public data class ComposerValue(
    val text: String,
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
    val composingStart: Int = -1,
    val composingEnd: Int = -1,
) {
    val selectionValid: Boolean get() = selectionStart >= 0 && selectionEnd >= 0
    val collapsed: Boolean get() = selectionStart == selectionEnd
    val composing: Boolean get() = composingStart >= 0 && composingEnd >= 0 && composingStart != composingEnd

    override fun toString(): String = "ComposerValue(${text.length} chars, $selectionStart..$selectionEnd)"

    public companion object {
        public fun collapsed(text: String, offset: Int): ComposerValue = ComposerValue(text, offset, offset)
    }
}

public enum class HistoryKey { Up, Down }

/** Port of `lib/ui/widgets/prompt_history_navigation.dart` (B-CHAT-089). */
public object PromptHistoryKeys {
    /**
     * Arrow up at the very start or down at the very end of the text browses sent prompts; never with a modifier,
     * open suggestions, a selection or composing text (multi-line movement and IME input stay the field's).
     */
    public fun isHistoryKey(key: HistoryKey, value: ComposerValue, suggestionsOpen: Boolean, modifier: Boolean): Boolean {
        if (suggestionsOpen || modifier || !value.selectionValid || !value.collapsed || value.composing) return false
        return when (key) {
            HistoryKey.Up -> value.selectionEnd == 0
            HistoryKey.Down -> value.selectionEnd == value.text.length
        }
    }
}

/** Steps through sent prompts and restores the exact draft (with its selection) when it walks back past the newest. */
public class PromptHistoryNavigation {
    public var original: ComposerValue? = null
        private set
    private var prompts: List<String> = emptyList()
    private var index = -1

    /** [prompts] newest first; read only when browsing starts. Null: nothing to do. */
    public fun move(older: Boolean, current: ComposerValue, prompts: List<String>): ComposerValue? {
        if (original == null) {
            if (!older || prompts.isEmpty()) return null
            original = current
            this.prompts = prompts.toList()
            index = -1
        }
        if (older) {
            if (index + 1 >= this.prompts.size) return null
            index++
        } else {
            if (index <= 0) return restore()
            index--
        }
        val text = this.prompts[index]
        return ComposerValue.collapsed(text, if (older) 0 else text.length)
    }

    public fun restore(): ComposerValue? {
        val value = original
        original = null
        prompts = emptyList()
        index = -1
        return value
    }
}
