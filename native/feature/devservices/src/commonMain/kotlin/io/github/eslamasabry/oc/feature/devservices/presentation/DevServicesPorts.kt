package io.github.eslamasabry.oc.feature.devservices.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.devservers.PhoneScriptRunner
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceSettings
import io.github.eslamasabry.oc.engine.runtime.services.commands.ManagedShellPort
import kotlin.time.Clock

/**
 * Everything one project's Development services page needs from the app, for one server and folder. The Android
 * binding (graph/DevServicesWiring.kt) builds it from the live connection; null from [DevServicesHost.open] means the
 * server is gone or not loaded, and the page says so.
 */
public class DevServicesContext(
    /** Where `oc.developmentServices.<profileId>` lives (the Flutter key, so migrated services read as-is). */
    public val settings: DevelopmentServiceSettings,
    /** False once the profile is being deleted: nothing more is written for it. */
    public val canWrite: () -> Boolean,
    public val workspace: String?,
    /** The server can start and track commands (ServerCapabilities.developmentServices). */
    public val supported: () -> Boolean,
    /** The live server's managed shells, or null when it cannot be reached now. */
    public val shells: suspend () -> ManagedShellPort?,
    /** Set only when the server is this phone's own and the in-app Linux exists (B-LINUX-059). */
    public val phone: PhoneScriptRunner?,
    /** The page's server and folder are still the ones it opened for. */
    public val isCurrent: () -> Boolean,
    /** A fresh unguessable id (24 secure random bytes as hex, as Flutter). */
    public val newId: () -> String,
    /** Opens a saved preview address through the external link policy; false when it was not opened. */
    public val openLink: (String) -> Boolean,
    public val clock: Clock,
)

public fun interface DevServicesHost {
    public fun open(profile: ProfileId, directory: String): DevServicesContext?
}

/** Desktop and tests: no live server, so the page reads "not available". Android replaces it. */
@ContributesBinding(AppScope::class)
@Inject
public class NoDevServicesHost : DevServicesHost {
    override fun open(profile: ProfileId, directory: String): DevServicesContext? = null
}
