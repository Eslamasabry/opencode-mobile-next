package io.github.eslamasabry.oc.feature.chat.composer

/**
 * S30 story 2: one row of the composer's "/" menu (Flutter `_ChatCommand` in `composer.dart` `_suggestions`): the
 * app's own actions and the server's commands, in the person's words. [inserts] marks a server command, which puts
 * `/slash ` in the message box; an app action leaves the box and runs [onRun].
 */
public class ComposerSlashCommand(
    public val key: String,
    public val slash: String,
    public val title: String,
    public val description: String?,
    public val aliases: List<String> = emptyList(),
    public val inserts: Boolean = false,
    public val onRun: () -> Unit,
)

/** The "/" menu's rules, as Flutter's `CommandSheetEntry.matchesQuery` and `scoreFor` (command_sheet.dart). */
public object SlashMenu {
    /** Rows shown above the field before "Show all" (Flutter `KitComposerChips.visibleCount`). */
    public const val VISIBLE: Int = 5

    /** The commands [query] matches, best first; ties by slash name. */
    public fun rank(commands: List<ComposerSlashCommand>, query: String): List<ComposerSlashCommand> =
        commands
            .filter { matches(it, query) }
            .sortedWith(compareBy<ComposerSlashCommand> { score(it, query) }.thenBy { it.slash })

    public fun matches(command: ComposerSlashCommand, query: String): Boolean {
        val q = normalized(query)
        if (q.isEmpty()) return true
        return command.slash.lowercase().contains(q) ||
            command.aliases.any { it.lowercase().contains(q) } ||
            command.title.lowercase().contains(q) ||
            command.description?.lowercase()?.contains(q) == true
    }

    /** 0 is the best match: exact slash, slash prefix, exact alias, alias prefix, title prefix, contains, other. */
    public fun score(command: ComposerSlashCommand, query: String): Int {
        val q = normalized(query)
        if (q.isEmpty()) return 0
        val slash = command.slash.lowercase()
        val aliases = command.aliases.map { it.lowercase() }
        val title = command.title.lowercase()
        return when {
            slash == q -> 0
            slash.startsWith(q) -> 1
            aliases.any { it == q } -> 2
            aliases.any { it.startsWith(q) } -> 3
            title.startsWith(q) -> 4
            slash.contains(q) -> 5
            title.contains(q) -> 6
            else -> 7
        }
    }

    private fun normalized(query: String): String = query.trim().lowercase().removePrefix("/")
}
