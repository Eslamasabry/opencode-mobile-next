package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One conversation folder on one server: where a branch is read (B-161). */
public data class BranchPlace(val profile: ProfileId, val directory: String)

/** One usage limit as the home's Usage row names it: whose limit ([name]) and how much of it is left today. */
public data class HomeUsageLimit(val name: String, val percentLeft: Int)

/** A project's kind as the folder scan names it (Dart `PhoneProjectKind`); it picks the project's glyph. */
public enum class HomeProjectKind { Dart, Node, Python, Rust, Go, Java, Ruby, Php, Dotnet, Cpp }

/**
 * What the project sheet says about one project folder beyond its counts (Dart `ProjectSummary.kind` / `isGit`,
 * chats_project_sheet.dart:85-107): its kind glyph and the "Git" pill.
 */
public data class HomeProjectFacts(val kind: HomeProjectKind? = null, val git: Boolean = false)

/** The defaults [ChatsHomeExtras] falls back on: no branch is known and no usage limit. */
internal object NoHomeRowFacts {
    val branches: StateFlow<Map<BranchPlace, String>> = MutableStateFlow(emptyMap<BranchPlace, String>()).asStateFlow()
    val projectFacts: StateFlow<Map<String, HomeProjectFacts>> =
        MutableStateFlow(emptyMap<String, HomeProjectFacts>()).asStateFlow()
    val usageLimits: StateFlow<ImmutableList<HomeUsageLimit>> =
        MutableStateFlow<ImmutableList<HomeUsageLimit>>(persistentListOf()).asStateFlow()
}

/** How long a live run has been going, for "Working 34m"; null below a minute or when the start is unknown. */
public sealed interface RunLength {
    public data class Minutes(val minutes: Int) : RunLength

    public data class HoursMinutes(val hours: Int, val minutes: Int) : RunLength

    public companion object {
        public fun of(elapsedMs: Long?): RunLength? {
            if (elapsedMs == null || elapsedMs < 60_000L) return null
            val minutes = (elapsedMs / 60_000L).toInt()
            return if (minutes < 60) Minutes(minutes) else HoursMinutes(minutes / 60, minutes % 60)
        }
    }
}

/** Pure rules for the B-161 rows and the B-162 Usage row. */
public object HomeRows {
    /** The tightest limit: the least left (ties keep the first); null when nothing is known. Clamped to 0..100. */
    public fun tightest(limits: List<HomeUsageLimit>): HomeUsageLimit? =
        limits.filter { it.name.isNotBlank() }.minByOrNull { it.percentLeft }?.let { it.copy(percentLeft = it.percentLeft.coerceIn(0, 100)) }

    /** The folders whose branch the rows want. */
    public fun places(ui: ChatsUi): Set<BranchPlace> =
        (ui.today + ui.earlier + ui.brief?.rows.orEmpty())
            .mapNotNullTo(linkedSetOf()) { row -> row.directory?.let { BranchPlace(row.ref.profileId, it) } }

    /** [ui] with each row's branch from [branches] (none for a folder without one). */
    public fun withBranches(ui: ChatsUi, branches: Map<BranchPlace, String>): ChatsUi {
        if (branches.isEmpty()) return ui
        fun ConversationRowUi.branched(): ConversationRowUi =
            copy(branch = directory?.let { branches[BranchPlace(ref.profileId, it)] }?.trim()?.takeIf { it.isNotEmpty() })
        return ui.copy(
            today = ui.today.map { it.branched() }.toImmutableList(),
            earlier = ui.earlier.map { it.branched() }.toImmutableList(),
            brief = ui.brief?.let { b -> b.copy(rows = b.rows.map { it.branched() }.toImmutableList()) },
        )
    }
}
