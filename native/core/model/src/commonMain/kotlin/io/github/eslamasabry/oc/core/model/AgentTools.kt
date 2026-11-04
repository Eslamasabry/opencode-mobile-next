package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.Redacted
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public enum class AgentFeatureKind {
    Toggle,
    Choice,
}

public data class AgentFeatureOption(val id: String, val label: String)

public data class AgentFeature(
    val id: String,
    val label: String,
    val description: String?,
    val kind: AgentFeatureKind,
    val on: Boolean = false,
    val selected: String? = null,
    val options: ImmutableList<AgentFeatureOption> = persistentListOf(),
)

public enum class HostUpdatePhase {
    Starting,
    Downloading,
    Installing,
    Complete,
}

public data class HostUpdateResult(
    val success: Boolean,
    val previousVersion: String?,
    val newVersion: String?,
    val reason: Redacted? = null,
    val alreadyUpToDate: Boolean =
        success && previousVersion != null && previousVersion == newVersion,
)

public data class ImportableConversation(
    val agentId: String,
    val handle: String,
    val directory: String,
    val lastUsedMs: Long,
    val title: String?,
    val firstPrompt: String?,
)

public data class ImportableConversations(
    val items: ImmutableList<ImportableConversation>,
    val alreadyImported: Int = 0,
)
