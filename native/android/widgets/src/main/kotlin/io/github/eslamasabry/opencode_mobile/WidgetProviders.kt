package io.github.eslamasabry.opencode_mobile

/** Every widget receiver. The picker offers two entries (owner, 2026-10-29): Conversations
 * (4x2, [SessionsWideWidgetProvider]) and Status (2x2, [StatusWidgetProvider]). The older
 * entries are hidden from the picker but stay registered, so widgets already on a home screen
 * keep drawing. Each entry is its own receiver (a launcher reads one size per provider).
 * Anything that redraws or checks widgets walks these lists, never a single class. */
object WidgetProviders {
    private const val WIDE_CELLS = 4

    val sessions: List<Class<out SessionsWidgetProvider>> = listOf(
        SessionsWidgetProvider::class.java,
        SessionsWideWidgetProvider::class.java,
        SessionsListWidgetProvider::class.java,
        SessionsStripWidgetProvider::class.java,
    )
    val usage: List<Class<out UsageWidgetProvider>> = listOf(
        UsageWidgetProvider::class.java,
        UsageWideWidgetProvider::class.java,
    )
    val status: List<Class<out StatusWidgetProvider>> = listOf(
        StatusWidgetProvider::class.java,
    )

    /** The only entries the picker shows. */
    val picker: List<String> get() = listOf(
        SessionsWideWidgetProvider::class.java.name,
        StatusWidgetProvider::class.java.name,
    )

    /** Class names, for the manifest and for ownership checks. */
    val sessionNames: List<String> get() = sessions.map { it.name }
    val usageNames: List<String> get() = usage.map { it.name }
    val statusNames: List<String> get() = status.map { it.name }
    val allNames: Set<String> get() = (sessionNames + usageNames + statusNames).toSet()

    /** The picker size each entry is declared at, in launcher cells (width, height). */
    val sessionCells: Map<String, Pair<Int, Int>> = mapOf(
        SessionsWidgetProvider::class.java.name to (2 to 2),
        SessionsWideWidgetProvider::class.java.name to (WIDE_CELLS to 2),
        SessionsListWidgetProvider::class.java.name to (WIDE_CELLS to WIDE_CELLS),
        SessionsStripWidgetProvider::class.java.name to (WIDE_CELLS to 1),
    )
    val usageCells: Map<String, Pair<Int, Int>> = mapOf(
        UsageWidgetProvider::class.java.name to (2 to 2),
        UsageWideWidgetProvider::class.java.name to (WIDE_CELLS to 2),
    )
    val statusCells: Map<String, Pair<Int, Int>> = mapOf(
        StatusWidgetProvider::class.java.name to (2 to 2),
    )
}

/** "Conversations": the 4x2 entry, drawn by [ConversationsWidgetBinder]. */
class SessionsWideWidgetProvider : SessionsWidgetProvider()

/** The retired 4x4 list: hidden from the picker; placed ones keep drawing the card. */
class SessionsListWidgetProvider : SessionsWidgetProvider()

/** The retired 4x1 strip: hidden from the picker; placed ones keep drawing the strip. */
class SessionsStripWidgetProvider : SessionsWidgetProvider()

/** The retired wide Usage entry: hidden from the picker; Usage now folds into Status. */
class UsageWideWidgetProvider : UsageWidgetProvider()
