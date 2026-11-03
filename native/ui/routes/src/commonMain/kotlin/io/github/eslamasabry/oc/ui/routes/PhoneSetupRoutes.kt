package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * Set up OpenCode on this phone (Flutter phone_setup_start_screen + phone_setup_progress_screen). Owned by
 * :feature:phonesetup (unit P12-BUILD-RUNTIME-C). One page: the start (what is installed, the preflight) until a job
 * exists, then that job's progress. Shown only with builtinLinux.
 */
@Serializable
public sealed interface PhoneSetupRoute : AppRoute {
    @Serializable public data object Setup : PhoneSetupRoute
}
