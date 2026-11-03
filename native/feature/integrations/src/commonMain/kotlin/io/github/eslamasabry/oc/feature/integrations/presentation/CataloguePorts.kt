package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * What a server lets the catalogue do from the app. OC2 lists, adds for the current location until OpenCode restarts,
 * and removes; a server that cannot list connectors can do none of it.
 */
public data class CatalogueAbilities(val canList: Boolean, val canAdd: Boolean, val canRemove: Boolean) {
    public companion object {
        public val None: CatalogueAbilities = CatalogueAbilities(canList = false, canAdd = false, canRemove = false)
    }
}

/**
 * The MCP catalogue's host side: the live servers, their MCP list, add and remove at the current location, and a
 * per-profile [CatalogueStore] (consent and saved registry list, `oc.setupRegistry.<profileId>`). Every call answers
 * with a Failure value; none carries the server's own words.
 */
public interface CatalogueSource {
    public val servers: StateFlow<ImmutableList<ConnectorServer>>

    public val active: StateFlow<ProfileId?>

    public fun abilities(profile: ProfileId): CatalogueAbilities

    /** The MCP servers this server has now, for the switches. */
    public suspend fun installed(profile: ProfileId): Outcome<ImmutableList<McpServerInfo>>

    /** Adds [draft] at the current location until OpenCode restarts; a name already there is refused. */
    public suspend fun add(profile: ProfileId, draft: McpServerDraft): Outcome<Unit>

    public suspend fun remove(profile: ProfileId, name: String): Outcome<Unit>

    /** A fresh store for [profile]; the page disposes it when it leaves the profile. */
    public fun catalogue(profile: ProfileId): CatalogueStore
}
