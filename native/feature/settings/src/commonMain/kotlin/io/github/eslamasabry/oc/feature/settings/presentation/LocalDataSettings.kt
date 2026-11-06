package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

public enum class LocalDataKind {
    Queue,
    Drafts,

    /** N-48: the queued prompts kept for review (they may already have been sent), after the person confirms. */
    QueueHeld,
}

public data class LocalDataFacts(
    val queued: Int = 0,
    val queueBytes: Long = 0,
    val queueReadable: Boolean = true,
    /**
     * N-48: of [queued], the ones kept for review (they may already have reached the server). With any, Clear queued
     * prompts asks once, names them, and deletes them all.
     */
    val held: Int = 0,
    val drafts: Int = 0,
    val draftBytes: Long = 0,
    val draftsReadable: Boolean = true,
    val servers: ImmutableList<ProfileId> = persistentListOf(),
    val sharing: Int = 0,
)

public data class LocalDeleteResult(
    val removed: Int,
    val kept: Int = 0,
    val payloadsCollected: Boolean = true,
)

public data class LocalDataUi(
    val facts: LocalDataFacts?,
    val busy: Boolean = false,
    val confirm: LocalDataKind? = null,
    val result: LocalDeleteResult? = null,
    val failed: Boolean = false,
)

public interface LocalDataSettings {
    public val facts: StateFlow<LocalDataFacts?>

    public fun refresh()

    public suspend fun discard(kind: LocalDataKind): Outcome<LocalDeleteResult>
}

/**
 * N-48: the question Clear queued prompts asks. With prompts kept for review it is one question that names them and
 * deletes them all ([LocalDataKind.QueueHeld]); never a second confirm for what the first already covered.
 */
public fun localDataQuestion(kind: LocalDataKind, facts: LocalDataFacts?): LocalDataKind =
    if (kind == LocalDataKind.Queue && (facts?.held ?: 0) > 0) LocalDataKind.QueueHeld else kind
