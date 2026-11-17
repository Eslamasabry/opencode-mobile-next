package io.github.eslamasabry.oc.core.model

/**
 * The model and its effort said once, the one home of the rule (owner, build 10073: the chip read
 * "Gemini 3.8 Flash (High) · high"). Every place that shows the pair (the message box chip, Settings rows, Usage rows)
 * joins them here, so there is no second formatter to disagree.
 *
 * An agent such as Antigravity names the effort inside the model's own name ("Gemini 3.8 Flash (High)", id
 * `gemini-3.8-flash-high`): the effort is one of the models it lists. When the name already ends with an effort word
 * (compared without case, spaces or hyphens), the effort is not said again, and the effort control has nothing more
 * to offer ([offersChoice]). A name that ends with a different effort word than the selected one says both, because
 * they are two settings that disagree.
 */
public object ModelEffort {
    private val WORDS =
        setOf("none", "nothinking", "minimal", "low", "medium", "high", "xhigh", "extrahigh", "max", "thinking")
    private val PARENTHESIS = Regex("""\(([^()]+)\)\s*$""")
    private val BARE = Regex("""[-_\s]([A-Za-z]+(?:[-\s][A-Za-z]+)?)\s*$""")

    /** The effort word a name ends with, as written ("High" for "Gemini 3.8 Flash (High)"), or null. */
    public fun carried(name: String): String? {
        val trimmed = name.trim()
        PARENTHESIS.find(trimmed)?.groupValues?.get(1)?.trim()?.takeIf { normal(it) in WORDS }?.let { return it }
        val tail = BARE.find(trimmed)?.groupValues?.get(1) ?: return null
        tail.takeIf { normal(it) in WORDS }?.let { return it }
        // "… Extra high" and "…-extra-high": the last word alone is not one, the last two are.
        val last = tail.split('-', ' ').last()
        return last.takeIf { normal(it) in WORDS }
    }

    /** False when the name already carries an effort word: the choice is made by choosing the model. */
    public fun offersChoice(name: String): Boolean = carried(name) == null

    /** The name followed by the effort ([shown], its words; [id] its id), the effort left out when the name says it. */
    public fun label(name: String, id: String?, shown: String? = id): String {
        val effort = shown?.trim()?.takeIf { it.isNotEmpty() } ?: id?.trim()?.takeIf { it.isNotEmpty() } ?: return name
        val carried = carried(name)
        if (carried != null && (normal(carried) == normal(effort) || normal(carried) == normal(id.orEmpty()))) return name
        return "$name · $effort"
    }

    private fun normal(word: String): String = word.lowercase().filter { it.isLetterOrDigit() }
}
