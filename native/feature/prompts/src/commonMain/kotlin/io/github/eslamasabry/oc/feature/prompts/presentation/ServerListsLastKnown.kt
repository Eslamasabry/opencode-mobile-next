package io.github.eslamasabry.oc.feature.prompts.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.SkillInfo
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the server's slash commands, skills and the agent's task list open on the rows
 * they last showed, then the live read replaces them in place. The app binds this to its last-known store.
 */
public interface ServerListsLastKnown {
    public fun peek(ref: ConversationRef, item: String): KeptServerList?

    public suspend fun read(ref: ConversationRef, item: String): KeptServerList?

    public suspend fun keep(ref: ConversationRef, item: String, kept: KeptServerList)
}

/** The shelf items of the two pages. */
internal object ServerListItems {
    const val COMMANDS = "commands"
    const val TASKS = "tasks"
}

/** What a list page last showed: its rows (commands with their skills, or tasks) and the server's name. */
@Serializable
public data class KeptServerList(
    val server: String,
    val commands: List<Command> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val tasks: List<Task> = emptyList(),
) {
    @Serializable public data class Command(val name: String, val description: String? = null)

    /** Skills keep their id and name only; a skill's body is never kept. */
    @Serializable public data class Skill(val id: String, val name: String)

    @Serializable public data class Task(val content: String, val state: String, val priority: String? = null)

    internal fun commandItems(): List<CommandItem> = commands.map { CommandItem(it.name, it.description) }

    internal fun skillInfos(): List<SkillInfo> = skills.map { SkillInfo(it.id, it.name, null, "", "", false) }

    internal fun taskItems(): List<TaskItem> =
        tasks.map {
            TaskItem(
                it.content,
                TaskState.entries.firstOrNull { s -> s.name == it.state } ?: TaskState.Pending,
                TaskPriority.entries.firstOrNull { p -> p.name == it.priority },
            )
        }

    internal companion object {
        const val MAX_ROWS = 200
        const val MAX_TEXT = 300

        fun ofCommands(server: String, items: List<CommandItem>, skills: List<SkillInfo>): KeptServerList =
            KeptServerList(
                server,
                commands = items.take(MAX_ROWS).map { Command(it.name.take(MAX_TEXT), it.description?.take(MAX_TEXT)) },
                skills = skills.take(MAX_ROWS).mapNotNull { s -> s.id?.let { Skill(it, s.name.take(MAX_TEXT)) } },
            )

        fun ofTasks(server: String, items: List<TaskItem>): KeptServerList =
            KeptServerList(
                server,
                tasks = items.take(MAX_ROWS).map { Task(it.content.take(MAX_TEXT), it.state.name, it.priority?.name) },
            )
    }
}
