package io.github.eslamasabry.oc.feature.chat.relations.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCommand
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public enum class RelationStatus { NeedsYou, Working, Idle, Done }

public data class RelationRow(
    val id: SessionId,
    val title: String?,
    val parent: Boolean,
    val current: Boolean,
    val status: RelationStatus,
    val createdMs: Long?,
    val pinned: Boolean,
    val ageMinutes: Long?,
)

public data class SessionRelationsUi(
    val loading: Boolean = true,
    val stale: Boolean = false,
    val parent: RelationRow? = null,
    val children: ImmutableList<RelationRow> = persistentListOf(),
    val failure: Failure? = null,
    val menu: RelationRow? = null,
    val stopping: RelationRow? = null,
    val working: Boolean = false,
    val canPin: Boolean = false,
    val canResume: Boolean = false,
    val resume: SessionResumeCommand? = null,
    val clip: RelationClip? = null,
)

public data class RelationClip(val id: Long, val text: String)

public interface SessionRelationsActions {
    public fun back()
    public fun refresh()
    public fun open(id: SessionId)
    public fun showMenu(id: SessionId)
    public fun dismissMenu()
    public fun togglePin()
    public fun showResume()
    public fun dismissResume()
    public fun copyResume()
    public fun clipDone(id: Long)
    public fun requestStop()
    public fun cancelStop()
    public fun confirmStop()
}
