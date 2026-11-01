package io.github.eslamasabry.opencode_mobile

/** Pure layout and presentation rules shared by the widget renderers. */
object WidgetLayoutPolicy {
    /** Which header phrase to show: see [WidgetStatusRules.summary]. */
    enum class Summary { IDLE, RUNNING, NEEDS, BOTH }

    private const val BOLD_GLYPH_EM = 0.54f
    private const val REGULAR_GLYPH_EM = 0.48f

    /** Whole lines only. Estimates use the average glyph width of the text size, so a
     * line count errs high (a line too many costs one preview line, never a cut word). */
    fun estimateLines(text: String, widthDp: Float, textSp: Float, bold: Boolean, maxLines: Int): Int {
        if (text.isBlank()) return 0
        val perLine = (widthDp / (textSp * if (bold) BOLD_GLYPH_EM else REGULAR_GLYPH_EM)).toInt().coerceAtLeast(1)
        var lines = 1
        var used = 0
        for (word in text.trim().split(Regex("\\s+"))) {
            val len = word.length
            if (used == 0) used = len
            else if (used + 1 + len <= perLine) used += 1 + len
            else { lines++; used = len }
            while (used > perLine) { lines++; used -= perLine }
        }
        return lines.coerceAtMost(maxLines)
    }

    /** The adaptive card's vertical budget, in dp at font scale 1 (see widget_card.xml): the
     * paddings, the header, the least gap under it and the bottom action. */
    private const val CARD_FIXED_DP = 16 + 24 + 10 + 12 + 48 + 12
    /** Whole lines: a 22sp title (18sp on a narrow card), the mono command, the live line, the meta line. */
    private const val TITLE_LINE_DP = 27
    private const val NARROW_TITLE_LINE_DP = 23
    private const val COMMAND_LINE_DP = 18
    private const val COMMAND_GAP_DP = 4
    private const val LIVE_LINE_DP = 24
    private const val META_LINE_DP = 24
    private const val ROWS_GAP_DP = 20
    const val ROW_DP = 50
    private const val MAX_ROWS = 5

    /** What one card shows. [command] 0 means the exact command is not shown, and so a request
     * offers Open alone: nobody allows what they cannot read. */
    data class CardPlan(val title: Int, val command: Int, val live: Boolean, val meta: Boolean, val rows: Int)

    /** What the card would like to show: whole title and command lines, the live and meta lines,
     * and how many other conversations there are. */
    data class CardNeeds(val title: Int, val command: Int, val live: Boolean, val meta: Boolean, val otherRows: Int)

    /** Spend the height in this order: one title line, the whole exact command (a request), the
     * live line (a working turn), a second title line (the question is the point), the meta line,
     * then whole rows of the other conversations. A command that cannot be shown whole is not
     * shown at all. */
    fun cardPlan(effectiveHeightDp: Int, narrow: Boolean, needs: CardNeeds): CardPlan {
        val titleLine = if (narrow) NARROW_TITLE_LINE_DP else TITLE_LINE_DP
        var left = effectiveHeightDp - CARD_FIXED_DP - titleLine
        var command = 0
        val commandCost = COMMAND_GAP_DP + needs.command * COMMAND_LINE_DP
        if (needs.command > 0 && left >= commandCost) { command = needs.command; left -= commandCost }
        val showLive = needs.live && left >= LIVE_LINE_DP
        if (showLive) left -= LIVE_LINE_DP
        var title = 1
        if (needs.title > 1 && left >= titleLine) { title = 2; left -= titleLine }
        val showMeta = needs.meta && left >= META_LINE_DP
        if (showMeta) left -= META_LINE_DP
        val rows = if (needs.otherRows > 0 && left >= ROWS_GAP_DP + ROW_DP) {
            minOf(needs.otherRows, MAX_ROWS, (left - ROWS_GAP_DP) / ROW_DP)
        } else 0
        return CardPlan(title, command, showLive, showMeta, rows)
    }

    /** The question a request leads with, and the command line under it. A short command reads in
     * the question itself ("Run npm install?") and needs no second line; a long one keeps the app's
     * plain question with the command in mono under it. */
    data class Ask(val question: String, val command: String)

    const val INLINE_COMMAND_MAX = 28

    fun ask(file: String, template: String?, question: String): Ask = when {
        file.isBlank() -> Ask(question, "")
        template != null && file.length <= INLINE_COMMAND_MAX -> Ask(template.format(file), "")
        else -> Ask(question, file)
    }

    enum class Ago { NOW, MINUTES, HOURS, YESTERDAY, WEEKDAY, DATE }
    data class Elapsed(val kind: Ago, val count: Int = 0, val label: String = "")

    /** Non-blank parts joined with the separator; no dangling dots. */
    fun join(vararg parts: String, separator: String = " · "): String =
        parts.filter { it.isNotBlank() }.joinToString(separator)

    /** Which words lead a card or row. Offline wins over everything but a request. */
    enum class Lead { NEEDS_YOU, OFFLINE, WORKING, READY }
}
