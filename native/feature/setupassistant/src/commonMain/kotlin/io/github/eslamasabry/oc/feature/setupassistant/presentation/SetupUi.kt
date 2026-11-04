package io.github.eslamasabry.oc.feature.setupassistant.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.setupassistant.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public class SetupWorkspace(
    public val inspection: SetupInspectionController?,
    public val registry: SetupRegistryStore,
    public val audit: SetupAudit,
    public val online: () -> Boolean,
    public val newId: () -> String,
)

public fun interface SetupHost {
    public suspend fun open(profile: ProfileId): Outcome<SetupWorkspace>
}

public data class SetupUi(
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val failed: Boolean = false,
    val inspection: SetupInspectionState? = null,
    val registry: SetupRegistryState = SetupRegistryState(),
    val audit: ImmutableList<SetupAuditRecord> = persistentListOf(),
    val auditUnavailable: Boolean = false,
    val savingReview: Boolean = false,
)

public interface SetupActions {
    public fun back()

    public fun refresh()

    public fun setRegistryConsent(enabled: Boolean)

    public fun refreshRegistry(query: String?)

    public fun clearRegistry()

    public fun showSaved()

    public fun recordReview()
}
