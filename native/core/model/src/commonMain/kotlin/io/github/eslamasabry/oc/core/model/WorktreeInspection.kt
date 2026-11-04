package io.github.eslamasabry.oc.core.model

/**
 * Server-owned membership: directories are compared exactly, never normalized or prefix matched.
 */
public data class WorktreeScope(
    val projectId: String,
    val directory: String,
    val workspaceId: String? = null,
)
