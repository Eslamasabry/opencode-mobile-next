package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.setupassist.SetupController
import io.github.eslamasabry.oc.engine.setupassist.SetupIntent
import io.github.eslamasabry.oc.engine.setupassist.SetupPlanner
import io.github.eslamasabry.oc.engine.setupassist.SetupPlannerState
import io.github.eslamasabry.oc.engine.setupassist.SetupRegistryStatus
import io.github.eslamasabry.oc.engine.setupassist.SetupRegistryStore

/** Opened against one live graph and location. Never silently moves to a new connection. */
public interface ServersSetupSource {
    public suspend fun open(profile: ProfileId): Outcome<ServersSetupSession>
}

public interface ServersSetupSession {
    public val controller: SetupController
    public val planner: SetupPlanner
    public val registry: SetupRegistryStore

    public fun current(): Boolean

    public fun online(): Boolean

    public fun stop()

    public suspend fun close()
}

public data class ServerSetupUi(
    val busy: Boolean = false,
    val available: Boolean = false,
    val failed: Boolean = false,
    val config: String = "",
    val intent: SetupIntent = SetupIntent.ChooseModel,
    val guidance: SetupPlannerState? = null,
    val invalid: Boolean = false,
    val proposal: String = "",
    val optedIn: Boolean = false,
    val registryStatus: SetupRegistryStatus = SetupRegistryStatus.Disabled,
    val listings: List<SetupListingUi> = emptyList(),
)

/** The listing's safe metadata only, never commands, header values or raw response fields. */
public data class SetupListingUi(
    val name: String,
    val description: String,
    val detail: SetupListingDetail,
)

public enum class SetupListingDetail {
    Remote,
    NeedsDetails,
    ReviewedCommand,
    NoEndpoint,
}

public interface ServerSetupActions {
    public fun back()

    public fun refresh()

    public fun choose(intent: SetupIntent)

    public fun propose(values: Map<String, String>)

    public fun optIn(enabled: Boolean)

    public fun refreshRegistry(query: String)
}
