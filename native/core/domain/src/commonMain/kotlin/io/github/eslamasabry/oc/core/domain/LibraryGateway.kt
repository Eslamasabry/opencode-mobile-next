package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList

/**
 * Safe inventory fields only. Loader diagnostics, source paths and plugin options never cross this
 * facet.
 */
public data class LibraryPlugin(
    val id: String?,
    val status: LibraryPluginStatus,
    val source: LibraryPluginSource,
    val terminalUi: Boolean,
    val packageName: String?,
)

public enum class LibraryPluginStatus {
    Active,
    Failed,
    Unknown,
}

public enum class LibraryPluginSource {
    Builtin,
    Package,
    Local,
    Sdk,
    Unknown,
}

/**
 * Optional server library actions. A session skill uses its catalog id and an explicit run choice.
 */
public interface LibraryGateway {
    public suspend fun plugins(): Outcome<ImmutableList<LibraryPlugin>>

    public suspend fun activateSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
    ): Outcome<Unit>
}
