package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

/** Retained Gemma files only; the deferred assistant runtime has no entry point. */
public data class LocalModelStorageFacts(
    val loading: Boolean = true,
    val bytes: Long = 0,
    val present: Boolean = false,
    val size: String = "",
    val deleting: Boolean = false,
    val failed: Boolean = false,
    val confirmDelete: Boolean = false,
)

public interface LocalModelStorage {
    public val facts: StateFlow<LocalModelStorageFacts>
    public suspend fun refresh(): Outcome<Unit>
    public suspend fun delete(): Outcome<Unit>
}
