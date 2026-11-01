package io.github.eslamasabry.oc.engine.composer.phrases

/**
 * What is known about the conversation's folder (Flutter `ChatStartFacts`, `empty_chat.dart`). [entries] null: the
 * server could not list it; [settled] false: still being looked at.
 */
public data class ChatStartFacts(
    val directory: String?,
    val entries: Int? = null,
    val git: Boolean? = null,
    val hasHistory: Boolean = false,
    val changes: Int? = null,
    val settled: Boolean = true,
) {
    private val normalized: String get() = (directory?.trim() ?: "").replace('\\', '/')

    /** The folder's name, ignoring trailing slashes and Windows separators (B-CHAT-018). */
    val projectName: String? get() = normalized.split('/').lastOrNull { it.isNotEmpty() }

    val isProjectsRoot: Boolean get() = normalized.endsWith("/root/projects")

    val hasProject: Boolean get() = projectName != null && !isProjectsRoot

    public companion object {
        public fun pending(directory: String?): ChatStartFacts = ChatStartFacts(directory, settled = false)
    }
}

/** A starter's words live in `:ui:words`; the engine says which ones (B-CHAT-017). */
public enum class StarterKind {
    ListFolder, FindBug, BuildWebPage, PythonScript, NodeProject, Readme, ExplainProject, WhatChanged, AddTests,
}

/** A fact of the muted line under the project name ("24 items · Git · 3 changes"). */
public sealed interface StartFact {
    public data object Looking : StartFact

    public data object EmptyFolder : StartFact

    public data class Items(val count: Int) : StartFact

    public data object Git : StartFact

    public data class Changes(val count: Int) : StartFact
}

public object Starters {
    /** Port of `chatStartSuggestions`. */
    public fun suggestions(facts: ChatStartFacts): List<StarterKind> =
        when {
            !facts.settled -> emptyList()
            // No project (the server's own folder) or the folder of projects: "this project" would be nothing.
            !facts.hasProject -> listOf(StarterKind.ListFolder, StarterKind.FindBug)
            facts.entries == 0 ->
                listOf(StarterKind.BuildWebPage, StarterKind.PythonScript, StarterKind.NodeProject, StarterKind.Readme)
            // Files, or a listing the server could not give: work on what is there.
            else -> listOfNotNull(
                StarterKind.ExplainProject,
                StarterKind.WhatChanged.takeIf { facts.hasHistory },
                StarterKind.FindBug,
                StarterKind.AddTests,
            )
        }

    /** Port of `chatStartFactsLine`; empty when there is nothing to say. */
    public fun factsLine(facts: ChatStartFacts): List<StartFact> {
        if (!facts.settled) return listOf(StartFact.Looking)
        val out = mutableListOf<StartFact>()
        when (val entries = facts.entries) {
            0 -> out += StartFact.EmptyFolder
            null -> Unit
            else -> out += StartFact.Items(entries)
        }
        if (facts.git == true) out += StartFact.Git
        val changes = facts.changes
        if (facts.git == true && changes != null && changes > 0) out += StartFact.Changes(changes)
        return out
    }

    /** A label ending in an ellipsis starts a sentence: it fills the composer with a trailing space instead. */
    public fun insertText(label: String): String = if (label.endsWith('…')) label.dropLast(1).trimEnd() + " " else label
}
